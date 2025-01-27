package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.azure

import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import com.azure.core.exception.ResourceNotFoundException
import com.azure.core.util.{Context, FluxUtil}
import com.azure.data.schemaregistry.implementation.models.SchemasGetByIdResponse
import com.azure.data.schemaregistry.models.{SchemaFormat, SchemaProperties, SchemaRegistrySchema}
import com.azure.data.schemaregistry.{SchemaRegistryClientBuilder, SchemaRegistryVersion}
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import org.apache.avro.Schema
import org.apache.commons.io.IOUtils
import pl.touk.nussknacker.engine.kafka._
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.azure.internal._
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry._
import reactor.core.publisher.Mono

import java.nio.charset.StandardCharsets
import scala.compat.java8.FunctionConverters._
import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal

object AzureSchemaRegistryClientFactory extends SchemaRegistryClientFactoryWithRegistration {

  override type SchemaRegistryClientT = AzureSchemaRegistryClient

  override def create(config: SchemaRegistryClientKafkaConfig): SchemaRegistryClientT = {
    new AzureSchemaRegistryClient(config)
  }

}

class AzureSchemaRegistryClient(config: SchemaRegistryClientKafkaConfig) extends SchemaRegistryClientWithRegistration {

  private val azureConfiguration      = AzureConfigurationFactory.createFromKafkaProperties(config.kafkaProperties)
  private val credential              = AzureTokenCredentialFactory.createCredential(azureConfiguration)
  private val httpPipeline            = AzureHttpPipelineFactory.createPipeline(azureConfiguration, credential)
  private val fullyQualifiedNamespace = config.kafkaProperties("schema.registry.url")
  private val schemaGroup             = config.kafkaProperties("schema.group")

  private val schemaRegistryClient = new SchemaRegistryClientBuilder()
    .fullyQualifiedNamespace(fullyQualifiedNamespace)
    .credential(credential)
    .buildClient()

  // TODO: close it
  private val kafkaAdminClient = KafkaUtils.createKafkaAdminClient(KafkaConfig(Some(config.kafkaProperties), None))

  // We need to create our own schemas service because some operations like schema listing are not exposed by default client
  // or even its Schemas inner class. Others like listing of versions are implemented incorrectly (it has wrong json field name in model)
  private val enhancedSchemasService = new EnhancedSchemasImpl(
    fullyQualifiedNamespace,
    SchemaRegistryVersion.getLatest.getVersion,
    httpPipeline,
    SchemaRegistryJsonSerializer
  )

  override def getSchemaById(id: SchemaId): SchemaWithMetadata = {
    toSchemaWithMetada(schemaRegistryClient.getSchema(id.asString))
  }

  override protected def getByTopicAndVersion(
      topicName: UnspecializedTopicName,
      version: Int,
      isKey: Boolean
  ): Validated[SchemaRegistryError, SchemaWithMetadata] = {
    getOneMatchingSchemaName(topicName, isKey)
      .andThen { fullSchemaName =>
        try {
          Valid(schemaRegistryClient.getSchema(schemaGroup, fullSchemaName, version))
        } catch {
          case NonFatal(ex) => Invalid(SchemaVersionError(ex.getMessage))
        }
      }
      .map(toSchemaWithMetada)

  }

  private def toSchemaWithMetada(result: SchemaRegistrySchema) = {
    SchemaWithMetadata(new AvroSchema(result.getDefinition), SchemaId.fromString(result.getProperties.getId))
  }

  override protected def getLatestFreshSchema(
      topicName: UnspecializedTopicName,
      isKey: Boolean
  ): Validated[SchemaRegistryError, SchemaWithMetadata] = {
    getOneMatchingSchemaName(topicName, isKey).andThen { fullSchemaName =>
      try {
        FluxUtil
          .withContext(enhancedSchemasService.getSchemaByNameWithResponseAsync(schemaGroup, fullSchemaName, _))
          .map[Validated[SchemaRegistryError, SchemaWithMetadata]](
            asJavaFunction((response: SchemasGetByIdResponse) =>
              // must to be done in async context - otherwise schema string is closed
              Valid(toSchemaWithMetadata(response))
            )
          )
          .block()
      } catch {
        case NonFatal(ex) => Invalid(SchemaRegistryUnknownError(ex.getMessage, ex))
      }
    }
  }

  private def toSchemaWithMetadata(response: SchemasGetByIdResponse) = {
    val schemaId = SchemaId.fromString(response.getDeserializedHeaders.getSchemaId)
    SchemaWithMetadata(new AvroSchema(IOUtils.toString(response.getValue, StandardCharsets.UTF_8)), schemaId)
  }

  override def getAllTopics: Validated[SchemaRegistryError, List[UnspecializedTopicName]] = {
    val topics        = fetchTopics
    val matchStrategy = SchemaNameTopicMatchStrategy(topics)
    getAllFullSchemaNames.map(matchStrategy.getAllMatchingTopics(_, isKey = false))
  }

  override def getAllVersions(
      topicName: UnspecializedTopicName,
      isKey: Boolean
  ): Validated[SchemaRegistryError, List[Integer]] = {
    getOneMatchingSchemaName(topicName, isKey).andThen(getVersions)
  }

  private def fetchTopics = kafkaAdminClient.listTopics().names().get().asScala.toList.map(UnspecializedTopicName.apply)

  private def getOneMatchingSchemaName(
      topicName: UnspecializedTopicName,
      isKey: Boolean
  ): Validated[SchemaRegistryError, String] = {
    getAllFullSchemaNames.andThen { fullSchemaNames =>
      val matchingFullSchemaNames = SchemaNameTopicMatchStrategy.getMatchingSchemas(topicName, fullSchemaNames, isKey)
      matchingFullSchemaNames match {
        case one :: Nil =>
          Valid(one)
        case Nil =>
          Invalid(SchemaTopicError(s"Schema for topic: $topicName not found"))
        case moreThenOnce =>
          // We can't pick one in this case because there is no option to recognize which one is newer.
          // I've tried to parse schemaId to UUID but it is saved in Version 4 UUID format (random) and has no timestamp
          Invalid(SchemaTopicError(s"Ambiguous schemas: ${moreThenOnce.mkString(", ")} for topic: $topicName"))
      }
    }
  }

  private def getVersions(fullSchemaName: String): Validated[SchemaRegistryError, List[Integer]] = {
    invokeBlocking(enhancedSchemasService.getVersionsWithResponseAsync(schemaGroup, fullSchemaName, _))
      .map(_.getValue.getSchemaVersions().asScala.toList)
  }

  private def getAllFullSchemaNames: Validated[SchemaRegistryError, List[String]] = {
    invokeBlocking(enhancedSchemasService.getSchemasWithResponseAsync(schemaGroup, _))
      .map(_.getValue.getSchemas().asScala.toList)
  }

  override def registerSchema(topicName: UnspecializedTopicName, isKey: Boolean, schema: ParsedSchema): SchemaId = {
    val schemaNameBasedOnTopic = SchemaNameTopicMatchStrategy.schemaNameFromTopicName(topicName, isKey)
    val avroSchema             = checkAvroSchema(schema).rawSchema()
    if (avroSchema.getType == Schema.Type.RECORD) {
      require(
        avroSchema.getName == schemaNameBasedOnTopic,
        s"Invalid record schema name ${avroSchema.getName} Should be: $schemaNameBasedOnTopic."
      )
      SchemaId.fromString(registerSchemaVersionIfNotExists(schema).getId)
    } else {
      // for primitive types we have to register schema on two names - one for listing of topics purpose and second one based on name, to be possible to serialize such object
      // by KafkaAvroSerializer - it just lookup into schema registry based on schema's full name. Can be tricky which one id is returned - in most cases it easy better
      // to return this based on name
      registerSchemaVersionIfNotExists(schema, Some(schemaNameBasedOnTopic))
      SchemaId.fromString(registerSchemaVersionIfNotExists(schema).getId)
    }

  }

  // forceSchemaNameOpt is for special purposes like primitive schemas when there is no name
  def registerSchemaVersionIfNotExists(
      schema: ParsedSchema,
      forceSchemaNameOpt: Option[String] = None
  ): SchemaProperties = {
    val avroSchema   = checkAvroSchema(schema).rawSchema()
    val schemaString = schema.canonicalString()
    val name         = forceSchemaNameOpt.getOrElse(avroSchema.getFullName)
    try {
      schemaRegistryClient.getSchemaProperties(schemaGroup, name, schemaString, SchemaFormat.AVRO)
    } catch {
      case _: ResourceNotFoundException =>
        schemaRegistryClient.registerSchema(schemaGroup, name, schemaString, SchemaFormat.AVRO)
    }
  }

  def getSchemaIdByContent(schema: AvroSchema): SchemaId = {
    SchemaId.fromString(
      schemaRegistryClient
        .getSchemaProperties(schemaGroup, schema.rawSchema().getFullName, schema.canonicalString(), SchemaFormat.AVRO)
        .getId
    )
  }

  private def checkAvroSchema(schema: ParsedSchema): AvroSchema = {
    schema match {
      case avroSchema: AvroSchema => avroSchema
      case _ => throw new IllegalArgumentException("Currently only avro schema is supported for Azure implementation")
    }
  }

  private def invokeBlocking[T](f: Context => Mono[T]): Validated[SchemaRegistryError, T] = {
    try {
      Valid(FluxUtil.withContext(context => f(context)).block())
    } catch {
      case NonFatal(ex) => Invalid(SchemaRegistryUnknownError(ex.getMessage, ex))
    }
  }

}
