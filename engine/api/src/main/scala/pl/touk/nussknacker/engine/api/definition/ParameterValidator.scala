package pl.touk.nussknacker.engine.api.definition

import java.util.regex.Pattern

import cats.data.Validated
import cats.data.Validated.{invalid, valid}
import io.circe.generic.extras.ConfiguredJsonCodec
import org.apache.commons.lang3.StringUtils
import pl.touk.nussknacker.engine.api.context.PartSubGraphCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError._
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}

import scala.util.Try
import pl.touk.nussknacker.engine.api.CirceUtil._

/**
  * Extend this trait to configure new parameter validator which should be handled on FE.
  * Please remember that you have to also add your own `pl.touk.nussknacker.engine.definition.validator.ValidatorExtractor`
  * to `pl.touk.nussknacker.engine.definition.validator.ValidatorsExtractor` which should decide whether new validator
  * should appear in configuration for certain parameter
  *
  * TODO: It shouldn't be a sealed trait. We should allow everyone to create own ParameterValidator
  */
@ConfiguredJsonCodec sealed trait ParameterValidator {

  val priority: Option[Long] = Some(0)
  val skipOnBlankIfRequired: Option[Boolean]

  def isValid(paramName: String, value: String, label: Option[String])(implicit nodeId: NodeId): Validated[PartSubGraphCompilationError, Unit]
}

//TODO: These validators should be moved to separated module

object MandatoryParameterValidator {
  def apply(): ParameterValidator = {
    MandatoryParameterValidator(skipOnBlankIfRequired = Some(false), Some(Long.MaxValue))
  }
}

case class MandatoryParameterValidator(override val skipOnBlankIfRequired: Option[Boolean], override val priority: Option[Long]) extends ParameterValidator {
  override def isValid(paramName: String, expression: String, label: Option[String])(implicit nodeId: NodeId): Validated[PartSubGraphCompilationError, Unit] =
    if (StringUtils.isNotBlank(expression)) valid(Unit) else invalid(error(paramName, nodeId.id))

  private def error(paramName: String, nodeId: String): EmptyMandatoryParameter = EmptyMandatoryParameter(
    "This field is mandatory and can not be empty",
    "Please fill field for this parameter",
    paramName,
    nodeId
  )
}

object NotBlankParameterValidator {
  def apply(): ParameterValidator = {
    NotBlankParameterValidator(skipOnBlankIfRequired = Some(false), Some(Long.MaxValue - 1))
  }
}

case class NotBlankParameterValidator(override val skipOnBlankIfRequired: Option[Boolean], override val priority: Option[Long]) extends ParameterValidator {

  private final lazy val blankStringLiteralPattern: Pattern = Pattern.compile("'\\s*'")

  // TODO: for now we correctly detect only literal expression with blank string - on this level (not evaluated expression) it is the only thing that we can do
  override def isValid(paramName: String, expression: String, label: Option[String])(implicit nodeId: NodeId): Validated[PartSubGraphCompilationError, Unit] =
    if (isBlankStringLiteral(expression)) invalid(error(paramName, nodeId.id)) else valid(Unit)

  private def error(paramName: String, nodeId: String): BlankParameter = BlankParameter(
    "This field value is required and can not be blank",
    "Please fill field value for this parameter",
    paramName,
    nodeId
  )

  private def isBlankStringLiteral(expression: String): Boolean =
    blankStringLiteralPattern.matcher(expression.trim).matches()
}

object FixedValuesValidator {
  def apply(possibleValues: List[FixedExpressionValue]): ParameterValidator = {
    FixedValuesValidator(skipOnBlankIfRequired = Some(true), Some(0), possibleValues)
  }
}

case class FixedValuesValidator(override val skipOnBlankIfRequired: Option[Boolean], override val priority: Option[Long], possibleValues: List[FixedExpressionValue]) extends ParameterValidator {

  override def isValid(paramName: String, value: String, label: Option[String])(implicit nodeId: NodeId): Validated[PartSubGraphCompilationError, Unit] = {
    val values = possibleValues.map(possibleValue => possibleValue.expression)

    if (values.contains(value))
      valid(Unit)
    else
      invalid(InvalidPropertyFixedValue(paramName, label, value, possibleValues.map(_.expression)))
  }
}

object LiteralIntegerValidator {
  def apply(): ParameterValidator = {
    LiteralIntegerValidator(skipOnBlankIfRequired = Some(true), Some(1))
  }
}

case class LiteralIntegerValidator(override val skipOnBlankIfRequired: Option[Boolean], override val priority: Option[Long]) extends ParameterValidator {

  override def isValid(paramName: String, value: String, label: Option[String])(implicit nodeId: NodeId): Validated[PartSubGraphCompilationError, Unit] =
    if (Try(value.toInt).isSuccess) valid(Unit) else invalid(error(paramName, nodeId.id))

  private def error(paramName: String, nodeId: String): InvalidIntegerLiteralParameter = InvalidIntegerLiteralParameter(
    "This field value has to be an integer number",
    "Please fill field by proper integer type",
    paramName,
    nodeId
  )
}

object RegExpParameterValidator {
  def apply(pattern: String, message: String, description: String): ParameterValidator = {
    RegExpParameterValidator(skipOnBlankIfRequired = Some(true), Some(1), pattern, message, description)
  }
}

case class RegExpParameterValidator(override val skipOnBlankIfRequired: Option[Boolean], override val priority: Option[Long], pattern: String, message: String, description: String) extends ParameterValidator {

  lazy val regexpPattern: Pattern = Pattern.compile(pattern)

  override def isValid(paramName: String, value: String, label: Option[String])(implicit nodeId: NodeId): Validated[PartSubGraphCompilationError, Unit] = {
    if (regexpPattern.matcher(value).matches())
      valid(Unit)
    else
      invalid(MismatchParameter(message, description, paramName, nodeId.id))
  }
}

object MinimalNumberValidator {
  def apply(minimalNumber: BigDecimal): ParameterValidator = {
    MinimalNumberValidator(skipOnBlankIfRequired = Some(true), Some(0), minimalNumber)
  }
}

case class MinimalNumberValidator(override val skipOnBlankIfRequired: Option[Boolean], override val priority: Option[Long], minimalNumber: BigDecimal) extends ParameterValidator {

  override def isValid(paramName: String, value: String, label: Option[String])(implicit nodeId: NodeId): Validated[PartSubGraphCompilationError, Unit] =
    if (Try(BigDecimal(value)).filter(_ >= minimalNumber).isSuccess)
      valid(Unit)
    else
      invalid(error(paramName, nodeId.id))

  private def error(paramName: String, nodeId: String): LowerThanRequiredParameter = LowerThanRequiredParameter(
    s"This field value has to be a number greater than or equal to ${minimalNumber}",
    "Please fill field with proper number",
    paramName,
    nodeId
  )
}

object MaximalNumberValidator {
  def apply(maximalNumber: BigDecimal): MaximalNumberValidator = {
    MaximalNumberValidator(skipOnBlankIfRequired = Some(true), Some(0), maximalNumber)
  }
}

case class MaximalNumberValidator(override val skipOnBlankIfRequired: Option[Boolean], override val priority: Option[Long], maximalNumber: BigDecimal) extends ParameterValidator {

  override def isValid(paramName: String, value: String, label: Option[String])(implicit nodeId: NodeId): Validated[PartSubGraphCompilationError, Unit] =
    if (Try(BigDecimal(value)).filter(_ <= maximalNumber).isSuccess)
      valid(Unit)
    else
      invalid(error(paramName, nodeId.id))

  private def error(paramName: String, nodeId: String): GreaterThanRequiredParameter = GreaterThanRequiredParameter(
    s"This field value has to be a number lower than or equal to ${maximalNumber}",
    "Please fill field with proper number",
    paramName,
    nodeId
  )
}

case object LiteralParameterValidator {

  lazy val integerValidator: ParameterValidator = LiteralIntegerValidator()

  lazy val numberValidator: ParameterValidator = RegExpParameterValidator(
    "^-?\\d+\\.?\\d*$",
    "This field value has to be an number",
    "Please fill field by proper number type"
  )

  def apply(typ: TypingResult): Option[ParameterValidator] =
    typ match {
      case clazz if typ.canBeSubclassOf(Typed[Int]) => Some(integerValidator)
      case _ => None
    }

}
