package pl.touk.nussknacker.engine.util.json

import cats.data.Validated
import com.github.ghik.silencer.silent
import org.everit.json.schema._
import org.json.JSONException
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap

import java.util.regex.Pattern
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
        case exc => s"Unknown error message type: ${exc.getMessage}"
      }.map(_ => data)

  }

  implicit class ExtendedObjectSchema(schema: ObjectSchema) {
    def hasPatternProperties: Boolean = schema.patternProperties.nonEmpty

    def acceptsEverythingAsAdditionalProperty: Boolean = schema.permitsAdditionalProperties() && schema.getSchemaOfAdditionalProperties == null

    @silent("deprecated")
    def patternProperties: Map[Pattern, Schema] = {
      // getPatternProperties is deprecated but for now there is no alternative https://github.com/everit-org/json-schema/issues/304
      schema.getPatternProperties.asScala.toMap
    }

    def hasOnlyAdditionalProperties: Boolean = {
      schema.permitsAdditionalProperties() && schema.getPropertySchemas.isEmpty
    }

    def requiredPropertiesSchemas: Map[String, Schema] = {
      val requiredProperties = schema.getRequiredProperties.asScala.toSet
      schema.getPropertySchemas.asScala.toMap.filterKeysNow(requiredProperties.contains)
    }
  }

  private def isNullSchema(sch: Schema): Boolean = sch match {
    case _: NullSchema => true
    case _ => false
  }

}
