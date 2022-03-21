package pl.touk.nussknacker.engine.lite.components.requestresponse.jsonschema.jsonschemautils

import cats.data.Validated.condNel
import cats.data.ValidatedNel
import cats.implicits.{catsSyntaxValidatedId, _}
import com.typesafe.scalalogging.LazyLogging
import org.everit.json.schema.{ArraySchema, ObjectSchema, Schema}
import pl.touk.nussknacker.engine.api.typed.typing.{TypedClass, TypedObjectTypingResult, TypingResult, Unknown}

import scala.annotation.tailrec
import scala.collection.JavaConverters._

class JsonSchemaSubclassDeterminer(parentSchema: Schema) extends LazyLogging {

  private val jsonTypeDefinitionExtractor = new JsonSchemaTypeDefinitionExtractor

  def validateTypingResultToSchema(typingResult: TypingResult): ValidatedNel[String, Unit] =
    validateTypingResultToSchema(typingResult, parentSchema, None)

  private def getFieldsWithDefaultValues(propertySchemas: Map[String, Schema]): Set[String] = {
    propertySchemas.filter(_._2.hasDefaultValue).keySet
  }

  private def msgWithLocation(msg: String)(implicit location: String) = s"[$location] $msg"

  private def validateObjectSchema(e: TypedObjectTypingResult, objectSchema: ObjectSchema): ValidatedNel[String, Unit] = {
    implicit val location: String = objectSchema.getLocation.toString

    val objectProperties: Map[String, Schema] = objectSchema.getPropertySchemas.asScala.toMap
    val requiredFieldNames: Set[String] = objectSchema.getRequiredProperties.asScala.toList.filterNot(getFieldsWithDefaultValues(objectProperties)).toSet

    val fieldsToValidate: Map[String, TypingResult] = e.fields.filterKeys(objectProperties.contains)
    val schemaFields: Map[String, Schema] = objectSchema.getPropertySchemas.asScala.toMap

    val requiredFieldsValidation = {
      val missingFields = requiredFieldNames.filterNot(e.fields.contains)
      condNel(missingFields.isEmpty, (), msgWithLocation(s"Missing fields: [${missingFields.mkString(",")}]"))
    }

    val schemaFieldsValidation = {
      fieldsToValidate.flatMap{ case (key, value) =>
        schemaFields.get(key).map(f => validateTypingResultToSchema(value, f, Option(key)))
      }.foldLeft[ValidatedNel[String, Unit]](().validNel)((a, b) => a combine b)
    }

    val additionalPropertiesValidation = {
      val additionalProperties = e.fields.keySet.diff(requiredFieldNames)
      condNel(objectSchema.permitsAdditionalProperties || additionalProperties.isEmpty, (),
        msgWithLocation(s"The object has redundant fields: $additionalProperties"))
    }

    requiredFieldsValidation combine schemaFieldsValidation combine additionalPropertiesValidation
  }

  private def canBeSubclassOf(a: TypingResult, b: TypingResult, fieldName: Option[String])(implicit location: String): ValidatedNel[String, Unit] = {
    val fieldNameStr = fieldName.fold("")(n => s"'$n' ")
    condNel(a.canBeSubclassOf(b), (),
      msgWithLocation(s"Field ${fieldNameStr}is of the wrong type. Expected: ${b.display}, actual: ${a.display}")
    )
  }

  private def extractListParameter(tc: TypedClass): TypingResult = {
    tc.params.headOption.getOrElse(Unknown)
  }

  @tailrec
  private def validateTypingResultToSchema(typingResult: TypingResult, schema: Schema, fieldName: Option[String]): ValidatedNel[String, Unit] = {
    implicit val location: String = schema.getLocation.toString

    (typingResult, schema) match {
      case (tr: TypedObjectTypingResult, objSchema: ObjectSchema) => validateObjectSchema(tr, objSchema)
      case (tc@TypedClass(cl, _), arrSchema: ArraySchema) if classOf[java.util.List[_]].isAssignableFrom(cl) =>
        validateTypingResultToSchema(extractListParameter(tc), arrSchema.getAllItemSchema, fieldName)
      case (anyTypingResult, anySchema) =>
        val schemaAsTypedResult = jsonTypeDefinitionExtractor.resolveJsonTypingResult(anySchema)
        canBeSubclassOf(anyTypingResult, schemaAsTypedResult, fieldName)
    }
  }

}
