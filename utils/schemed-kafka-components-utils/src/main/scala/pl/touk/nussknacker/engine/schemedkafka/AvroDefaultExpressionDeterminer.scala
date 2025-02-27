package pl.touk.nussknacker.engine.schemedkafka

import cats.data.Validated.{Invalid, Valid}
import cats.data.ValidatedNel
import org.apache.avro.{JsonProperties, LogicalTypes, Schema}
import pl.touk.nussknacker.engine.graph.expression.Expression

import java.time.Instant
import java.util.UUID
import scala.annotation.tailrec
import scala.language.implicitConversions
import scala.reflect.ClassTag

object AvroDefaultExpressionDeterminer {

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
class AvroDefaultExpressionDeterminer(handleNotSupported: Boolean) {

  import scala.jdk.CollectionConverters._

  import AvroDefaultExpressionDeterminer._

  private val validatedNullExpression: ValidatedNel[AvroDefaultToSpELExpressionError, Option[Expression]] =
    Valid(Some(asSpelExpression("null")))

  private def typeNotSupported(
      implicit fieldSchema: Schema.Field
  ): ValidatedNel[AvroDefaultToSpELExpressionError, Option[Expression]] =
    if (handleNotSupported)
      Valid(None)
    else
      Invalid(TypeNotSupported(fieldSchema.schema())).toValidatedNel

  def determine(fieldSchema: Schema.Field): ValidatedNel[AvroDefaultToSpELExpressionError, Option[Expression]] =
    if (fieldSchema.hasDefaultValue)
      determine(fieldSchema.schema(), fieldSchema.defaultVal())(fieldSchema)
    else
      Valid(None)

  /**
    * The expression has to correspond to type extracted by {@link pl.touk.nussknacker.engine.schemedkafka.typed.AvroSchemaTypeDefinitionExtractor}#typeDefinition
    * !when applying changes keep in mind that this Schema.Type pattern matching is duplicated in AvroSchemaTypeDefinitionExtractor
    */
  @tailrec
  private def determine(schema: Schema, defaultValue: AnyRef)(
      implicit fieldSchema: Schema.Field
  ): ValidatedNel[AvroDefaultToSpELExpressionError, Option[Expression]] = {
    schema.getType match {
      case Schema.Type.RECORD =>
        typeNotSupported
      case Schema.Type.ENUM =>
        withValidation[String](str => s"'$str'")
      case Schema.Type.ARRAY =>
        typeNotSupported
      case Schema.Type.MAP =>
        typeNotSupported
      case Schema.Type.UNION =>
        // For unions Avro supports default to be the type of the first type in the union. See: https://issues.apache.org/jira/browse/AVRO-1118
        schema.getTypes.asScala.toList.headOption match {
          case Some(firstUnionSchema) => determine(firstUnionSchema, defaultValue)
          case None                   => Invalid(InvalidValue).toValidatedNel
        }
      case Schema.Type.STRING if schema.getLogicalType == LogicalTypes.uuid() =>
        withValidation[String](uuid => s"T(${classOf[UUID].getName}).fromString('$uuid')")
      case Schema.Type.BYTES | Schema.Type.FIXED
          if schema.getLogicalType != null && schema.getLogicalType.isInstanceOf[LogicalTypes.Decimal] =>
        typeNotSupported
      case Schema.Type.STRING =>
        withValidation[String](str => s"'$str'")
      case Schema.Type.BYTES =>
        typeNotSupported
      case Schema.Type.FIXED =>
        typeNotSupported
      case Schema.Type.INT if schema.getLogicalType == LogicalTypes.date() =>
        typeNotSupported
      case Schema.Type.INT if schema.getLogicalType == LogicalTypes.timeMillis() =>
        typeNotSupported
      case Schema.Type.INT =>
        withValidation[Integer](_.toString)
      case Schema.Type.LONG
          if schema.getLogicalType == LogicalTypes.timestampMillis() || schema.getLogicalType == LogicalTypes
            .timestampMicros() =>
        withValidation[java.lang.Long](l => s"T(${classOf[Instant].getName}).ofEpochMilli(${l}L)")
      case Schema.Type.LONG if schema.getLogicalType == LogicalTypes.timeMicros() =>
        typeNotSupported
      case Schema.Type.LONG =>
        withValidation[java.lang.Long](l => s"${l}L")
      case Schema.Type.FLOAT =>
        withValidation[java.lang.Float](_.toString)
      case Schema.Type.DOUBLE =>
        withValidation[java.lang.Double](_.toString)
      case Schema.Type.BOOLEAN =>
        withValidation[java.lang.Boolean](_.toString)
      case Schema.Type.NULL =>
        validatedNullExpression
    }
  }

  private def withValidation[T <: AnyRef: ClassTag](
      toExpression: T => Expression
  )(implicit fieldSchema: Schema.Field): ValidatedNel[AvroDefaultToSpELExpressionError, Option[Expression]] =
    Option(fieldSchema.defaultVal()) match {
      case Some(JsonProperties.NULL_VALUE) => validatedNullExpression
      case Some(value: T)                  => Valid(Some(toExpression(value)))
      case Some(_)                         => Invalid(InvalidValue).toValidatedNel
      case None                            => Invalid(NullNotAllowed).toValidatedNel
    }

  private implicit def asSpelExpression(expression: String): Expression = Expression.spel(expression)

}
