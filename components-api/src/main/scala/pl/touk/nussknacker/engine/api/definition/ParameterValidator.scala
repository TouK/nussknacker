package pl.touk.nussknacker.engine.api.definition

import java.util.ServiceLoader
import java.util.regex.Pattern
import cats.data.Validated
import cats.data.Validated.{catchOnly, invalid, valid}
import io.circe.ParsingFailure
import io.circe.generic.extras.ConfiguredJsonCodec
import org.apache.commons.lang3.StringUtils
import pl.touk.nussknacker.engine.api.context.PartSubGraphCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError._
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import io.circe.parser._
import org.springframework.expression.spel.standard.SpelExpressionParser

import scala.util.Try
import pl.touk.nussknacker.engine.api.CirceUtil._
import pl.touk.nussknacker.engine.api.NodeId
import org.springframework.expression.ExpressionParser
import org.springframework.expression.spel.support.StandardEvaluationContext

import java.time.LocalDateTime
import java.time.temporal.Temporal
import scala.collection.concurrent.TrieMap


trait Validator {
  def isValid(paramName: String, value: String, paramType: Option[TypingResult], label: Option[String])(implicit nodeId: NodeId): Validated[PartSubGraphCompilationError, Unit] // instead of adding paramType here, can also add new method `isValidWithType`
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

case class CustomExpressionParameterValidator(validationExpression: String,
                                              validationFailedMessage: Option[String]
                                             ) extends ParameterValidator {

  private val parser: ExpressionParser = new SpelExpressionParser()
  private val parsedValidationExpression = parser.parseExpression(validationExpression)

  def isValidatorValid(paramName: String, typ: TypingResult): Boolean = {
    // TODO use SpelExpressionValidator, result has to be Boolean and computable during validation (no reliance on input)
    val context = new StandardEvaluationContext()

    def defaultForType(valueType: TypingResult): Any = {
      if (valueType.canBeSubclassOf(Typed[Number])) 0 else ""
    }

    context.setVariable(paramName, defaultForType(typ))
    try {
      parsedValidationExpression.getValue(context, classOf[Boolean])
      true
    } catch {
      case e: Throwable =>
        println(s"Invalid validation expression: ${e.getMessage}")
        false
    }
  }

  override def isValid(paramName: String, value: String, paramType: Option[TypingResult], label: Option[String])(implicit nodeId: NodeId): Validated[PartSubGraphCompilationError, Unit] =
    if (evaluateToBoolean(paramName, paramType, value)) valid(()) else invalid(error(paramName, nodeId.id))

  private def evaluateToBoolean(paramName: String, paramType: Option[TypingResult], value: String): Boolean = { // paramName has to be the same as in validationExpression (and same as the field name)
    val context: StandardEvaluationContext = new StandardEvaluationContext()  // TODO should be context with access to function that the user can input, for example #DATE.now


    // TODO use some already present classes from `interpreter` module ?

    paramType match { // TODO expand/fix, this is just an example
      case Some(t) if t.canBeSubclassOf(Typed[Number])   => context.setVariable(paramName, value.toDouble/*parseToNumber(value)*/)
      case Some(t) if t.canBeSubclassOf(Typed[Temporal]) => context.setVariable(paramName, LocalDateTime.now()/*parseToTime(value)*/)
      case Some(t) if t.canBeSubclassOf(Typed[String])   => context.setVariable(paramName, value.drop(1).dropRight(1))
      case _ => context.setVariable(paramName, value)
    }

    try {
      parsedValidationExpression.getValue(context, classOf[Boolean])
    } catch {
//      case _: EvaluationException => false // TODO better handling? it will throw if paramName != fieldName
      case e: Throwable => throw new IllegalArgumentException(s"Expression evaluation failed: ${e.getMessage}")
    }
  }

  private def error(paramName: String, nodeId: String): CustomParameterValidationError = CustomParameterValidationError(
    validationFailedMessage.getOrElse("This field has to satisfy the validation expression"),
    s"Please correct the provided value to satisfy the expression \"$validationExpression\"",
    paramName,
    nodeId
  )
}

case object MandatoryParameterValidator extends ParameterValidator {

  override def isValid(paramName: String, expression: String, paramType: Option[TypingResult], label: Option[String])(implicit nodeId: NodeId): Validated[PartSubGraphCompilationError, Unit] =
    if (StringUtils.isNotBlank(expression)) valid(()) else invalid(error(paramName, nodeId.id))

  private def error(paramName: String, nodeId: String): EmptyMandatoryParameter = EmptyMandatoryParameter(
    "This field is mandatory and can not be empty",
    "Please fill field for this parameter",
    paramName,
    nodeId
  )
}

case object NotBlankParameterValidator extends ParameterValidator {

  private final lazy val blankStringLiteralPattern: Pattern = Pattern.compile("['\"]\\s*['\"]")

  // TODO: for now we correctly detect only literal expression with blank string - on this level (not evaluated expression) it is the only thing that we can do
  override def isValid(paramName: String, expression: String, paramType: Option[TypingResult], label: Option[String])(implicit nodeId: NodeId): Validated[PartSubGraphCompilationError, Unit] =
    if (isBlankStringLiteral(expression)) invalid(error(paramName, nodeId.id)) else valid(())

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
  override def isValid(paramName: String, value: String, paramType: Option[TypingResult], label: Option[String])(implicit nodeId: NodeId): Validated[PartSubGraphCompilationError, Unit] = {
    val values = possibleValues.map(possibleValue => possibleValue.expression)

    if (StringUtils.isBlank(value) || values.contains(value))
      valid(())
    else
      invalid(InvalidPropertyFixedValue(paramName, label, value, possibleValues.map(_.expression)))
  }
}

case class RegExpParameterValidator(pattern: String, message: String, description: String) extends ParameterValidator {

  lazy val regexpPattern: Pattern = Pattern.compile(pattern)

  //Blank value should be not validate - we want to chain validators
  override def isValid(paramName: String, value: String, paramType: Option[TypingResult], label: Option[String])(implicit nodeId: NodeId): Validated[PartSubGraphCompilationError, Unit] = {
    if (StringUtils.isBlank(value) || regexpPattern.matcher(value).matches())
      valid(())
    else
      invalid(MismatchParameter(message, description, paramName, nodeId.id))
  }
}

case object LiteralIntegerValidator extends ParameterValidator {
  //Blank value should be not validate - we want to chain validators
  override def isValid(paramName: String, value: String, paramType: Option[TypingResult], label: Option[String])(implicit nodeId: NodeId): Validated[PartSubGraphCompilationError, Unit] =
    if (StringUtils.isBlank(value) || Try(value.toInt).isSuccess) valid(()) else invalid(error(paramName, nodeId.id))

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
  override def isValid(paramName: String, value: String, paramType: Option[TypingResult], label: Option[String])(implicit nodeId: NodeId): Validated[PartSubGraphCompilationError, Unit] =
    if (StringUtils.isBlank(value) || Try(BigDecimal(normalizeStringToNumber(value))).filter(_ >= minimalNumber).isSuccess)
      valid(())
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
  override def isValid(paramName: String, value: String, paramType: Option[TypingResult], label: Option[String])(implicit nodeId: NodeId): Validated[PartSubGraphCompilationError, Unit] =
    if (StringUtils.isBlank(value) || Try(BigDecimal(normalizeStringToNumber(value))).filter(_ <= maximalNumber).isSuccess)
      valid(())
    else
      invalid(error(paramName, nodeId.id))

  private def error(paramName: String, nodeId: String): GreaterThanRequiredParameter = GreaterThanRequiredParameter(
    s"This field value has to be a number lower than or equal to ${maximalNumber}",
    "Please fill field with proper number",
    paramName,
    nodeId
  )
}

// This validator is not determined by default in components based on usage of JsonParameterEditor because someone may want to use only
// editor for syntax highlight but don't want to use validator e.g. when want user to provide SpEL literal map
case object JsonValidator extends ParameterValidator {

  //Blank value should be not validate - we want to chain validators
  override def isValid(paramName: String, value: String, paramType: Option[TypingResult], label: Option[String])(implicit nodeId: NodeId): Validated[PartSubGraphCompilationError, Unit] = {
    val strippedValue = value.stripPrefix("'").stripSuffix("'").trim
    val parsingResult = parse(strippedValue)

    if (StringUtils.isBlank(value) || parsingResult.isRight)
      valid(())
    else
      invalid(error(parsingResult.swap.getOrElse(throw new IllegalStateException()), paramName, nodeId.id))
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

  override def isValid(paramName: String, value: String, paramType: Option[TypingResult], label: Option[String])(implicit nodeId: NodeId)
  : Validated[PartSubGraphCompilationError, Unit] = getOrLoad(name).isValid(paramName, value, paramType, label)
}

object CustomParameterValidatorDelegate {
  import scala.jdk.CollectionConverters._

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

  private val numberRegexp = "[^-?\\d.]".r

  //It's kind of hack.. Because from SPeL we get string with "L" or others number's mark.
  //We can't properly cast that kind of string to number, so we have to remove all not digits chars.
  def normalizeStringToNumber(value: String): String =
    numberRegexp.replaceAllIn(value, "")
}