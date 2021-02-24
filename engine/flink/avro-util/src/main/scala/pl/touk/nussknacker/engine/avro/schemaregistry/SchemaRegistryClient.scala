package pl.touk.nussknacker.engine.avro.schemaregistry

import cats.data.Validated

trait SchemaRegistryClient extends Serializable {

  protected def getBySubjectAndVersion(topic: String, version: Int, isKey: Boolean): Validated[SchemaRegistryError, SchemaWithMetadata]

  /**
    * Latest fresh schema by subject - it should be always fresh schema
    *
    * @param topic
    * @param isKey
    * @return
    */
  protected def getLatestFreshSchema(topic: String, isKey: Boolean): Validated[SchemaRegistryError, SchemaWithMetadata]

  def getFreshSchema(topic: String, version: Option[Int], isKey: Boolean): Validated[SchemaRegistryError, SchemaWithMetadata] =
    version
      .map(ver => getBySubjectAndVersion(topic, ver, isKey))
      .getOrElse(getLatestFreshSchema(topic, isKey))

  def getAllTopics: Validated[SchemaRegistryError, List[String]]

  def getAllVersions(topic: String, isKey: Boolean): Validated[SchemaRegistryError, List[Integer]]

}

