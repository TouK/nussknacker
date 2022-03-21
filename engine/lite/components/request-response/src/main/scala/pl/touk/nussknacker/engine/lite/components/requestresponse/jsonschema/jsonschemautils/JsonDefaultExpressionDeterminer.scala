package pl.touk.nussknacker.engine.lite.components.requestresponse.jsonschema.jsonschemautils

import cats.data.Validated.{Invalid, Valid}
import cats.data.ValidatedNel
import org.everit.json.schema._
import org.json.JSONObject
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.lite.components.requestresponse.jsonschema.jsonschemautils.JsonDefaultExpressionDeterminer._

import scala.reflect.ClassTag
import scala.language.implicitConversions

object JsonDefaultExpressionDeterminer {

  private val extractorWithHandleNotSupported = new JsonDefaultExpressionDeterminer(true)

  private val extractorWithoutHandleNotSupported = new JsonDefaultExpressionDeterminer(false)

  def determineWithHandlingNotSupportedTypes(schema: Schema): ValidatedNel[JsonDefaultToSpELExpressionError, Option[Expression]] =
    extractorWithHandleNotSupported.determine(schema)

  def determineWitNotHandlingNotSupportedTypes(schema: Schema): ValidatedNel[JsonDefaultToSpELExpressionError, Option[Expression]] =
    extractorWithoutHandleNotSupported.determine(schema)

  sealed trait JsonDefaultToSpELExpressionError extends Exception

  final case object NullNotAllowed extends JsonDefaultToSpELExpressionError {
    override val getMessage: String = "Value is not nullable"
  }

  final case object InvalidValue extends JsonDefaultToSpELExpressionError {
    override val getMessage: String = "Value is invalid"
  }

  case class TypeNotSupported(schema: Schema) extends JsonDefaultToSpELExpressionError {
    override def getMessage: String = s"Default value for $schema is not supported."
  }

}

class JsonDefaultExpressionDeterminer(handleNotSupported: Boolean) {

  private implicit def asSpelExpression(expression: String): Expression =
    Expression(
      language = "spel",
      expression = expression
    )

  private val validatedNullExpression: ValidatedNel[JsonDefaultToSpELExpressionError, Option[Expression]] =
    Valid(Option(asSpelExpression("null")))

  def determine(schema: Schema): ValidatedNel[JsonDefaultToSpELExpressionError, Option[Expression]] =
    if (schema.hasDefaultValue)
      doDetermine(schema)
    else
      Valid(None)

  //TODO: Add support for others type: enum, const, combined, etc..
  private def doDetermine(schema: Schema): ValidatedNel[JsonDefaultToSpELExpressionError, Option[Expression]] =
    schema match {
      case s: NumberSchema if s.requiresInteger() => withValidation[Number](schema, l => s"${l}L")
      case _: NumberSchema => withValidation[Number](schema, _.toString)
      case _: BooleanSchema => withValidation[java.lang.Boolean](schema, _.toString)
      case _: TrueSchema => withValidation[java.lang.Boolean](schema, _.toString)
      case _: FalseSchema => withValidation[java.lang.Boolean](schema, _.toString)
      case _: NullSchema => validatedNullExpression
      case _: StringSchema => withValidation[String](schema, str => s"'$str'")
      case _: ObjectSchema => withNullValidation(schema)
      case _: ArraySchema => withNullValidation(schema)
      case _ => typeNotSupported(schema)
    }

  private def withValidation[T <: AnyRef : ClassTag](schema: Schema, toExpression: T => Expression): ValidatedNel[JsonDefaultToSpELExpressionError, Option[Expression]] = {
    Option.apply(schema.getDefaultValue) match {
      case Some(JSONObject.NULL) if schema.isNullable => validatedNullExpression
      case Some(value: T) => Valid(Option(toExpression(value)))
      case Some(_) => Invalid(InvalidValue).toValidatedNel
      case None => Invalid(NullNotAllowed).toValidatedNel
    }
  }

  //Right now we use nullable = true, default = null as kind of union type..
  private def withNullValidation(schema: Schema): ValidatedNel[JsonDefaultToSpELExpressionError, Option[Expression]] = Option.apply(schema.getDefaultValue) match {
    case Some(JSONObject.NULL) if schema.isNullable => validatedNullExpression
    case _ => typeNotSupported(schema)
  }

  private def typeNotSupported(implicit fieldSchema: Schema): ValidatedNel[JsonDefaultToSpELExpressionError, Option[Expression]] =
    if (handleNotSupported)
      Valid(None)
    else
      Invalid(TypeNotSupported(fieldSchema)).toValidatedNel

}
