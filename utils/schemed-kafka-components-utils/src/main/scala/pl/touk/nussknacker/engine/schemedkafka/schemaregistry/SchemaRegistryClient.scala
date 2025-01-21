package pl.touk.nussknacker.engine.schemedkafka.schemaregistry

import cats.data.Validated
import io.confluent.kafka.schemaregistry.ParsedSchema
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, UnspecializedTopicName}
import pl.touk.nussknacker.engine.schemedkafka.TopicsWithExistingSubjectSelectionStrategy

trait SchemaRegistryClient extends Serializable {

  def getSchemaById(id: SchemaId): SchemaWithMetadata

  protected def getByTopicAndVersion(
      topic: UnspecializedTopicName,
      version: Int,
      isKey: Boolean
  ): Validated[SchemaRegistryError, SchemaWithMetadata]

  /**
    * Latest fresh schema by topic - it should be always fresh schema
    *
    * @param topic
    * @param isKey
    * @return
    */
  protected def getLatestFreshSchema(
      topic: UnspecializedTopicName,
      isKey: Boolean
  ): Validated[SchemaRegistryError, SchemaWithMetadata]

  def getFreshSchema(
      topic: UnspecializedTopicName,
      version: Option[Int],
      isKey: Boolean
  ): Validated[SchemaRegistryError, SchemaWithMetadata] =
    version
      .map(ver => getByTopicAndVersion(topic, ver, isKey))
      .getOrElse(getLatestFreshSchema(topic, isKey))

  def getAllTopics: Validated[SchemaRegistryError, List[UnspecializedTopicName]]

  def getAllVersions(topic: UnspecializedTopicName, isKey: Boolean): Validated[SchemaRegistryError, List[Integer]]

  def isTopicWithSchema(topic: String, kafkaConfig: KafkaConfig): Boolean = {
    if (!kafkaConfig.showTopicsWithoutSchema) {
      true
    } else {
      val topicsWithSchema = new TopicsWithExistingSubjectSelectionStrategy().getTopics(this, kafkaConfig)
      topicsWithSchema.exists(_.map(_.name).contains(topic))
    }
  }

}

object EmptySchemaRegistry extends SchemaRegistryClient {

  private val errorMessage = "There is no schema in empty schema registry";
  private val error        = SchemaError(errorMessage)

  override def getSchemaById(id: SchemaId): SchemaWithMetadata = throw new IllegalStateException(errorMessage)

  override protected def getByTopicAndVersion(
      topic: UnspecializedTopicName,
      version: Int,
      isKey: Boolean
  ): Validated[SchemaRegistryError, SchemaWithMetadata] = Validated.Invalid(error)

  override protected def getLatestFreshSchema(
      topic: UnspecializedTopicName,
      isKey: Boolean
  ): Validated[SchemaRegistryError, SchemaWithMetadata] = Validated.Invalid(error)

  override def getAllTopics: Validated[SchemaRegistryError, List[UnspecializedTopicName]] = Validated.Valid(List())

  override def getAllVersions(
      topic: UnspecializedTopicName,
      isKey: Boolean
  ): Validated[SchemaRegistryError, List[Integer]] = Validated.Invalid(error)

}

// This trait is mainly for testing mechanism purpose - in production implementation we assume that all schemas
// are registered before usage of client. We don't want to merge both traits because it can be hard to
// manage caching when both writing and reading operation will be available
trait SchemaRegistryClientWithRegistration extends SchemaRegistryClient {

  def registerSchema(topic: UnspecializedTopicName, isKey: Boolean, schema: ParsedSchema): SchemaId

}
