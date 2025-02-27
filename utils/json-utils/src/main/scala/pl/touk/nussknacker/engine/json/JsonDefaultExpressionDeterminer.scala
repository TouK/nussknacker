package pl.touk.nussknacker.engine.json

import cats.data.Validated.{Invalid, Valid}
import cats.data.ValidatedNel
import org.everit.json.schema._
import org.json.JSONObject
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.json.JsonDefaultExpressionDeterminer.{InvalidValue, NullNotAllowed, TypeNotSupported}

import scala.language.implicitConversions
import scala.reflect.ClassTag

object JsonDefaultExpressionDeterminer {

  private val extractorWithHandleNotSupported = new JsonDefaultExpressionDeterminer(true)

  def determineWithHandlingNotSupportedTypes(schema: Schema, paramName: Option[ParameterName])(
      implicit nodeId: NodeId
  ): ValidatedNel[ProcessCompilationError, Option[Expression]] =
    extractorWithHandleNotSupported
      .determine(schema)
      .leftMap(_.map(customNodeError => customNodeError.copy(nodeId = nodeId.id, paramName = paramName)))

  private def createCustomNodeError(errMsg: String): CustomNodeError = CustomNodeError("", errMsg, None)

  final val NullNotAllowed = createCustomNodeError("Value is not nullable")

  final val InvalidValue = createCustomNodeError("Value is invalid")

  final val TypeNotSupported = (schema: Schema) => createCustomNodeError(s"Default value for $schema is not supported.")

}

class JsonDefaultExpressionDeterminer(handleNotSupported: Boolean) {

  private implicit def asSpelExpression(expression: String): Expression = Expression.spel(expression)

  private val validatedNullExpression: ValidatedNel[CustomNodeError, Option[Expression]] =
    Valid(Option(asSpelExpression("null")))

  def determine(schema: Schema): ValidatedNel[CustomNodeError, Option[Expression]] =
    if (schema.hasDefaultValue)
      doDetermine(schema)
    else
      Valid(None)

  // TODO: Add support for others type: enum, const, combined, etc..
  private def doDetermine(schema: Schema): ValidatedNel[CustomNodeError, Option[Expression]] =
    schema match {
      case s: NumberSchema if s.requiresInteger() => withValidation[Number](schema, l => s"${l}L")
      case _: NumberSchema                        => withValidation[Number](schema, _.toString)
      case _: BooleanSchema                       => withValidation[java.lang.Boolean](schema, _.toString)
      case _: NullSchema                          => validatedNullExpression
      case _: StringSchema                        => withValidation[String](schema, str => s"'$str'")
      case _: ObjectSchema                        => withNullValidation(schema)
      case _: ArraySchema                         => withNullValidation(schema)
      case _                                      => typeNotSupported(schema)
    }

  private def withValidation[T <: AnyRef: ClassTag](
      schema: Schema,
      toExpression: T => Expression
  ): ValidatedNel[CustomNodeError, Option[Expression]] = {
    Option.apply(schema.getDefaultValue) match {
      case Some(JSONObject.NULL) if schema.isNullable => validatedNullExpression
      case Some(value: T)                             => Valid(Option(toExpression(value)))
      case Some(_)                                    => Invalid(InvalidValue).toValidatedNel
      case None                                       => Invalid(NullNotAllowed).toValidatedNel
    }
  }

  // Right now we use nullable = true, default = null as kind of union type..
  private def withNullValidation(schema: Schema): ValidatedNel[CustomNodeError, Option[Expression]] =
    Option.apply(schema.getDefaultValue) match {
      case Some(JSONObject.NULL) if schema.isNullable => validatedNullExpression
      case _                                          => typeNotSupported(schema)
    }

  private def typeNotSupported(implicit fieldSchema: Schema): ValidatedNel[CustomNodeError, Option[Expression]] =
    if (handleNotSupported)
      Valid(None)
    else
      Invalid(TypeNotSupported(fieldSchema)).toValidatedNel

}
