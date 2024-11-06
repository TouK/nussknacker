package pl.touk.nussknacker.engine.schemedkafka.schemaregistry

import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.ContentTypes.ContentType
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
      case `JsonOptionName`   => PassedContentType(ContentTypes.JSON)
      case `PlainOptionName`  => PassedContentType(ContentTypes.PLAIN)
      case _                  => throw new IllegalArgumentException(s"Unexpected schema version option: $name")
    }
  }

}

case class ExistingSchemaVersion(version: Int) extends SchemaVersionOption

case object LatestSchemaVersion extends SchemaVersionOption

case class PassedContentType(typ: ContentType) extends SchemaVersionOption

object ContentTypes extends Enumeration {
  type ContentType = Value

  val JSON, PLAIN = Value
}
