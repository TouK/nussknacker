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
  * TODO: It shouldn't be a sealed trait. We should allow everyone to create own ParameterValidator
  * TODO: This being sealed also makes the tests of cases that use these validators dependant on the code here -
  * not good/unseal!!
  */
sealed trait ParameterValidator extends Validator

object ParameterValidator {

  def resolveLoaders(validators: List[Validator]): List[Validator] =
    validators.map { case d: CustomParameterValidatorLoader => d.resolved; case v => v }

  private val fixedValuesCodec: Codec[FixedValuesValidator] =
    Codec.forProduct1("possibleValues")(FixedValuesValidator.apply)(_.possibleValues)

  private val regExpCodec: Codec[RegExpParameterValidator] =
    Codec.forProduct3("pattern", "message", "description")(RegExpParameterValidator.apply)(v =>
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
      case "MandatoryParameterValidator"        => Right(MandatoryParameterValidator)
      case "NotNullParameterValidator"          => Right(NotNullParameterValidator)
      case "CompileTimeEvaluableValueValidator" => Right(CompileTimeEvaluableValueValidator)
      case "NotBlankParameterValidator"         => Right(NotBlankParameterValidator)
      case "LiteralIntegerValidator"            => Right(LiteralIntegerValidator)
      case "JsonValidator"                      => Right(JsonValidator)
      case "FixedValuesValidator"               => fixedValuesCodec(cursor)
      case "RegExpParameterValidator"           => regExpCodec(cursor)
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
    case MandatoryParameterValidator        => Json.obj("type" -> "MandatoryParameterValidator".asJson)
    case NotNullParameterValidator          => Json.obj("type" -> "NotNullParameterValidator".asJson)
    case CompileTimeEvaluableValueValidator => Json.obj("type" -> "CompileTimeEvaluableValueValidator".asJson)
    case NotBlankParameterValidator         => Json.obj("type" -> "NotBlankParameterValidator".asJson)
    case LiteralIntegerValidator            => Json.obj("type" -> "LiteralIntegerValidator".asJson)
    case JsonValidator                      => Json.obj("type" -> "JsonValidator".asJson)
    case v: FixedValuesValidator =>
      fixedValuesCodec(v).deepMerge(Json.obj("type" -> "FixedValuesValidator".asJson))
    case v: RegExpParameterValidator =>
      regExpCodec(v).deepMerge(Json.obj("type" -> "RegExpParameterValidator".asJson))
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

//TODO: These validators should be moved to separated module

case object MandatoryParameterValidator extends CompileTimeParameterValidator {

  override def isValid(paramName: ParameterName, expression: Expression, value: Option[Any], label: Option[String])(
      implicit nodeId: NodeId
  ): Validated[PartSubGraphCompilationError, Unit] = {
    expression.language match {
      case Language.Spel | Language.DictKeyWithLabel | Language.TabularDataDefinition | Language.Json |
          Language.JsonTemplate =>
        Validated.cond(!expression.expression.isBlank, (), error(paramName, nodeId.id))
      case Language.SpelTemplate =>
        valid(())
    }

  }

  private def error(paramName: ParameterName, nodeId: String): EmptyMandatoryParameter = EmptyMandatoryParameter(
    message = s"Field: ${paramName.value} is mandatory and can not be empty",
    description = "Please fill field for this parameter",
    paramName = paramName,
    nodeId = nodeId
  )

}

case object NotNullParameterValidator extends CompileTimeParameterValidator {

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

case object CompileTimeEvaluableValueValidator extends CompileTimeParameterValidator {

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

case object NotBlankParameterValidator extends CompileTimeParameterValidator {

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

case class FixedValuesValidator(possibleValues: List[FixedExpressionValue]) extends CompileTimeParameterValidator {

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

case class RegExpParameterValidator(pattern: String, message: String, description: String)
    extends CompileTimeParameterValidator {

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
case object LiteralIntegerValidator extends CompileTimeParameterValidator {

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

case class MinimalNumberValidator(minimalNumber: BigDecimal) extends CompileTimeParameterValidator {

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

case class MaximalNumberValidator(maximalNumber: BigDecimal) extends CompileTimeParameterValidator {

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
case object JsonValidator extends CompileTimeParameterValidator {

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

  @transient lazy val resolved: ParameterValidator with WithUnderlyingCustomParameterValidator = load() match {
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
