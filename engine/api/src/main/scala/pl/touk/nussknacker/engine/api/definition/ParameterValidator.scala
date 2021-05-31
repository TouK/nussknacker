package pl.touk.nussknacker.engine.api.definition

import java.util.ServiceLoader
import java.util.regex.Pattern

import cats.data.Validated
import cats.data.Validated.{invalid, valid}
import io.circe.ParsingFailure
import io.circe.generic.extras.ConfiguredJsonCodec
import org.apache.commons.lang3.StringUtils
import pl.touk.nussknacker.engine.api.context.PartSubGraphCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError._
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import io.circe.parser._

import scala.util.Try
import pl.touk.nussknacker.engine.api.CirceUtil._

import scala.collection.concurrent.TrieMap


trait Validator {
  def isValid(paramName: String, value: String, label: Option[String])(implicit nodeId: NodeId): Validated[PartSubGraphCompilationError, Unit]
}

/**
  * Extend this trait to configure new parameter validator which should be handled on FE.
  * Please remember that you have to also add your own `pl.touk.nussknacker.engine.definition.validator.ValidatorExtractor`
  * to `pl.touk.nussknacker.engine.definition.validator.ValidatorsExtractor` which should decide whether new validator
  * should appear in configuration for certain parameter
  *
  * TODO: It shouldn't be a sealed trait. We should allow everyone to create own ParameterValidator
  */
@ConfiguredJsonCodec sealed trait ParameterValidator extends Validator

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

case class MinimalNumberValidator(minimalNumber: BigDecimal) extends ParameterValidator {

  import NumberValidatorHelper._

  //Blank value should be not validate - we want to chain validators
  override def isValid(paramName: String, value: String, label: Option[String])(implicit nodeId: NodeId): Validated[PartSubGraphCompilationError, Unit] =
    if (StringUtils.isBlank(value) || Try(BigDecimal(normalizeStringToNumber(value))).filter(_ >= minimalNumber).isSuccess)
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

  import NumberValidatorHelper._

  //Blank value should be not validate - we want to chain validators
  override def isValid(paramName: String, value: String, label: Option[String])(implicit nodeId: NodeId): Validated[PartSubGraphCompilationError, Unit] =
    if (StringUtils.isBlank(value) || Try(BigDecimal(normalizeStringToNumber(value))).filter(_ <= maximalNumber).isSuccess)
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

case object JsonValidator extends ParameterValidator {

  //Blank value should be not validate - we want to chain validators
  override def isValid(paramName: String, value: String, label: Option[String])(implicit nodeId: NodeId): Validated[PartSubGraphCompilationError, Unit] = {
    val strippedValue = value.stripPrefix("'").stripSuffix("'").trim
    val parsingResult = parse(strippedValue)

    if (StringUtils.isBlank(value) || parsingResult.isRight)
      valid(Unit)
    else
      invalid(error(parsingResult.left.get, paramName, nodeId.id))
  }

  private def error(parsingException: ParsingFailure, paramName: String, nodeId: String): JsonRequiredParameter = JsonRequiredParameter(
    parsingException.message,
    "Please fill field with valid json",
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

trait CustomParameterValidator extends Validator {
  def name: String
}

case class CustomParameterValidatorDelegate(name: String) extends ParameterValidator {
  import CustomParameterValidatorDelegate._

  override def isValid(paramName: String, value: String, label: Option[String])(implicit nodeId: NodeId)
  : Validated[PartSubGraphCompilationError, Unit] = getOrLoad(name).isValid(paramName, value, label)
}

object CustomParameterValidatorDelegate {
  import scala.collection.JavaConverters._

  private val cache: TrieMap[String, CustomParameterValidator] = TrieMap[String, CustomParameterValidator]()

  private def getOrLoad(name: String): CustomParameterValidator = cache.getOrElseUpdate(name, load(name))

  private def load(name: String) = ServiceLoader.load(classOf[CustomParameterValidator])
    .iterator().asScala.filter(_.name == name).toList match {
    case v :: Nil => v
    case Nil => throw new RuntimeException(s"Cannot load custom validator: $name")
    case _ => throw new RuntimeException(s"Multiple custom validators with name: $name")
  }
}


object NumberValidatorHelper {

  private val numberRegexp = "[^\\d.]".r

  //It's kind of hack.. Because from SPeL we get string with "L" or others number's mark.
  //We can't properly cast that kind of string to number, so we have to remove all not digits chars.
  def normalizeStringToNumber(value: String): String =
    numberRegexp.replaceAllIn(value, "")
}