package pl.touk.nussknacker.engine.util.functions

import cats.data.{NonEmptyList, Validated, ValidatedNel}
import pl.touk.nussknacker.engine.api.{CompiledExpression, Context}
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.generics.ExpressionParseError
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult, Unknown}
import pl.touk.nussknacker.engine.definition.clazz.ClassDefinitionTestUtils
import pl.touk.nussknacker.engine.dict.SimpleDictRegistry
import pl.touk.nussknacker.engine.spel.SpelExpressionParser
import pl.touk.nussknacker.engine.testing.ModelDefinitionBuilder
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap

import java.text.ParseException
import java.time.{Clock, ZoneOffset, ZonedDateTime}
import java.util.Locale
import scala.reflect.runtime.universe._

trait BaseSpelSpec {

  protected val fixedZoned: ZonedDateTime = ZonedDateTime.of(2020, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)

  private val globalVariables = Map[String, Any](
    "COLLECTION"  -> collection,
    "DATE"        -> new DateUtils(Clock.fixed(fixedZoned.toInstant, ZoneOffset.UTC)),
    "DATE_FORMAT" -> new DateFormatUtils(Locale.US),
    "UTIL"        -> util,
    "NUMERIC"     -> numeric,
    "CONV"        -> conversion,
    "BASE64"      -> base64
  )

  private val parser = SpelExpressionParser.default(
    getClass.getClassLoader,
    ModelDefinitionBuilder.emptyExpressionConfig,
    new SimpleDictRegistry(Map.empty),
    enableSpelForceCompile = false,
    SpelExpressionParser.Standard,
    classDefinitions,
  )

  private lazy val classDefinitions = ClassDefinitionTestUtils.createDefinitionForClasses(
    collection.getClass,
    classOf[DateUtils],
    classOf[DateFormatUtils],
    util.getClass,
    numeric.getClass,
    conversion.getClass,
  )

  protected def evaluate[T: TypeTag](expr: String, localVariables: Map[String, Any] = Map.empty): T = {
    val validationCtx = ValidationContext(
      localVariables.mapValuesNow(Typed.fromInstance),
      globalVariables.mapValuesNow(Typed.fromInstance)
    )
    val evaluationCtx = Context("fooId").withVariables(localVariables)
    parser
      .parse(expr, validationCtx, Typed.fromDetailedType[T])
      .value
      .expression
      .evaluate[T](evaluationCtx, globalVariables)
  }

  protected def evaluateAny(expr: String, localVariables: Map[String, Any] = Map.empty): AnyRef = {
    val validationCtx = ValidationContext(
      localVariables.mapValuesNow(Typed.fromInstance),
      globalVariables.mapValuesNow(Typed.fromInstance)
    )
    val evaluationCtx = Context("fooId").withVariables(localVariables)
    parser.parse(expr, validationCtx, Unknown).value.expression.evaluate[AnyRef](evaluationCtx, globalVariables)
  }

  protected def evaluateType(
      expr: String,
      localVariables: Map[String, Any] = Map.empty,
      types: Map[String, TypingResult] = Map.empty
  ): Validated[NonEmptyList[ExpressionParseError], String] = {
    val validationCtx = ValidationContext(
      localVariables.mapValuesNow(Typed.fromInstance) ++ types,
      globalVariables.mapValuesNow(Typed.fromInstance)
    )
    parser.parse(expr, validationCtx, Unknown).map(_.returnType.display)
  }

  protected implicit class ValidatedValue[E, A](validated: ValidatedNel[ExpressionParseError, A]) {
    def value: A = validated.valueOr(err => throw new ParseException(err.map(_.message).toList.mkString, -1))
  }

  protected implicit class EvaluateSync(expression: CompiledExpression) {
    def evaluateSync[T](ctx: Context): T = expression.evaluate(ctx, Map.empty)
  }

}
