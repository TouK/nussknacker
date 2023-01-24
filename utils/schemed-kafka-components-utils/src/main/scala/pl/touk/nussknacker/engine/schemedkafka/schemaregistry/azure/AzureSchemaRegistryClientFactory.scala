package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.azure

import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import com.azure.core.exception.ResourceNotFoundException
import com.azure.core.util.{Context, FluxUtil}
import com.azure.data.schemaregistry.implementation.models.SchemasGetByIdResponse
import com.azure.data.schemaregistry.models.{SchemaFormat, SchemaProperties}
import com.azure.data.schemaregistry.{SchemaRegistryClientBuilder, SchemaRegistryVersion}
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import org.apache.commons.io.IOUtils
import pl.touk.nussknacker.engine.kafka.SchemaRegistryClientKafkaConfig
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.azure.SchemaNameTopicMatchingStrategy.FullSchemaNameDecomposed
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{SchemaId, SchemaNotFound, SchemaRegistryClient, SchemaRegistryClientFactory, SchemaRegistryError, SchemaRegistryUnknownError, SchemaWithMetadata}
import reactor.core.publisher.Mono

import scala.compat.java8.FunctionConverters._
import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal

class AzureSchemaRegistryClientFactory extends SchemaRegistryClientFactory {
  override def create(config: SchemaRegistryClientKafkaConfig): AzureSchemaRegistryClient = {
    new AzureSchemaRegistryClient(config)
  }

}

class AzureSchemaRegistryClient(config: SchemaRegistryClientKafkaConfig) extends SchemaRegistryClient {

  private val azureConfiguration = AzureConfigurationFactory.createFromKafkaProperties(config.kafkaProperties)
  private val credential = AzureTokenCredentialFactory.createCredential(azureConfiguration)
  private val httpPipeline = AzureHttpPipelineFactory.createPipeline(azureConfiguration, credential)
  private val fullyQualifiedNamespace = config.kafkaProperties("schema.registry.url")
  private val schemaGroup = config.kafkaProperties("schema.group")

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
    SchemaRegistryJsonSerializer)

  override def getSchemaById(id: SchemaId): SchemaWithMetadata = ???

  override protected def getBySubjectAndVersion(topicName: String, version: Int, isKey: Boolean): Validated[SchemaRegistryError, SchemaWithMetadata] = {
    getOneMatchingSchemaName(topicName, isKey).andThen { fullSchemaName =>
      try {
        Valid(schemaRegistryClient.getSchema(schemaGroup, fullSchemaName, version))
      } catch {
        case NonFatal(ex) => Invalid(SchemaRegistryUnknownError(ex.getMessage, ex))
      }
    }.map { result =>
      SchemaWithMetadata(new AvroSchema(result.getDefinition), SchemaId.fromString(result.getProperties.getId))
    }
  }

  override protected def getLatestFreshSchema(topicName: String, isKey: Boolean): Validated[SchemaRegistryError, SchemaWithMetadata] = {
    getOneMatchingSchemaName(topicName, isKey).andThen { fullSchemaName =>
      try {
        FluxUtil.withContext(enhancedSchemasService.getSchemaByNameWithResponseAsync(schemaGroup, fullSchemaName, _))
          .map[Validated[SchemaRegistryError, SchemaWithMetadata]](asJavaFunction((response: SchemasGetByIdResponse) =>
            // must to be done in async context - otherwise schema string is closed
            Valid(toSchemaWithMetadata(response))
          )).block()
      } catch {
        case NonFatal(ex) => Invalid(SchemaRegistryUnknownError(ex.getMessage, ex))
      }
    }
  }

  private def toSchemaWithMetadata(response: SchemasGetByIdResponse) = {
    val schemaId = SchemaId.fromString(response.getDeserializedHeaders.getSchemaId)
    SchemaWithMetadata(new AvroSchema(IOUtils.toString(response.getValue)), schemaId)
  }

  override def getAllTopics: Validated[SchemaRegistryError, List[String]] = {
    val fullSchemaNames = getAllFullSchemaNames
    fullSchemaNames.map(_.collect {
      case FullSchemaNameDecomposed(topicName, _, false) => topicName
    })
  }

  override def getAllVersions(topicName: String, isKey: Boolean): Validated[SchemaRegistryError, List[Integer]] = {
    getOneMatchingSchemaName(topicName, isKey).andThen(getVersions)
  }

  private def getOneMatchingSchemaName(topicName: String, isKey: Boolean): Validated[SchemaRegistryError, String] = {
    getAllFullSchemaNames.andThen { fullSchemaNames =>
      val matchingFullSchemaNames = fullSchemaNames.collect {
        case fullSchemaName@FullSchemaNameDecomposed(`topicName`, _, `isKey`) =>
          fullSchemaName
      }
      matchingFullSchemaNames match {
        case one :: Nil =>
          Valid(one)
        case Nil =>
          Invalid(SchemaNotFound(s"Schema for topic: $topicName not found"))
        case moreThenOnce =>
          // We can't pick one in this case because there is no option to recognize which one is newer.
          // I've tried to parse schemaId to UUID but it is saved in Version 4 UUID format (random) and has no timestamp
          Invalid(SchemaRegistryUnknownError(s"Ambiguous schemas: ${moreThenOnce.mkString(", ")} for topic: $topicName", null))
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

  // TODO: handle json schema as well when it will be available in SchemaFormat
  def registerSchemaVersionIfNotExists(schema: AvroSchema): SchemaProperties = {
    val schemaString = schema.canonicalString()
    try {
      schemaRegistryClient.getSchemaProperties(schemaGroup, schema.name(), schemaString, SchemaFormat.AVRO)
    } catch {
      case _: ResourceNotFoundException =>
        schemaRegistryClient.registerSchema(schemaGroup, schema.name(), schemaString, SchemaFormat.AVRO)
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
