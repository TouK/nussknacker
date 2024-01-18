package pl.touk.nussknacker.engine.api.definition

import cats.data.Validated
import cats.data.Validated.{invalid, valid}
import io.circe.generic.extras.ConfiguredJsonCodec
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.parser._
import io.circe.{Decoder, Encoder}
import pl.touk.nussknacker.engine.api.CirceUtil._
import pl.touk.nussknacker.engine.api.context.PartSubGraphCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError._
import pl.touk.nussknacker.engine.api.definition.ValidationExpressionParameterValidator.variableName
import pl.touk.nussknacker.engine.api.expression.{Expression => CompiledExpression}
import pl.touk.nussknacker.engine.api.parameter.ParameterValueCompileTimeValidation
import pl.touk.nussknacker.engine.api.{Context, NodeId}
import pl.touk.nussknacker.engine.graph.expression.Expression

import java.util.ServiceLoader
import java.util.regex.Pattern
import scala.collection.concurrent.TrieMap
import scala.util.Try

trait Validator {

  def isValid(paramName: String, expression: Expression, value: Option[Any], label: Option[String])(
      implicit nodeId: NodeId
  ): Validated[PartSubGraphCompilationError, Unit]

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

  override def isValid(paramName: String, expression: Expression, value: Option[Any], label: Option[String])(
      implicit nodeId: NodeId
  ): Validated[PartSubGraphCompilationError, Unit] =
    if (!expression.expression.isBlank) valid(()) else invalid(error(paramName, nodeId.id))

  private def error(paramName: String, nodeId: String): EmptyMandatoryParameter = EmptyMandatoryParameter(
    "This field is mandatory and can not be empty",
    "Please fill field for this parameter",
    paramName,
    nodeId
  )

}

case object NotNullParameterValidator extends ParameterValidator {

  override def isValid(paramName: String, expression: Expression, value: Option[Any], label: Option[String])(
      implicit nodeId: NodeId
  ): Validated[PartSubGraphCompilationError, Unit] = {
    value match {
      case Some(null) => invalid(error(paramName, nodeId.id))
      case _          => valid(())
    }
  }

  private def error(paramName: String, nodeId: String): EmptyMandatoryParameter = EmptyMandatoryParameter(
    "This field is required and can not be null",
    "Please fill field for this parameter",
    paramName,
    nodeId
  )

}

case object CompileTimeEvaluableValueValidator extends ParameterValidator {

  override def isValid(paramName: String, expression: Expression, value: Option[Any], label: Option[String])(
      implicit nodeId: NodeId
  ): Validated[PartSubGraphCompilationError, Unit] = {
    value match {
      case None => invalid(error(paramName, nodeId.id))
      case _    => valid(())
    }
  }

  private def error(paramName: String, nodeId: String): EmptyMandatoryParameter = EmptyMandatoryParameter(
    "This field is required and value has to be evaluable at compile time",
    "Please fill field for this parameter",
    paramName,
    nodeId
  )

}

case object NotBlankParameterValidator extends ParameterValidator {

  override def isValid(paramName: String, expression: Expression, value: Option[Any], label: Option[String])(
      implicit nodeId: NodeId
  ): Validated[PartSubGraphCompilationError, Unit] =
    value match {
      case None                         => valid(())
      case Some(null)                   => valid(())
      case Some(s: String) if s.isBlank => invalid(error(paramName, nodeId.id))
      case _                            => valid(())
    }

  private def error(paramName: String, nodeId: String): BlankParameter = BlankParameter(
    "This field value is required and can not be blank",
    "Please fill field value for this parameter",
    paramName,
    nodeId
  )

}

case class FixedValuesValidator(possibleValues: List[FixedExpressionValue]) extends ParameterValidator {

  override def isValid(paramName: String, expression: Expression, value: Option[Any], label: Option[String])(
      implicit nodeId: NodeId
  ): Validated[PartSubGraphCompilationError, Unit] = {
    // FIXME: we should properly evaluate `possibleValues`
    val values = possibleValues.map(possibleValue => possibleValue.expression)

    // empty expression should not be validated - we want to chain validators
    expression.expression match {
      case e if e.isBlank          => valid(())
      case e if values.contains(e) => valid(())
      case e => invalid(InvalidPropertyFixedValue(paramName, label, e, possibleValues.map(_.expression)))
    }
  }

}

case class RegExpParameterValidator(pattern: String, message: String, description: String) extends ParameterValidator {

  lazy val regexpPattern: Pattern = Pattern.compile(pattern)

  // null value should not be validated - we want to chain validators
  override def isValid(paramName: String, expression: Expression, value: Option[Any], label: Option[String])(
      implicit nodeId: NodeId
  ): Validated[PartSubGraphCompilationError, Unit] = {
    value match {
      case None                                                  => valid(())
      case Some(null)                                            => valid(())
      case Some(s: String) if regexpPattern.matcher(s).matches() => valid(())
      case _ => invalid(MismatchParameter(message, description, paramName, nodeId.id))
    }
  }

}

// TODO: we need this validator because scenario properties do not have typing result, so we enforce proper type
//   here in validator by parsing raw expression to int
case object LiteralIntegerValidator extends ParameterValidator {

  // empty expression should not be validated - we want to chain validators
  override def isValid(paramName: String, expression: Expression, value: Option[Any], label: Option[String])(
      implicit nodeId: NodeId
  ): Validated[PartSubGraphCompilationError, Unit] =
    expression.expression match {
      case e if e.isBlank              => valid(())
      case e if Try(e.toInt).isSuccess => valid(())
      case _                           => invalid(error(paramName, nodeId.id))
    }

  private def error(paramName: String, nodeId: String): InvalidIntegerLiteralParameter = InvalidIntegerLiteralParameter(
    "This field value has to be an integer number",
    "Please fill field by proper integer type",
    paramName,
    nodeId
  )

}

case class MinimalNumberValidator(minimalNumber: BigDecimal) extends ParameterValidator {

  // null value should not be validated - we want to chain validators
  override def isValid(paramName: String, expression: Expression, value: Option[Any], label: Option[String])(
      implicit nodeId: NodeId
  ): Validated[PartSubGraphCompilationError, Unit] =
    value match {
      case None                                                       => valid(())
      case Some(null)                                                 => valid(())
      case Some(n: BigDecimal) if n >= minimalNumber                  => valid(())
      case Some(n: Number) if BigDecimal(n.toString) >= minimalNumber => valid(())
      case _                                                          => invalid(error(paramName, nodeId.id))
    }

  private def error(paramName: String, nodeId: String): LowerThanRequiredParameter = LowerThanRequiredParameter(
    s"This field value has to be a number greater than or equal to ${minimalNumber}",
    "Please fill field with proper number",
    paramName,
    nodeId
  )

}

case class MaximalNumberValidator(maximalNumber: BigDecimal) extends ParameterValidator {

  // null value should not be validated - we want to chain validators
  override def isValid(paramName: String, expression: Expression, value: Option[Any], label: Option[String])(
      implicit nodeId: NodeId
  ): Validated[PartSubGraphCompilationError, Unit] =
    value match {
      case None                                                       => valid(())
      case Some(null)                                                 => valid(())
      case Some(n: BigDecimal) if n <= maximalNumber                  => valid(())
      case Some(n: Number) if BigDecimal(n.toString) <= maximalNumber => valid(())
      case _                                                          => invalid(error(paramName, nodeId.id))
    }

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

  // null value should not be validated - we want to chain validators
  override def isValid(paramName: String, expression: Expression, value: Option[Any], label: Option[String])(
      implicit nodeId: NodeId
  ): Validated[PartSubGraphCompilationError, Unit] = {
    value match {
      case None       => valid(())
      case Some(null) => valid(())
      case Some(s: String) =>
        parse(s.trim) match {
          case Right(_)             => valid(())
          case Left(parsingFailure) => invalid(error(parsingFailure.message, paramName, nodeId.id))
        }
      case o =>
        invalid(
          error(s"Expected String with valid json, got object of class: ${o.getClass.getName}", paramName, nodeId.id)
        )
    }
  }

  private def error(message: String, paramName: String, nodeId: String): JsonRequiredParameter =
    JsonRequiredParameter(
      message,
      "Please fill field with valid json",
      paramName,
      nodeId
    )

}

case class ValidationExpressionParameterValidatorToCompile(
    validationExpression: Expression,
    validationFailedMessage: Option[String]
) extends ParameterValidator {

  override def isValid(paramName: String, expression: Expression, value: Option[Any], label: Option[String])(
      implicit nodeId: NodeId
  ): Validated[PartSubGraphCompilationError, Unit] = throw new IllegalStateException(
    s"$this must be converted to ValidationExpressionParameterValidator before being used"
  )

}

object ValidationExpressionParameterValidatorToCompile {

  def apply(
      parameterValueCompileTimeValidation: ParameterValueCompileTimeValidation
  ): ValidationExpressionParameterValidatorToCompile =
    ValidationExpressionParameterValidatorToCompile(
      parameterValueCompileTimeValidation.validationExpression,
      parameterValueCompileTimeValidation.validationFailedMessage
    )

}

case class ValidationExpressionParameterValidator(
    validationExpression: CompiledExpression,
    validationFailedMessage: Option[String]
) extends ParameterValidator {

  override def isValid(paramName: String, expression: Expression, value: Option[Any], label: Option[String])(
      implicit nodeId: NodeId
  ): Validated[PartSubGraphCompilationError, Unit] = {
    value match {
      case None       => valid(())
      case Some(null) => valid(())
      case Some(v)    => validateValue(paramName, v)
    }
  }

  private def validateValue(paramName: String, value: Any)(
      implicit nodeId: NodeId
  ): Validated[PartSubGraphCompilationError, Unit] = {
    // TODO: paramName should be used here, but a lot of parameters have names that are not valid variables (e.g. "Topic name")
    val context = Context("validator", Map(variableName -> value), None)
    Try(validationExpression.evaluate[Boolean](context, Map())).fold(
      e =>
        invalid(
          CustomParameterValidationError(
            s"Evaluation of validation expression '${validationExpression.original}' of language ${validationExpression.language} failed: ${e.getMessage}",
            s"Please provide value that satisfies the validation expression '${validationExpression.original}'",
            paramName,
            nodeId.id
          )
        ),
      result => if (result) valid(()) else invalid(error(paramName, nodeId.id))
    )
  }

  private def error(paramName: String, nodeId: String): CustomParameterValidationError = CustomParameterValidationError(
    validationFailedMessage.getOrElse(
      s"This field has to satisfy the validation expression '${validationExpression.original}'"
    ),
    s"Please provide value that satisfies the validation expression '${validationExpression.original}'",
    paramName,
    nodeId
  )

}

object ValidationExpressionParameterValidator {

  val variableName = "value"

  implicit val encoder: Encoder[ValidationExpressionParameterValidator] = deriveEncoder
  implicit val decoder: Decoder[ValidationExpressionParameterValidator] = deriveDecoder

  implicit val CompiledExpressionEncoder: Encoder[CompiledExpression] = {
    Encoder.forProduct2("language", "original")(e => (e.language, e.original))
  }

  implicit val CompiledExpressionDecoder: Decoder[CompiledExpression] = {
    Decoder.failedWithMessage(
      "Cannot evaluate Expression in ValidationExpressionParameterValidator as loading from config file is not supported"
    )
  }

}

trait CustomParameterValidator extends Validator {
  def name: String
}

case class CustomParameterValidatorDelegate(name: String) extends ParameterValidator {
  import CustomParameterValidatorDelegate._

  override def isValid(paramName: String, expression: Expression, value: Option[Any], label: Option[String])(
      implicit nodeId: NodeId
  ): Validated[PartSubGraphCompilationError, Unit] = getOrLoad(name).isValid(paramName, expression, value, label)

}

object CustomParameterValidatorDelegate {
  import scala.jdk.CollectionConverters._

  private val cache: TrieMap[String, CustomParameterValidator] = TrieMap[String, CustomParameterValidator]()

  private def getOrLoad(name: String): CustomParameterValidator = cache.getOrElseUpdate(name, load(name))

  private def load(name: String) = ServiceLoader
    .load(classOf[CustomParameterValidator])
    .iterator()
    .asScala
    .filter(_.name == name)
    .toList match {
    case v :: Nil => v
    case Nil      => throw new RuntimeException(s"Cannot load custom validator: $name")
    case _        => throw new RuntimeException(s"Multiple custom validators with name: $name")
  }

}
