package pl.touk.nussknacker.engine.avro.schemaregistry

import cats.data.Validated
import org.apache.avro.Schema

trait SchemaRegistryClient extends Serializable {

  def getBySubjectAndVersion(topic: String, version: Int, isKey: Boolean): Validated[SchemaRegistryError, Schema]

  /**
    * Latest fresh schema by subject - it should be always fresh schema
    *
    * @param topic
    * @param isKey
    * @return
    */
  def getLatestFreshSchema(topic: String, isKey: Boolean): Validated[SchemaRegistryError, Schema]

  /**
    * Latest schema by subject - we assume there can be some latency
    *
    * @param topic
    * @param isKey
    * @return
    */
  def getLatestSchema(topic: String, isKey: Boolean): Validated[SchemaRegistryError, Schema]

  def getSchema(topic: String, version: Option[Int], isKey: Boolean): Validated[SchemaRegistryError, Schema] =
    version
      .map(ver => getBySubjectAndVersion(topic, ver, isKey))
      .getOrElse(getLatestSchema(topic, isKey))

  def getFreshSchema(topic: String, version: Option[Int], isKey: Boolean): Validated[SchemaRegistryError, Schema] =
    version
      .map(ver => getBySubjectAndVersion(topic, ver, isKey))
      .getOrElse(getLatestFreshSchema(topic, isKey))
}
