package pl.touk.nussknacker.engine.schemedkafka.schemaregistry

import pl.touk.nussknacker.engine.util.convert.IntValue

sealed trait SchemaVersionOption

object SchemaVersionOption {

  val LatestOptionName = "latest"

  def byName(name: String): SchemaVersionOption = {
    name match {
      case `LatestOptionName` => LatestSchemaVersion
      case IntValue(version)  => ExistingSchemaVersion(version)
      case _                  => throw new IllegalArgumentException(s"Unexpected schema version option: $name")
    }
  }

}

case class ExistingSchemaVersion(version: Int) extends SchemaVersionOption

case object LatestSchemaVersion extends SchemaVersionOption
