package pl.touk.nussknacker.engine.api.definition

import cats.data.Validated
import cats.data.Validated.{invalid, valid}
import io.circe.generic.extras.ConfiguredJsonCodec
import io.circe.parser._
import pl.touk.nussknacker.engine.api.CirceUtil._
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.PartSubGraphCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError._
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.graph.expression.Expression

import java.util.ServiceLoader
import java.util.regex.Pattern
import scala.collection.concurrent.TrieMap
import scala.util.Try

trait Validator {

  def isValid(paramName: ParameterName, expression: Expression, value: Option[Any], label: Option[String])(
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
  * TODO: This being sealed also makes the tests of cases that use these validators dependant on the code here -
  * not good/unseal!!
  */
@ConfiguredJsonCodec sealed trait ParameterValidator extends Validator

//TODO: These validators should be moved to separated module

case object MandatoryParameterValidator extends ParameterValidator {

  override def isValid(paramName: ParameterName, expression: Expression, value: Option[Any], label: Option[String])(
      implicit nodeId: NodeId
  ): Validated[PartSubGraphCompilationError, Unit] =
    if (!expression.expression.isBlank) valid(()) else invalid(error(paramName, nodeId.id))

  private def error(paramName: ParameterName, nodeId: String): EmptyMandatoryParameter = EmptyMandatoryParameter(
    message = "This field is mandatory and can not be empty",
    description = "Please fill field for this parameter",
    paramName = paramName,
    nodeId = nodeId
  )

}

case object NotNullParameterValidator extends ParameterValidator {

  override def isValid(paramName: ParameterName, expression: Expression, value: Option[Any], label: Option[String])(
      implicit nodeId: NodeId
  ): Validated[PartSubGraphCompilationError, Unit] = {
    value match {
      case Some(null) => invalid(error(paramName, nodeId.id))
      case _          => valid(())
    }
  }

  private def error(paramName: ParameterName, nodeId: String): EmptyMandatoryParameter = EmptyMandatoryParameter(
    message = "This field is required and can not be null",
    description = "Please fill field for this parameter",
    paramName = paramName,
    nodeId = nodeId
  )

}

case object CompileTimeEvaluableValueValidator extends ParameterValidator {

  override def isValid(paramName: ParameterName, expression: Expression, value: Option[Any], label: Option[String])(
      implicit nodeId: NodeId
  ): Validated[PartSubGraphCompilationError, Unit] = {
    value match {
      case None => invalid(error(paramName, nodeId.id))
      case _    => valid(())
    }
  }

  private def error(paramName: ParameterName, nodeId: String): CompileTimeEvaluableParameterNotEvaluated =
    CompileTimeEvaluableParameterNotEvaluated(
      message = "This field's value has to be evaluable at deployment time",
      description = "Please provide a value that is evaluable at deployment time",
      paramName = paramName,
      nodeId = nodeId
    )

}

case object NotBlankParameterValidator extends ParameterValidator {

  override def isValid(paramName: ParameterName, expression: Expression, value: Option[Any], label: Option[String])(
      implicit nodeId: NodeId
  ): Validated[PartSubGraphCompilationError, Unit] =
    value match {
      case None                         => valid(())
      case Some(null)                   => valid(())
      case Some(s: String) if s.isBlank => invalid(error(paramName, nodeId.id))
      case _                            => valid(())
    }

  private def error(paramName: ParameterName, nodeId: String): BlankParameter = BlankParameter(
    "This field value is required and can not be blank",
    "Please fill field value for this parameter",
    paramName,
    nodeId
  )

}

case class FixedValuesValidator(possibleValues: List[FixedExpressionValue]) extends ParameterValidator {

  override def isValid(paramName: ParameterName, expression: Expression, value: Option[Any], label: Option[String])(
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
  override def isValid(paramName: ParameterName, expression: Expression, value: Option[Any], label: Option[String])(
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
  override def isValid(paramName: ParameterName, expression: Expression, value: Option[Any], label: Option[String])(
      implicit nodeId: NodeId
  ): Validated[PartSubGraphCompilationError, Unit] =
    expression.expression match {
      case e if e.isBlank              => valid(())
      case e if Try(e.toInt).isSuccess => valid(())
      case _                           => invalid(error(paramName, nodeId.id))
    }

  private def error(paramName: ParameterName, nodeId: String): InvalidIntegerLiteralParameter =
    InvalidIntegerLiteralParameter(
      "This field value has to be an integer number",
      "Please fill field by proper integer type",
      paramName,
      nodeId
    )

}

case class MinimalNumberValidator(minimalNumber: BigDecimal) extends ParameterValidator {

  // null value should not be validated - we want to chain validators
  override def isValid(paramName: ParameterName, expression: Expression, value: Option[Any], label: Option[String])(
      implicit nodeId: NodeId
  ): Validated[PartSubGraphCompilationError, Unit] =
    value match {
      case None                                                       => valid(())
      case Some(null)                                                 => valid(())
      case Some(n: BigDecimal) if n >= minimalNumber                  => valid(())
      case Some(n: Number) if BigDecimal(n.toString) >= minimalNumber => valid(())
      case _                                                          => invalid(error(paramName, nodeId.id))
    }

  private def error(paramName: ParameterName, nodeId: String): LowerThanRequiredParameter = LowerThanRequiredParameter(
    s"This field value has to be a number greater than or equal to ${minimalNumber}",
    "Please fill field with proper number",
    paramName,
    nodeId
  )

}

case class MaximalNumberValidator(maximalNumber: BigDecimal) extends ParameterValidator {

  // null value should not be validated - we want to chain validators
  override def isValid(paramName: ParameterName, expression: Expression, value: Option[Any], label: Option[String])(
      implicit nodeId: NodeId
  ): Validated[PartSubGraphCompilationError, Unit] =
    value match {
      case None                                                       => valid(())
      case Some(null)                                                 => valid(())
      case Some(n: BigDecimal) if n <= maximalNumber                  => valid(())
      case Some(n: Number) if BigDecimal(n.toString) <= maximalNumber => valid(())
      case _                                                          => invalid(error(paramName, nodeId.id))
    }

  private def error(paramName: ParameterName, nodeId: String): GreaterThanRequiredParameter =
    GreaterThanRequiredParameter(
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
  override def isValid(paramName: ParameterName, expression: Expression, value: Option[Any], label: Option[String])(
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

  private def error(message: String, paramName: ParameterName, nodeId: String): JsonRequiredParameter =
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

  override def isValid(paramName: ParameterName, expression: Expression, value: Option[Any], label: Option[String])(
      implicit nodeId: NodeId
  ): Validated[PartSubGraphCompilationError, Unit] = throw new IllegalStateException(
    s"$this must be converted to ValidationExpressionParameterValidator before being used"
  )

}

trait CustomParameterValidator extends Validator {
  def name: String
}

case class CustomParameterValidatorDelegate(name: String) extends ParameterValidator {
  import CustomParameterValidatorDelegate._

  override def isValid(paramName: ParameterName, expression: Expression, value: Option[Any], label: Option[String])(
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
