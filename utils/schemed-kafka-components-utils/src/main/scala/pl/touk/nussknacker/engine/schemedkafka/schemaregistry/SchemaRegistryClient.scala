package pl.touk.nussknacker.engine.schemedkafka.schemaregistry

import cats.data.Validated
import io.confluent.kafka.schemaregistry.ParsedSchema
import pl.touk.nussknacker.engine.kafka.UnspecializedTopicName
import pl.touk.nussknacker.engine.schemedkafka.TopicSelectionStrategy

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

  def isTopicWithSchema(topic: String, strategy: TopicSelectionStrategy): Boolean = {
    val topicsWithSchema = strategy.getTopics(this)
    topicsWithSchema.exists(_.map(_.name).contains(topic))
  }

}

// This trait is mainly for testing mechanism purpose - in production implementation we assume that all schemas
// are registered before usage of client. We don't want to merge both traits because it can be hard to
// manage caching when both writing and reading operation will be available
trait SchemaRegistryClientWithRegistration extends SchemaRegistryClient {

  def registerSchema(topic: UnspecializedTopicName, isKey: Boolean, schema: ParsedSchema): SchemaId

}
