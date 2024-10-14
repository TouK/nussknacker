package pl.touk.nussknacker.engine.schemedkafka.schemaregistry

import enumeratum.values.{IntEnum, IntEnumEntry}
import pl.touk.nussknacker.engine.util.convert.IntValue

sealed trait SchemaVersionOption

object SchemaVersionOption {

  val LatestOptionName = "latest"
  val JsonOptionName   = "Json"
  val PlainOptionName  = "Plain"

  def byName(name: String): SchemaVersionOption = {
    name match {
      case `LatestOptionName` => LatestSchemaVersion
      case IntValue(version)  => ExistingSchemaVersion(version)
      case `JsonOptionName`   => DynamicSchemaVersion(JsonTypes.Json)
      case `PlainOptionName`  => DynamicSchemaVersion(JsonTypes.Plain)
      case _                  => throw new IllegalArgumentException(s"Unexpected schema version option: $name")
    }
  }

}

case class ExistingSchemaVersion(version: Int) extends SchemaVersionOption

case object LatestSchemaVersion extends SchemaVersionOption

case class DynamicSchemaVersion(typ: JsonTypes) extends SchemaVersionOption

sealed abstract class JsonTypes(val value: Int) extends IntEnumEntry {

  implicit def apply(): Int = {
    this.value
  }

}

object JsonTypes extends IntEnum[JsonTypes] {
  val values = findValues

  case object Json  extends JsonTypes(1)
  case object Plain extends JsonTypes(2)
}
