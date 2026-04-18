package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.azure.client

import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import com.azure.core.exception.ResourceNotFoundException
import com.azure.core.util.{Context, FluxUtil}
import com.azure.data.schemaregistry.{SchemaRegistryClientBuilder, SchemaRegistryVersion}
import com.azure.data.schemaregistry.implementation.models.SchemasGetByIdResponse
import com.azure.data.schemaregistry.models.{SchemaFormat, SchemaProperties, SchemaRegistrySchema}
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import org.apache.commons.io.IOUtils
import pl.touk.nussknacker.engine.kafka.{KafkaComponentsConfig, KafkaUtils, SchemaRegistryClientKafkaConfig, UnspecializedTopicName}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{SchemaId, SchemaRegistryError, SchemaRegistryUnknownError, SchemaVersionError, SchemaWithMetadata}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.azure.SchemaNameTopicMatchStrategy
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.azure.internal.{AzureConfigurationFactory, AzureHttpPipelineFactory, AzureTokenCredentialFactory, EnhancedSchemasImpl, SchemaRegistryJsonSerializer}
import reactor.core.publisher.Mono

import java.nio.charset.StandardCharsets
import scala.compat.java8.FunctionConverters.asJavaFunction
import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal

class DefaultAzureSchemaRegistryClient(config: SchemaRegistryClientKafkaConfig) extends AzureSchemaRegistryClient {

  private val azureConfiguration      = AzureConfigurationFactory.createFromKafkaProperties(config.kafkaProperties)
  private val credential              = AzureTokenCredentialFactory.createCredential(azureConfiguration)
  private val httpPipeline            = AzureHttpPipelineFactory.createPipeline(azureConfiguration, credential)
  private val fullyQualifiedNamespace = config.kafkaProperties("schema.registry.url")
  private val schemaGroup             = config.kafkaProperties("schema.group")

  private val schemaRegistryClient = new SchemaRegistryClientBuilder()
    .fullyQualifiedNamespace(fullyQualifiedNamespace)
    .credential(credential)
    .buildClient()

  // We need to create our own schemas service because some operations like schema listing are not exposed by default client
  // or even its Schemas inner class. Others like listing of versions are implemented incorrectly (it has wrong json field name in model)
  private val enhancedSchemasService = new EnhancedSchemasImpl(
    fullyQualifiedNamespace,
    SchemaRegistryVersion.getLatest.getVersion,
    httpPipeline,
    SchemaRegistryJsonSerializer
  )

  override def getSchemaById(id: SchemaId): SchemaWithMetadata = {
    toSchemaWithMetadata(schemaRegistryClient.getSchema(id.asString))
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
      .map(toSchemaWithMetadata)

  }

  private def toSchemaWithMetadata(result: SchemaRegistrySchema) = {
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
    val topics        = fetchTopics(KafkaComponentsConfig(config.kafkaProperties, None))
    val matchStrategy = SchemaNameTopicMatchStrategy(topics)
    getAllFullSchemaNames.map(matchStrategy.getAllMatchingTopics(_, isKey = false))
  }

  override def getAllVersions(
                               topicName: UnspecializedTopicName,
                               isKey: Boolean
                             ): Validated[SchemaRegistryError, List[Integer]] = {
    getOneMatchingSchemaName(topicName, isKey).andThen(getVersions)
  }

  private def fetchTopics(kafkaComponentsConfig: KafkaComponentsConfig) = {
    KafkaUtils
      .usingAdminClient(kafkaComponentsConfig) { admin =>
        admin.listTopics().names().get().asScala.toList
      }
      .map(UnspecializedTopicName.apply)
  }

  private def getVersions(fullSchemaName: String): Validated[SchemaRegistryError, List[Integer]] = {
    invokeBlocking(enhancedSchemasService.getVersionsWithResponseAsync(schemaGroup, fullSchemaName, _))
      .map(_.getValue.getSchemaVersions().asScala.toList)
  }

  override protected def getAllFullSchemaNames: Validated[SchemaRegistryError, List[String]] = {
    invokeBlocking(enhancedSchemasService.getSchemasWithResponseAsync(schemaGroup, _))
      .map(_.getValue.getSchemas().asScala.toList)
  }

  override def registerSchemaVersionIfNotExists(
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

  override def getSchemaIdByContent(schema: AvroSchema): SchemaId = {
    SchemaId.fromString(
      schemaRegistryClient
        .getSchemaProperties(schemaGroup, schema.rawSchema().getFullName, schema.canonicalString(), SchemaFormat.AVRO)
        .getId
    )
  }

  private def invokeBlocking[T](f: Context => Mono[T]): Validated[SchemaRegistryError, T] = {
    try {
      Valid(FluxUtil.withContext(context => f(context)).block())
    } catch {
      case NonFatal(ex) => Invalid(SchemaRegistryUnknownError(ex.getMessage, ex))
    }
  }

}
