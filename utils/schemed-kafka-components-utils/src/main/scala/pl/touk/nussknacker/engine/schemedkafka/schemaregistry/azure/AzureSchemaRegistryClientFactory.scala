package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.azure

import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import com.azure.core.util.{BinaryData, Context, FluxUtil}
import com.azure.data.schemaregistry.SchemaRegistryVersion
import com.azure.data.schemaregistry.implementation.models.SchemasGetByIdResponse
import com.azure.data.schemaregistry.implementation.{AzureSchemaRegistryImpl, AzureSchemaRegistryImplBuilder}
import com.google.common.base.CaseFormat
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import org.apache.commons.io.IOUtils
import pl.touk.nussknacker.engine.kafka.SchemaRegistryClientKafkaConfig
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{SchemaId, SchemaNotFound, SchemaRegistryClient, SchemaRegistryClientFactory, SchemaRegistryError, SchemaRegistryUnknownError, SchemaWithMetadata}
import reactor.core.publisher.Mono

import scala.jdk.CollectionConverters._
import scala.compat.java8.FunctionConverters._
import scala.util.control.NonFatal

class AzureSchemaRegistryClientFactory extends SchemaRegistryClientFactory {
  override def create(config: SchemaRegistryClientKafkaConfig): AzureSchemaRegistryClient = {
    new AzureSchemaRegistryClient(config)
  }

}

class AzureSchemaRegistryClient(config: SchemaRegistryClientKafkaConfig) extends SchemaRegistryClient {

  private val httpPipeline = AzureHttpPipelineFactory.createPipeline(config)
  private val fullyQualifiedNamespace = config.kafkaProperties("schema.registry.url")
  private val schemaGroup = config.kafkaProperties("schema.group")

  // TODO: check if SchemaRegistryClient abstraction will be ok for our purpose
  private val schemaRegistryImpl: AzureSchemaRegistryImpl = new AzureSchemaRegistryImplBuilder()
    .endpoint(fullyQualifiedNamespace)
    .apiVersion(SchemaRegistryVersion.getLatest.getVersion)
    .pipeline(httpPipeline)
    .buildClient

  // We need to create our own schemas service because some operations like schema listing are not exposed by default client
  // others like listing of versions are implemented incorrectly
  private val enhancedSchemasService = new EnhancedSchemasImpl(
    fullyQualifiedNamespace,
    SchemaRegistryVersion.getLatest.getVersion,
    httpPipeline,
    SchemaRegistryJsonSerializer)

  override def getSchemaById(id: SchemaId): SchemaWithMetadata = ???

  override protected def getBySubjectAndVersion(topic: String, version: Int, isKey: Boolean): Validated[SchemaRegistryError, SchemaWithMetadata] = ???

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

  override def getLatestSchemaId(topic: String, isKey: Boolean): Validated[SchemaRegistryError, SchemaId] = ???

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

  // TODO: handle json schema as well (take ParsedSchema here)
  def registerSchema(schema: AvroSchema): String = {
    FluxUtil.withContext(context =>
      schemaRegistryImpl.getSchemas.registerWithResponseAsync(
        schemaGroup, schema.name(), BinaryData.fromString(schema.canonicalString()), schema.canonicalString().length, context)).block().getDeserializedHeaders.getSchemaId
  }

  private def invokeBlocking[T](f: Context => Mono[T]): Validated[SchemaRegistryError, T] = {
    try {
      Valid(FluxUtil.withContext(context => f(context)).block())
    } catch {
      case NonFatal(ex) => Invalid(SchemaRegistryUnknownError(ex.getMessage, ex))
    }
  }

}

object FullSchemaNameDecomposed {

  val KeySuffix = "Key"
  val ValueSuffix = "Value"

  def topicNameFromKeySchemaName(schemaName: String): Option[String] = unapply(schemaName).filter(_._3).map(_._1)

  def topicNameFromValueSchemaName(schemaName: String): Option[String] = unapply(schemaName).filterNot(_._3).map(_._1)

  def keySchemaNameFromTopicName(topicName: String): String = CaseFormat.LOWER_HYPHEN.to(CaseFormat.UPPER_CAMEL, topicName) + KeySuffix

  def valueSchemaNameFromTopicName(topicName: String): String = CaseFormat.LOWER_HYPHEN.to(CaseFormat.UPPER_CAMEL, topicName) + ValueSuffix

  // Decompose schema (full) name to: topicName, namespace and isKey
  def unapply(schemaFullName: String): Option[(String, String, Boolean)] = {
    // Only schemas ends with Key or Value will be recognized - we assume that other schemas are for other purpose
    // and won't appear on any Nussknacker lists.
    if (schemaFullName.endsWith(KeySuffix)) {
      val (topicName, namespace) = extractTopicNameAndNamespace(schemaFullName, KeySuffix)
      Some(topicName, namespace, true)
    } else if (schemaFullName.endsWith(ValueSuffix)) {
      val (topicName, namespace) = extractTopicNameAndNamespace(schemaFullName, ValueSuffix)
      Some(topicName, namespace, false)
    } else {
      None
    }
  }

  // TODO: it probable should be configurable
  private def extractTopicNameAndNamespace(schemaFullName: String, suffix: String): (String, String) = {
    val schemaFullNameWithoutSuffix = schemaFullName.replaceFirst(suffix + "$", "")
    // We remove namespace from topic name because we don't want to force ppl to create technically looking event hub names
    // Also event hubs already has dedicated namespaces
    val cleanedSchemaName = schemaFullNameWithoutSuffix.replaceFirst("^.*\\.", "")
    // Topics (event hubs) can have capital letters but generally it is a good rule to not use them to avoid
    // mistakes in interpretation shortcuts and so on - similar like with sql tables
    val topicName = CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_HYPHEN, cleanedSchemaName)
    val namespace = schemaFullNameWithoutSuffix.replaceFirst("\\..*?$", "")
    (topicName, namespace)
  }

}
