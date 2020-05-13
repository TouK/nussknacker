package pl.touk.nussknacker.engine.avro.schemaregistry

import cats.data.Validated
import org.apache.avro.Schema

trait SchemaRegistryClient extends Serializable {

  def getBySubjectAndVersion(subject: String, version: Int): Validated[SchemaRegistryError, Schema]

  /**
    * Latest fresh schema by subject - it should be always fresh schema
    *
    * @param subject
    * @return
    */
  def getLatestFreshSchema(subject: String): Validated[SchemaRegistryError, Schema]

  /**
    * Latest schema by subject - we assume there can be some latency
    *
    * @param subject
    * @return
    */
  def getLatestSchema(subject: String): Validated[SchemaRegistryError, Schema]

  def getSchema(subject: String, version: Option[Int]): Validated[SchemaRegistryError, Schema] =
    version
      .map(ver => getBySubjectAndVersion(subject, ver))
      .getOrElse(getLatestFreshSchema(subject))
}
