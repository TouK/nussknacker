package pl.touk.nussknacker.engine.util.json

import cats.data.Validated
import org.everit.json.schema.{CombinedSchema, NullSchema, Schema, ValidationException}
import org.json.JSONException

import scala.util.Try

object JsonSchemaImplicits {

  import collection.JavaConverters._

  implicit class ExtendedSchema(schema: Schema) {

    def isNullableSchema: Boolean = schema match {
      case combined: CombinedSchema => combined.getSubschemas.asScala.exists(isNullSchema)
      case sch: Schema => isNullSchema(sch)
    }

    //validate can change json object.. e.g. fill default values
    def validateData(data: AnyRef): Validated[String, AnyRef] =
      Validated.fromTry(Try(schema.validate(data))).leftMap{
        case ve: ValidationException => ve.getAllMessages.asScala.mkString("\n\n")
        case je: JSONException => s"Invalid JSON: ${je.getMessage}"
        case _ => "unknown error message"
      }.map(_ => data)

  }

  private def isNullSchema(sch: Schema): Boolean = sch match {
    case _: NullSchema => true
    case _ => false
  }

}
