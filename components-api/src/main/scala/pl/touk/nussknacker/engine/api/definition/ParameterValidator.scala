package pl.touk.nussknacker.engine.api.definition

import java.util.ServiceLoader
import java.util.regex.Pattern
import cats.data.Validated
import cats.data.Validated.{invalid, valid}
import io.circe.generic.extras.ConfiguredJsonCodec
import pl.touk.nussknacker.engine.api.context.PartSubGraphCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError._
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import io.circe.parser._
import pl.touk.nussknacker.engine.api.CirceUtil._
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.graph.expression.Expression

import scala.collection.concurrent.TrieMap

trait Validator {

  def isValid(paramName: String, expression: Expression, value: Any, label: Option[String])(
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

  override def isValid(paramName: String, expression: Expression, value: Any, label: Option[String])(
      implicit nodeId: NodeId
  ): Validated[PartSubGraphCompilationError, Unit] =
    if (!expression.expression.isEmpty) valid(()) else invalid(error(paramName, nodeId.id))

  private def error(paramName: String, nodeId: String): EmptyMandatoryParameter = EmptyMandatoryParameter(
    "This field is mandatory and can not be empty",
    "Please fill field for this parameter",
    paramName,
    nodeId
  )

}

case object NotNullParameterValidator extends ParameterValidator {

  override def isValid(paramName: String, expression: Expression, value: Any, label: Option[String])(
      implicit nodeId: NodeId
  ): Validated[PartSubGraphCompilationError, Unit] =
    if (value != null) valid(()) else invalid(error(paramName, nodeId.id))

  private def error(paramName: String, nodeId: String): EmptyMandatoryParameter = EmptyMandatoryParameter(
    "This field is required and can not be null",
    "Please fill field for this parameter",
    paramName,
    nodeId
  )

}

case object NotBlankParameterValidator extends ParameterValidator {

  override def isValid(paramName: String, expression: Expression, value: Any, label: Option[String])(
      implicit nodeId: NodeId
  ): Validated[PartSubGraphCompilationError, Unit] =
    value match {
      case null      => valid(())
      case s: String => if (s.isBlank) invalid(error(paramName, nodeId.id)) else valid(())
      case _         => valid(())
    }

  private def error(paramName: String, nodeId: String): BlankParameter = BlankParameter(
    "This field value is required and can not be blank",
    "Please fill field value for this parameter",
    paramName,
    nodeId
  )

}

case class FixedValuesValidator(possibleValues: List[FixedExpressionValue]) extends ParameterValidator {

  override def isValid(paramName: String, expression: Expression, value: Any, label: Option[String])(
      implicit nodeId: NodeId
  ): Validated[PartSubGraphCompilationError, Unit] = {
    // FIXME: we should properly evaluate `possibleValues`
    val values = possibleValues.map(possibleValue => possibleValue.expression)

    value match {
      case null                                        => valid(())
      case s: String if values.contains(s"'$s'")       => valid(())
      case _ if values.contains(expression.expression) => valid(())
      case s: String => invalid(InvalidPropertyFixedValue(paramName, label, s"'$s'", possibleValues.map(_.expression)))
      case x if values.contains(x.toString) => valid(())
      case _ => invalid(InvalidPropertyFixedValue(paramName, label, value.toString, possibleValues.map(_.expression)))
    }
  }

}

case class LiteralRegExpParameterValidator(pattern: String, message: String, description: String)
    extends ParameterValidator {

  lazy val regexpPattern: Pattern = Pattern.compile(pattern)

  // null value should not be validated - we want to chain validators
  override def isValid(paramName: String, expression: Expression, value: Any, label: Option[String])(
      implicit nodeId: NodeId
  ): Validated[PartSubGraphCompilationError, Unit] = {
    value match {
      case null                                            => valid(())
      case s: String if regexpPattern.matcher(s).matches() => valid(())
      case _ => invalid(MismatchParameter(message, description, paramName, nodeId.id))
    }

  }

}

case object LiteralIntegerValidator extends ParameterValidator {

  // null value should not be validated - we want to chain validators
  override def isValid(paramName: String, expression: Expression, value: Any, label: Option[String])(
      implicit nodeId: NodeId
  ): Validated[PartSubGraphCompilationError, Unit] =
    value match {
      case null   => valid(())
      case _: Int => valid(())
      case _      => invalid(error(paramName, nodeId.id))
    }

  private def error(paramName: String, nodeId: String): InvalidIntegerLiteralParameter = InvalidIntegerLiteralParameter(
    "This field value has to be an integer number",
    "Please fill field by proper integer type",
    paramName,
    nodeId
  )

}

case object LiteralNumberValidator extends ParameterValidator {

  // null value should not be validated - we want to chain validators
  override def isValid(paramName: String, expression: Expression, value: Any, label: Option[String])(
      implicit nodeId: NodeId
  ): Validated[PartSubGraphCompilationError, Unit] =
    value match {
      case null      => valid(())
      case _: Number => valid(())
      case _         => invalid(error(paramName, nodeId.id))
    }

  private def error(paramName: String, nodeId: String): InvalidIntegerLiteralParameter = InvalidIntegerLiteralParameter(
    "This field value has to be a number",
    "Please fill field by proper number type",
    paramName,
    nodeId
  )

}

case class MinimalNumberValidator(minimalNumber: BigDecimal) extends ParameterValidator {

  // null value should not be validated - we want to chain validators
  override def isValid(paramName: String, expression: Expression, value: Any, label: Option[String])(
      implicit nodeId: NodeId
  ): Validated[PartSubGraphCompilationError, Unit] =
    value match {
      case null                                                 => valid(())
      case n: BigDecimal if n >= minimalNumber                  => valid(())
      case n: Number if BigDecimal(n.toString) >= minimalNumber => valid(())
      case _                                                    => invalid(error(paramName, nodeId.id))
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
  override def isValid(paramName: String, expression: Expression, value: Any, label: Option[String])(
      implicit nodeId: NodeId
  ): Validated[PartSubGraphCompilationError, Unit] =
    value match {
      case null                                                 => valid(())
      case n: BigDecimal if n <= maximalNumber                  => valid(())
      case n: Number if BigDecimal(n.toString) <= maximalNumber => valid(())
      case _                                                    => invalid(error(paramName, nodeId.id))
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
  override def isValid(paramName: String, expression: Expression, value: Any, label: Option[String])(
      implicit nodeId: NodeId
  ): Validated[PartSubGraphCompilationError, Unit] = {
    value match {
      case null => valid(())
      case s: String =>
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

case object LiteralParameterValidator {

  lazy val integerValidator: ParameterValidator = LiteralIntegerValidator

  lazy val numberValidator: ParameterValidator = LiteralNumberValidator

  def apply(typ: TypingResult): Option[ParameterValidator] =
    typ match {
      case _ if typ.canBeSubclassOf(Typed[Int])    => Some(integerValidator)
      case _ if typ.canBeSubclassOf(Typed[Number]) => Some(numberValidator)
      case _                                       => None
    }

}

trait CustomParameterValidator extends Validator {
  def name: String
}

case class CustomParameterValidatorDelegate(name: String) extends ParameterValidator {
  import CustomParameterValidatorDelegate._

  override def isValid(paramName: String, expression: Expression, value: Any, label: Option[String])(
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
