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

  val priority: Long = 0
  protected val skipIfBlank: Boolean

  final def isValid(paramName: String, value: String, label: Option[String])(implicit nodeId: NodeId): Validated[PartSubGraphCompilationError, Unit] = {
    if (skipIfBlank && StringUtils.isBlank(value)) valid(Unit)
    else isValidFunc(paramName, value, label)
  }

  protected def isValidFunc(paramName: String, value: String, label: Option[String])(implicit nodeId: NodeId): Validated[PartSubGraphCompilationError, Unit]
}

//TODO: These validators should be moved to separated module

case object MandatoryParameterValidator extends ParameterValidator {

  override val skipIfBlank: Boolean = false
  override val priority: Long = Long.MaxValue

  override def isValidFunc(paramName: String, expression: String, label: Option[String])(implicit nodeId: NodeId): Validated[PartSubGraphCompilationError, Unit] =
    if (StringUtils.isNotBlank(expression)) valid(Unit) else invalid(error(paramName, nodeId.id))

  private def error(paramName: String, nodeId: String): EmptyMandatoryParameter = EmptyMandatoryParameter(
    "This field is mandatory and can not be empty",
    "Please fill field for this parameter",
    paramName,
    nodeId
  )
}

case object NotBlankParameterValidator extends ParameterValidator {

  override val skipIfBlank: Boolean = false
  override val priority: Long = Long.MaxValue - 1

  private final lazy val blankStringLiteralPattern: Pattern = Pattern.compile("'\\s*'")

  // TODO: for now we correctly detect only literal expression with blank string - on this level (not evaluated expression) it is the only thing that we can do
  override def isValidFunc(paramName: String, expression: String, label: Option[String])(implicit nodeId: NodeId): Validated[PartSubGraphCompilationError, Unit] =
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

case class FixedValuesValidator(possibleValues: List[FixedExpressionValue]) extends ParameterValidator {

  override val skipIfBlank = true

  override def isValidFunc(paramName: String, value: String, label: Option[String])(implicit nodeId: NodeId): Validated[PartSubGraphCompilationError, Unit] = {
    val values = possibleValues.map(possibleValue => possibleValue.expression)

    if (values.contains(value))
      valid(Unit)
    else
      invalid(InvalidPropertyFixedValue(paramName, label, value, possibleValues.map(_.expression)))
  }
}

case class RegExpParameterValidator(pattern: String, message: String, description: String) extends ParameterValidator {

  //Blank value should be not validate - we want to chain validators
  override val skipIfBlank: Boolean = true
  override val priority: Long = 1

  lazy val regexpPattern: Pattern = Pattern.compile(pattern)

  override def isValidFunc(paramName: String, value: String, label: Option[String])(implicit nodeId: NodeId): Validated[PartSubGraphCompilationError, Unit] = {
    if (regexpPattern.matcher(value).matches())
      valid(Unit)
    else
      invalid(MismatchParameter(message, description, paramName, nodeId.id))
  }
}

case object LiteralIntegerValidator extends ParameterValidator {

  override val skipIfBlank: Boolean = true
  override val priority: Long = 1

  override def isValidFunc(paramName: String, value: String, label: Option[String])(implicit nodeId: NodeId): Validated[PartSubGraphCompilationError, Unit] =
    if (Try(value.toInt).isSuccess) valid(Unit) else invalid(error(paramName, nodeId.id))

  private def error(paramName: String, nodeId: String): InvalidIntegerLiteralParameter = InvalidIntegerLiteralParameter(
    "This field value has to be an integer number",
    "Please fill field by proper integer type",
    paramName,
    nodeId
  )
}

case class MinimalNumberValidator(minimalNumber: BigDecimal) extends ParameterValidator {

  override val skipIfBlank: Boolean = true

  override def isValidFunc(paramName: String, value: String, label: Option[String])(implicit nodeId: NodeId): Validated[PartSubGraphCompilationError, Unit] =
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

case class MaximalNumberValidator(maximalNumber: BigDecimal) extends ParameterValidator {

  override val skipIfBlank: Boolean = true

  override def isValidFunc(paramName: String, value: String, label: Option[String])(implicit nodeId: NodeId): Validated[PartSubGraphCompilationError, Unit] =
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

  lazy val integerValidator: ParameterValidator = LiteralIntegerValidator

  lazy val numberValidator: RegExpParameterValidator = RegExpParameterValidator(
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
