package pl.touk.nussknacker.engine.avro

import cats.data.Validated.{Invalid, Valid}
import cats.data.ValidatedNel
import io.circe.Json
import org.apache.avro.{LogicalTypes, Schema}
import pl.touk.nussknacker.engine.graph.expression.Expression

object AvroDefaultExpressionExtractor {

  sealed trait AvroDefaultToSpELExpressionError extends Exception

  case object NullNotAllowed extends AvroDefaultToSpELExpressionError {
    override val getMessage: String = "Value is not nullable"
  }

  case object InvalidValue extends AvroDefaultToSpELExpressionError {
    override val getMessage: String = "Value is invalid"
  }

  case class TypeNotSupported(schema: Schema) extends AvroDefaultToSpELExpressionError {
    override def getMessage: String = s"Default value for ${schema.getType} is not supported."
  }
}

/**
  *
  * @param handleNotSupported when true values of not supported types are returned as valid None, otherwise invalid TypeNotSupported is returned
  */
class AvroDefaultExpressionExtractor(schema: Schema, defaultValue: Json, handleNotSupported: Boolean) {
  import AvroDefaultExpressionExtractor._
  import pl.touk.nussknacker.engine.spel.Implicits.asSpelExpression

  private val typeNotSupported: ValidatedNel[AvroDefaultToSpELExpressionError, Option[Expression]] =
    if (handleNotSupported)
      Valid(None)
    else
      Invalid(TypeNotSupported(schema)).toValidatedNel

  /**
    * The expression has to correspond to type extracted by AvroSchemaTypeDefinitionExtractor#typeDefinition
    * !when applying changes keep in mind that this Schema.Type pattern matching is duplicated in AvroSchemaTypeDefinitionExtractor
    */
  def toExpression: ValidatedNel[AvroDefaultToSpELExpressionError, Option[Expression]] = {
    schema.getType match {
      case Schema.Type.RECORD =>
        typeNotSupported
      case Schema.Type.ENUM =>
        withValidation(_.asString.map(str => asSpelExpression(s"'$str'")))
      case Schema.Type.ARRAY =>
        typeNotSupported
      case Schema.Type.MAP =>
        typeNotSupported
      case Schema.Type.UNION =>
        typeNotSupported
      case Schema.Type.STRING if schema.getLogicalType == LogicalTypes.uuid() =>
        typeNotSupported
      case Schema.Type.BYTES | Schema.Type.FIXED if schema.getLogicalType != null && schema.getLogicalType.isInstanceOf[LogicalTypes.Decimal] =>
        typeNotSupported
      case Schema.Type.STRING =>
        withValidation(_.asString.map(str => asSpelExpression(s"'$str'")))
      case Schema.Type.BYTES =>
        typeNotSupported
      case Schema.Type.FIXED =>
        typeNotSupported
      case Schema.Type.INT if schema.getLogicalType == LogicalTypes.date() =>
        typeNotSupported
      case Schema.Type.INT if schema.getLogicalType == LogicalTypes.timeMillis() =>
        typeNotSupported
      case Schema.Type.INT =>
        withValidation(_.asNumber.flatMap(_.toInt).map(_.toString))
      case Schema.Type.LONG if schema.getLogicalType == LogicalTypes.timestampMillis() || schema.getLogicalType == LogicalTypes.timestampMicros() =>
        typeNotSupported
      case Schema.Type.LONG if schema.getLogicalType == LogicalTypes.timeMicros() =>
        typeNotSupported
      case Schema.Type.LONG =>
        withValidation(_.asNumber.flatMap(_.toLong).map(l => s"${l}L"))
      case Schema.Type.FLOAT =>
        withValidation(_.asNumber.map(_.toFloat.toString))
      case Schema.Type.DOUBLE =>
        withValidation(_.asNumber.map(_.toDouble.toString))
      case Schema.Type.BOOLEAN =>
        withValidation(_.asBoolean.map(_.toString))
      case Schema.Type.NULL =>
        withValidation(_ => Some("null"))
    }
  }

  private def withValidation[T](toExpression: Json => Option[Expression]): ValidatedNel[AvroDefaultToSpELExpressionError, Option[Expression]] = {
    toExpression(defaultValue) match {
      case expression@Some(_) => Valid(expression).toValidatedNel
      case None if defaultValue.isNull && schema.isNullable => Valid(Some(asSpelExpression("null"))).toValidatedNel
      case None if defaultValue.isNull => Invalid(NullNotAllowed).toValidatedNel
      case None => Invalid(InvalidValue).toValidatedNel
    }
  }
}
