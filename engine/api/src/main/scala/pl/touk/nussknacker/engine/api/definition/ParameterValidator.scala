package pl.touk.nussknacker.engine.api.definition

import java.util.Optional
import java.util.regex.Pattern

import cats.data.Validated
import cats.data.Validated.{invalid, valid}
import io.circe.generic.extras.ConfiguredJsonCodec
import org.apache.commons.lang3.StringUtils
import pl.touk.nussknacker.engine.api.context.PartSubGraphCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError._

import scala.reflect.ClassTag
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

  def isValid(paramName: String, value: String, label: Option[String])(implicit nodeId: NodeId): Validated[PartSubGraphCompilationError, Unit]

}

//TODO: These validators should be moved to separated module

case object MandatoryParameterValidator extends ParameterValidator {

  override def isValid(paramName: String, expression: String, label: Option[String])(implicit nodeId: NodeId): Validated[PartSubGraphCompilationError, Unit] =
    if (StringUtils.isNotBlank(expression)) valid(Unit) else invalid(error(paramName, nodeId.id))

  private def error(paramName: String, nodeId: String): EmptyMandatoryParameter = EmptyMandatoryParameter(
    "This field is mandatory and can not be empty",
    "Please fill field for this parameter",
    paramName,
    nodeId
  )
}

case object NotBlankParameterValidator extends ParameterValidator {

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

case class FixedValuesValidator(possibleValues: List[FixedExpressionValue]) extends ParameterValidator {
  override def isValid(paramName: String, value: String, label: Option[String])(implicit nodeId: NodeId): Validated[PartSubGraphCompilationError, Unit] = {
    val values = possibleValues.map(possibleValue => possibleValue.expression)

    if (StringUtils.isBlank(value) || values.contains(value))
      valid(Unit)
    else
      invalid(InvalidPropertyFixedValue(paramName, label, value, possibleValues.map(_.expression)))
  }
}

case class RegExpParameterValidator(pattern: String, message: String, description: String) extends ParameterValidator {

  lazy val regexpPattern: Pattern = Pattern.compile(pattern)

  //Blank value should be not validate - we want to chain validators
  override def isValid(paramName: String, value: String, label: Option[String])(implicit nodeId: NodeId): Validated[PartSubGraphCompilationError, Unit] = {
    if (StringUtils.isBlank(value) || regexpPattern.matcher(value).matches())
      valid(Unit)
    else
      invalid(MismatchParameter(message, description, paramName, nodeId.id))
  }
}

case object LiteralIntegerValidator extends ParameterValidator {
  //Blank value should be not validate - we want to chain validators
  override def isValid(paramName: String, value: String, label: Option[String])(implicit nodeId: NodeId): Validated[PartSubGraphCompilationError, Unit] =
    if (StringUtils.isBlank(value) || Try(value.toInt).isSuccess) valid(Unit) else invalid(error(paramName, nodeId.id))

  private def error(paramName: String, nodeId: String): InvalidIntegerLiteralParameter = InvalidIntegerLiteralParameter(
    "This field value has to be an integer number",
    "Please fill field by proper integer type",
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

  /**
    * A now we support only simple literals like: Int / Integer.
    * Option / Optional / LazyParam are not allowed.
    */
  def apply(clazz: Class[_]): Option[ParameterValidator] =
    clazz match {
      case clazz if clazz == classOf[Int] => Some(integerValidator)
      case clazz if clazz == classOf[Integer] => Some(integerValidator)
      case _ => None
    }

  def apply[T: ClassTag]: Option[ParameterValidator] =
    LiteralParameterValidator(implicitly[ClassTag[T]].runtimeClass.asInstanceOf[Class[T]])
}
