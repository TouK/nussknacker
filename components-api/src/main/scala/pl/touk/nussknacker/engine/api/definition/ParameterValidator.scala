package pl.touk.nussknacker.engine.api.definition

import cats.data.Validated
import cats.data.Validated.{invalid, valid}
import io.circe._
import io.circe.parser._
import io.circe.syntax.EncoderOps
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.PartSubGraphCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError._
import pl.touk.nussknacker.engine.api.definition.CustomParameterValidatorLoader.WithUnderlyingCustomParameterValidator
import pl.touk.nussknacker.engine.api.parameter.{ParameterName, ParameterValueCompileTimeValidation}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.expression.Expression.Language

import java.util.ServiceLoader
import java.util.regex.Pattern
import scala.collection.concurrent.TrieMap
import scala.util.Try

sealed trait Validator

trait CompileTimeValidator extends Validator {

  def isValid(paramName: ParameterName, expression: Expression, value: Option[Any], label: Option[String])(
      implicit nodeId: NodeId
  ): Validated[PartSubGraphCompilationError, Unit]

}

final case class ParameterRuntimeValidationError(input: String, message: String)

trait RuntimeValidator extends Validator {

  def isValid(paramName: ParameterName, expression: Expression, value: Any)(
      implicit nodeId: NodeId
  ): Validated[ParameterRuntimeValidationError, Unit]

}

/**
  * Extend this trait to configure new parameter validator which should be handled on FE.
  * Please remember that you have to also add your own `pl.touk.nussknacker.engine.definition.validator.ValidatorExtractor`
  * to `pl.touk.nussknacker.engine.definition.validator.ValidatorsExtractor` which should decide whether new validator
  * should appear in configuration for certain parameter
  *
  * Validators can be run:
  * <ul>
  *   <li>at compile time, on expression - `*ExpressionValidator` subclasses
  *   <li>at runtime, on evaluated value - `*ValueValidator` subclasses
  *   <li>at both times - `*Validator` subclasses
  * </ul>
  *
  * TODO: It shouldn't be a sealed trait. We should allow everyone to create own ParameterValidator
  * TODO: This being sealed also makes the tests of cases that use these validators dependant on the code here -
  * not good/unseal!!
  */
sealed trait ParameterValidator extends Validator

object ParameterValidator {

  def resolveLoaders(validators: List[Validator]): List[Validator] =
    validators.map { case d: CustomParameterValidatorLoader => d.resolved; case v => v }

  private val fixedValuesCodec: Codec[FixedValuesExpressionValidator] =
    Codec.forProduct1("possibleValues")(FixedValuesExpressionValidator.apply)(_.possibleValues)

  private val regExpCodec: Codec[RegExpValidator] =
    Codec.forProduct3("pattern", "message", "description")(RegExpValidator.apply)(v =>
      (v.pattern, v.message, v.description)
    )

  private val minimalNumberCodec: Codec[MinimalNumberValidator] =
    Codec.forProduct1("minimalNumber")(MinimalNumberValidator.apply)(_.minimalNumber)

  private val maximalNumberCodec: Codec[MaximalNumberValidator] =
    Codec.forProduct1("maximalNumber")(MaximalNumberValidator.apply)(_.maximalNumber)

  private val validationExpressionCodec: Codec[ValidationExpressionParameterValidatorToCompile] =
    Codec.forProduct2("validationExpression", "validationFailedMessage")(
      ValidationExpressionParameterValidatorToCompile.apply
    )(v => (v.validationExpression, v.validationFailedMessage))

  implicit val decoder: Decoder[ParameterValidator] = Decoder.instance { cursor =>
    cursor.downField("type").as[String].flatMap {
      case "MandatoryExpressionValidator"       => Right(MandatoryExpressionValidator)
      case "NotNullValidator"                   => Right(NotNullValidator)
      case "CompileTimeEvaluableValueValidator" => Right(CompileTimeEvaluableValueValidator)
      case "NotBlankValidator"                  => Right(NotBlankValidator)
      case "LiteralIntegerExpressionValidator"  => Right(LiteralIntegerExpressionValidator)
      case "JsonExpressionValidator"            => Right(JsonExpressionValidator)
      case "NotEmptyCollectionValidator"        => Right(NotEmptyCollectionValidator)
      case "FixedValuesExpressionValidator"     => fixedValuesCodec(cursor)
      case "RegExpValidator"                    => regExpCodec(cursor)
      case "MinimalNumberValidator"             => minimalNumberCodec(cursor)
      case "MaximalNumberValidator"             => maximalNumberCodec(cursor)
      case "ValidationExpressionParameterValidatorToCompile" =>
        validationExpressionCodec(cursor)
      case "CustomParameterValidatorDelegate" =>
        cursor
          .downField("className")
          .as[String]
          .map(cn => CustomParameterValidatorByClassLoader(cn))
          .orElse(cursor.downField("name").as[String].map(name => CustomParameterValidatorByNameLoader(name)))
      case other =>
        Left(DecodingFailure(s"Unknown ParameterValidator type: $other", cursor.history))
    }
  }

  implicit val encoder: Encoder[ParameterValidator] = Encoder.instance {
    case MandatoryExpressionValidator       => Json.obj("type" -> "MandatoryExpressionValidator".asJson)
    case NotNullValidator                   => Json.obj("type" -> "NotNullValidator".asJson)
    case CompileTimeEvaluableValueValidator => Json.obj("type" -> "CompileTimeEvaluableValueValidator".asJson)
    case NotBlankValidator                  => Json.obj("type" -> "NotBlankValidator".asJson)
    case LiteralIntegerExpressionValidator  => Json.obj("type" -> "LiteralIntegerExpressionValidator".asJson)
    case JsonExpressionValidator            => Json.obj("type" -> "JsonExpressionValidator".asJson)
    case NotEmptyCollectionValidator =>
      Json.obj("type" -> "NotEmptyCollectionValidator".asJson)
    case v: FixedValuesExpressionValidator =>
      fixedValuesCodec(v).deepMerge(Json.obj("type" -> "FixedValuesExpressionValidator".asJson))
    case v: RegExpValidator =>
      regExpCodec(v).deepMerge(Json.obj("type" -> "RegExpValidator".asJson))
    case v: MinimalNumberValidator =>
      minimalNumberCodec(v).deepMerge(Json.obj("type" -> "MinimalNumberValidator".asJson))
    case v: MaximalNumberValidator =>
      maximalNumberCodec(v).deepMerge(Json.obj("type" -> "MaximalNumberValidator".asJson))
    case v: ValidationExpressionParameterValidatorToCompile =>
      validationExpressionCodec(v).deepMerge(
        Json.obj("type" -> "ValidationExpressionParameterValidatorToCompile".asJson)
      )
    case v: CustomParameterValidatorByClassLoader =>
      Json.obj("type" -> "CustomParameterValidatorDelegate".asJson, "className" -> v.validatorClassName.asJson)
    case v: CustomParameterValidatorByNameLoader =>
      Json.obj("type" -> "CustomParameterValidatorDelegate".asJson, "name" -> v.name.asJson)
    case v =>
      throw new IllegalArgumentException(s"Cannot encode unknown ParameterValidator type: ${v.getClass.getName}")
  }

}

trait CompileTimeParameterValidator extends ParameterValidator with CompileTimeValidator

trait RuntimeParameterValidator extends ParameterValidator with RuntimeValidator

trait CompileTimeAndRuntimeValidator extends CompileTimeParameterValidator with RuntimeParameterValidator {

  protected def message: String
  protected def description: String

  override def isValid(paramName: ParameterName, expression: Expression, value: Option[Any], label: Option[String])(
      implicit nodeId: NodeId
  ): Validated[PartSubGraphCompilationError, Unit] =
    value match {
      case Some(v) => validate(v, compileTimeError(paramName, nodeId))
      case None    => valid(())
    }

  override def isValid(paramName: ParameterName, expression: Expression, value: Any)(
      implicit nodeId: NodeId
  ): Validated[ParameterRuntimeValidationError, Unit] =
    validate(value, runtimeError(expression))

  protected def validate[E](value: Any, error: => E): Validated[E, Unit]

  protected def compileTimeError(paramName: ParameterName, nodeId: NodeId): PartSubGraphCompilationError

  protected def runtimeError(expression: Expression): ParameterRuntimeValidationError =
    ParameterRuntimeValidationError(input = expression.expression, message = message)

}

case object MandatoryExpressionValidator extends CompileTimeParameterValidator {

  override def isValid(paramName: ParameterName, expression: Expression, value: Option[Any], label: Option[String])(
      implicit nodeId: NodeId
  ): Validated[PartSubGraphCompilationError, Unit] = {
    expression.language match {
      case Language.Spel | Language.DictKeyWithLabel | Language.TabularDataDefinition | Language.Json |
          Language.JsonTemplate =>
        Validated.cond(!expression.expression.isBlank, (), error(paramName, nodeId))
      case Language.SpelTemplate =>
        valid(())
    }

  }

  private def error(paramName: ParameterName, nodeId: NodeId): EmptyMandatoryParameter = EmptyMandatoryParameter(
    message = s"Field: ${paramName.value} is mandatory and can not be empty",
    description = "Please fill field for this parameter",
    paramName = paramName,
    nodeId = nodeId
  )

}

case object NotNullValidator extends CompileTimeAndRuntimeValidator {

  override protected val message     = "This field value can not be null"
  override protected val description = "Please provide the correct not null value"

  override protected def validate[E](value: Any, error: => E): Validated[E, Unit] =
    if (value == null) invalid(error) else valid(())

  override protected def compileTimeError(paramName: ParameterName, nodeId: NodeId): EmptyMandatoryParameter =
    EmptyMandatoryParameter(message, description, paramName, nodeId)

}

case object NotEmptyCollectionValidator extends CompileTimeAndRuntimeValidator {

  override protected val message     = "This field value can not be empty"
  override protected val description = "Please provide the correct not empty collection"

  // null value should not be validated - we want to chain validators
  override protected def validate[E](value: Any, error: => E): Validated[E, Unit] =
    value match {
      case c: java.util.Collection[_] if c.isEmpty => invalid(error)
      case m: java.util.Map[_, _] if m.isEmpty     => invalid(error)
      case _                                       => valid(())
    }

  override protected def compileTimeError(paramName: ParameterName, nodeId: NodeId): EmptyMandatoryParameter =
    EmptyMandatoryParameter(message, description, paramName, nodeId)

}

case object CompileTimeEvaluableValueValidator extends CompileTimeParameterValidator {

  override def isValid(paramName: ParameterName, expression: Expression, value: Option[Any], label: Option[String])(
      implicit nodeId: NodeId
  ): Validated[PartSubGraphCompilationError, Unit] = {
    value match {
      case None => invalid(error(paramName, nodeId))
      case _    => valid(())
    }
  }

  private def error(paramName: ParameterName, nodeId: NodeId): CompileTimeEvaluableParameterNotEvaluated =
    CompileTimeEvaluableParameterNotEvaluated(
      message = "This field's value has to be evaluable at deployment time",
      description = "Please provide a value that is evaluable at deployment time",
      paramName = paramName,
      nodeId = nodeId
    )

}

case object NotBlankValidator extends CompileTimeAndRuntimeValidator {

  override protected val message     = "This field value can not be blank"
  override protected val description = "Please provide the correct not blank value"

  // null value should not be validated - we want to chain validators
  override protected def validate[E](value: Any, error: => E): Validated[E, Unit] =
    value match {
      case s: String if s.isBlank => invalid(error)
      case _                      => valid(())
    }

  override protected def compileTimeError(paramName: ParameterName, nodeId: NodeId): BlankParameter =
    BlankParameter(message, description, paramName, nodeId)

}

case class FixedValuesExpressionValidator(possibleValues: List[FixedExpressionValue])
    extends CompileTimeParameterValidator {

  override def isValid(paramName: ParameterName, expression: Expression, value: Option[Any], label: Option[String])(
      implicit nodeId: NodeId
  ): Validated[PartSubGraphCompilationError, Unit] = {
    // FIXME: we should properly evaluate `possibleValues`
    val values = possibleValues.map(possibleValue => possibleValue.expression)

    // empty expression should not be validated - we want to chain validators
    expression.expression match {
      case e if e.isBlank          => valid(())
      case e if values.contains(e) => valid(())
      case e                       => invalid(InvalidPropertyFixedValue(paramName, label, e, possibleValues))
    }
  }

}

case class RegExpValidator(pattern: String, message: String, description: String)
    extends CompileTimeAndRuntimeValidator {

  private lazy val regexpPattern: Pattern = Pattern.compile(pattern)

  // null value should not be validated - we want to chain validators
  override protected def validate[E](value: Any, error: => E): Validated[E, Unit] =
    value match {
      case null                                            => valid(())
      case s: String if regexpPattern.matcher(s).matches() => valid(())
      case _                                               => invalid(error)
    }

  override protected def compileTimeError(paramName: ParameterName, nodeId: NodeId): MismatchParameter =
    MismatchParameter(message, description, paramName, nodeId)

}

// TODO: we need this validator because scenario properties do not have typing result, so we enforce proper type
//   here in validator by parsing raw expression to int
case object LiteralIntegerExpressionValidator extends CompileTimeParameterValidator {

  // empty expression should not be validated - we want to chain validators
  override def isValid(paramName: ParameterName, expression: Expression, value: Option[Any], label: Option[String])(
      implicit nodeId: NodeId
  ): Validated[PartSubGraphCompilationError, Unit] =
    expression.expression match {
      case e if e.isBlank              => valid(())
      case e if Try(e.toInt).isSuccess => valid(())
      case _                           => invalid(error(paramName, nodeId))
    }

  private def error(paramName: ParameterName, nodeId: NodeId): InvalidIntegerLiteralParameter =
    InvalidIntegerLiteralParameter(
      "This field value has to be an integer number",
      "Please fill field by proper integer type",
      paramName,
      nodeId
    )

}

case class MinimalNumberValidator(minimalNumber: BigDecimal) extends CompileTimeAndRuntimeValidator {

  override protected val message     = s"This field value has to be a number greater than or equal to $minimalNumber"
  override protected val description = "Please fill field with proper number"

  // null value should not be validated - we want to chain validators
  override protected def validate[E](value: Any, error: => E): Validated[E, Unit] =
    value match {
      case null                                                 => valid(())
      case n: BigDecimal if n >= minimalNumber                  => valid(())
      case n: Number if BigDecimal(n.toString) >= minimalNumber => valid(())
      case _                                                    => invalid(error)
    }

  override protected def compileTimeError(paramName: ParameterName, nodeId: NodeId): LowerThanRequiredParameter =
    LowerThanRequiredParameter(message, description, paramName, nodeId)

}

case class MaximalNumberValidator(maximalNumber: BigDecimal) extends CompileTimeAndRuntimeValidator {

  override protected val message     = s"This field value has to be a number lower than or equal to $maximalNumber"
  override protected val description = "Please fill field with proper number"

  // null value should not be validated - we want to chain validators
  override protected def validate[E](value: Any, error: => E): Validated[E, Unit] =
    value match {
      case null                                                 => valid(())
      case n: BigDecimal if n <= maximalNumber                  => valid(())
      case n: Number if BigDecimal(n.toString) <= maximalNumber => valid(())
      case _                                                    => invalid(error)
    }

  override protected def compileTimeError(paramName: ParameterName, nodeId: NodeId): GreaterThanRequiredParameter =
    GreaterThanRequiredParameter(message, description, paramName, nodeId)

}

// This validator is not determined by default in components based on usage of JsonParameterEditor because someone may want to use only
// editor for syntax highlight but don't want to use validator e.g. when want user to provide SpEL literal map
case object JsonExpressionValidator extends CompileTimeParameterValidator {

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
          case Left(parsingFailure) => invalid(error(parsingFailure.message, paramName, nodeId))
        }
      case o =>
        invalid(
          error(s"Expected String with valid json, got object of class: ${o.getClass.getName}", paramName, nodeId)
        )
    }
  }

  private def error(message: String, paramName: ParameterName, nodeId: NodeId): JsonRequiredParameter =
    JsonRequiredParameter(
      message,
      "Please fill field with valid json",
      paramName,
      nodeId
    )

}

case class MultiSelectFixedValuesExpressionValidator(possibleSelectValues: List[MultiSelectFixedValue])
    extends CompileTimeParameterValidator {

  private val possibleValues: List[Json] = possibleSelectValues.map(_.value)

  override def isValid(paramName: ParameterName, expression: Expression, value: Option[Any], label: Option[String])(
      implicit nodeId: NodeId
  ): Validated[PartSubGraphCompilationError, Unit] = {
    import scala.jdk.CollectionConverters._
    parse(expression.expression) match {
      case Right(json) =>
        json.asArray match {
          case Some(elements) => ensureOnlyPossibleElements(paramName, elements.toList)
          case None           => invalid(invalidFormatError(paramName, nodeId, s"Expected a List, got value: $json"))
        }
      case Left(error) =>
        invalid(
          invalidFormatError(
            paramName,
            nodeId,
            s"Could not parse expression as JSON: ${expression.expression}. Error: $error"
          )
        )
    }
  }

  private def ensureOnlyPossibleElements(parameterName: ParameterName, elements: List[Json])(
      implicit nodeId: NodeId
  ): Validated[PartSubGraphCompilationError, Unit] = {
    elements
      .find(!possibleValues.contains(_))
      .map(unallowedValue => invalid(unallowedValueError(parameterName, nodeId, unallowedValue)))
      .getOrElse(valid(()))
  }

  private def invalidFormatError(paramName: ParameterName, nodeId: NodeId, message: String) =
    MultiSelectInvalidFormat(
      message,
      paramName,
      nodeId,
    )

  private def unallowedValueError(parameterName: ParameterName, nodeId: NodeId, unallowedValue: Json) = {
    MultiSelectUnallowedValue(
      unallowedValue,
      possibleSelectValues,
      parameterName,
      nodeId
    )
  }

}

case class ValidationExpressionParameterValidatorToCompile(
    validationExpression: Expression,
    validationFailedMessage: Option[String]
) extends CompileTimeParameterValidator {

  override def isValid(paramName: ParameterName, expression: Expression, value: Option[Any], label: Option[String])(
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

sealed trait CustomParameterValidator {
  def name: String
}

trait CustomCompileTimeParameterValidator extends CustomParameterValidator with CompileTimeValidator

trait CustomRuntimeParameterValidator extends CustomParameterValidator with RuntimeValidator

sealed trait CustomParameterValidatorLoader extends ParameterValidator {
  protected def load(): CustomParameterValidator

  lazy val resolved: ParameterValidator with WithUnderlyingCustomParameterValidator = load() match {
    case validator: CustomCompileTimeParameterValidator with CustomRuntimeParameterValidator =>
      new CompileTimeParameterValidator with RuntimeParameterValidator with WithUnderlyingCustomParameterValidator {
        override val underlying: CustomCompileTimeParameterValidator with CustomRuntimeParameterValidator = validator
        override def isValid(
            paramName: ParameterName,
            expression: Expression,
            value: Option[Any],
            label: Option[String]
        )(implicit nodeId: NodeId): Validated[PartSubGraphCompilationError, Unit] =
          underlying.isValid(paramName, expression, value, label)
        override def isValid(paramName: ParameterName, expression: Expression, value: Any)(
            implicit nodeId: NodeId
        ): Validated[ParameterRuntimeValidationError, Unit] = underlying.isValid(paramName, expression, value)
      }
    case validator: CustomCompileTimeParameterValidator =>
      new CompileTimeParameterValidator with WithUnderlyingCustomParameterValidator {
        override val underlying: CustomCompileTimeParameterValidator = validator
        override def isValid(
            paramName: ParameterName,
            expression: Expression,
            value: Option[Any],
            label: Option[String]
        )(implicit nodeId: NodeId): Validated[PartSubGraphCompilationError, Unit] =
          underlying.isValid(paramName, expression, value, label)
      }
    case validator: CustomRuntimeParameterValidator =>
      new RuntimeParameterValidator with WithUnderlyingCustomParameterValidator {
        override val underlying: CustomRuntimeParameterValidator = validator
        override def isValid(paramName: ParameterName, expression: Expression, value: Any)(
            implicit nodeId: NodeId
        ): Validated[ParameterRuntimeValidationError, Unit] = underlying.isValid(paramName, expression, value)
      }
  }

}

object CustomParameterValidatorLoader {

  trait WithUnderlyingCustomParameterValidator {
    def underlying: CustomParameterValidator
  }

}

case class CustomParameterValidatorByNameLoader(name: String) extends CustomParameterValidatorLoader {
  import CustomParameterValidatorByNameLoader._
  override protected def load(): CustomParameterValidator = getOrLoad(name)
}

object CustomParameterValidatorByNameLoader {
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

case class CustomParameterValidatorByClassLoader(validatorClassName: String) extends CustomParameterValidatorLoader {
  import CustomParameterValidatorByClassLoader._
  override protected def load(): CustomParameterValidator = getOrLoad(validatorClassName)
}

object CustomParameterValidatorByClassLoader {

  def apply(clazz: Class[_ <: CustomParameterValidator]): CustomParameterValidatorByClassLoader =
    new CustomParameterValidatorByClassLoader(clazz.getName)

  private val cache: TrieMap[String, CustomParameterValidator] = TrieMap.empty

  private def getOrLoad(className: String): CustomParameterValidator =
    cache.getOrElseUpdate(className, load(className))

  private def load(className: String): CustomParameterValidator =
    try {
      Class
        .forName(className, true, Thread.currentThread().getContextClassLoader)
        .getDeclaredConstructor()
        .newInstance() match {
        case v: CustomParameterValidator => v
        case _ => throw new RuntimeException(s"Class $className does not extend CustomParameterValidator")
      }
    } catch {
      case e: ReflectiveOperationException =>
        throw new RuntimeException(s"Failed to instantiate CustomParameterValidator '$className': ${e.getMessage}", e)
    }

}
