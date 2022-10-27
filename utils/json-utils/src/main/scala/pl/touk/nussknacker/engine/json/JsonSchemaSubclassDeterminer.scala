package pl.touk.nussknacker.engine.json

import cats.data.Validated.condNel
import cats.data.{Validated, ValidatedNel}
import cats.implicits.{catsSyntaxValidatedId, _}
import com.typesafe.scalalogging.LazyLogging
import org.everit.json.schema.{ArraySchema, ObjectSchema, Schema}
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError
import pl.touk.nussknacker.engine.api.typed.typing.{TypedClass, TypedObjectTypingResult, TypingResult, Unknown}

import scala.annotation.tailrec
import scala.collection.JavaConverters._

class JsonSchemaSubclassDeterminer(parentSchema: Schema) extends LazyLogging {

  private val ValidationErrorMessageBase = "Provided value does not match scenario output JSON schema"
  private val jsonTypeDefinitionExtractor = SwaggerBasedJsonSchemaTypeDefinitionExtractor

  def validateTypingResultToSchema(typingResult: TypingResult, schemaParamName: String)(implicit nodeId: NodeId): Validated[CustomNodeError, Unit] =
    validateTypingResultToSchema(typingResult, parentSchema, schemaParamName)
      .leftMap(errors => prepareError(errors.toList, schemaParamName))

  private def prepareError(errors: List[String], schemaParamName: String)(implicit nodeId: NodeId): CustomNodeError = errors match {
    case Nil => CustomNodeError(ValidationErrorMessageBase, Option(schemaParamName))
    case _ => CustomNodeError(errors.mkString(s"$ValidationErrorMessageBase - errors:\n", ", ", ""), Option(schemaParamName))
  }

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
        schemaFields.get(key).map(f => validateTypingResultToSchema(value, f, key))
      }.foldLeft[ValidatedNel[String, Unit]](().validNel)((a, b) => a combine b)
    }

    val additionalPropertiesValidation = {
      val additionalProperties = e.fields.keySet.diff(objectProperties.keySet)
      condNel(objectSchema.permitsAdditionalProperties || additionalProperties.isEmpty, (),
        msgWithLocation(s"The object has redundant fields: $additionalProperties"))
    }

    requiredFieldsValidation combine schemaFieldsValidation combine additionalPropertiesValidation
  }

  private def canBeSubclassOf(a: TypingResult, b: TypingResult, fieldName: String)(implicit location: String): ValidatedNel[String, Unit] = {
    condNel(a.canBeSubclassOf(b), (),
      msgWithLocation(s"Field '$fieldName' is of the wrong type. Expected: ${b.display}, actual: ${a.display}")
    )
  }

  private def extractListParameter(tc: TypedClass): TypingResult = {
    tc.params.headOption.getOrElse(Unknown)
  }

  @tailrec
  private def validateTypingResultToSchema(typingResult: TypingResult, schema: Schema, fieldName: String): ValidatedNel[String, Unit] = {
    implicit val location: String = schema.getLocation.toString

    (typingResult, schema) match {
      case (tr: TypedObjectTypingResult, objSchema: ObjectSchema) => validateObjectSchema(tr, objSchema)
      case (tc@TypedClass(cl, _), arrSchema: ArraySchema) if classOf[java.util.List[_]].isAssignableFrom(cl) =>
        validateTypingResultToSchema(extractListParameter(tc), arrSchema.getAllItemSchema, fieldName)
      case (anyTypingResult, anySchema) =>
        val schemaAsTypedResult = jsonTypeDefinitionExtractor.swaggerType(anySchema).typingResult
        canBeSubclassOf(anyTypingResult, schemaAsTypedResult, fieldName)
    }
  }

}
