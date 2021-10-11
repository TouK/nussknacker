package pl.touk.nussknacker.engine.util.functions

import cats.data.ValidatedNel
import pl.touk.nussknacker.engine.TypeDefinitionSet
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.expression.{Expression, ExpressionParseError, TypedExpression}
import pl.touk.nussknacker.engine.api.process.ClassExtractionSettings
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.api.{Context, SpelExpressionExcludeList}
import pl.touk.nussknacker.engine.dict.SimpleDictRegistry
import pl.touk.nussknacker.engine.spel.SpelExpressionParser

import java.text.ParseException
import java.time.{Clock, ZoneOffset, ZonedDateTime}
import java.util.Locale
import scala.reflect.runtime.universe._

trait BaseSpelSpec {

  protected val fixedZoned: ZonedDateTime = ZonedDateTime.of(2020, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)

  private val globalVariables = Map[String, Any](
    "DATE" -> new DateUtils(Clock.fixed(fixedZoned.toInstant, ZoneOffset.UTC)),
    "DATE_FORMAT" -> new DateFormatUtils(Locale.US))

  protected def evaluate[T: TypeTag](expr: String, localVariables: Map[String, Any]): T = {
    val validationCtx = ValidationContext(localVariables.mapValues(Typed.fromInstance), globalVariables.mapValues(Typed.fromInstance))
    val evaluationCtx = Context.withInitialId.withVariables(localVariables)
    parse(expr, validationCtx).value.expression.evaluate[T](evaluationCtx, globalVariables)
  }

  protected def parse[T: TypeTag](expr: String, validationCtx: ValidationContext): ValidatedNel[ExpressionParseError, TypedExpression] = {
    val parser = SpelExpressionParser.default(getClass.getClassLoader, new SimpleDictRegistry(Map.empty), enableSpelForceCompile = false, strictTypeChecking = true,
      List.empty, SpelExpressionParser.Standard, strictMethodsChecking = true, staticMethodInvocationsChecking = true, TypeDefinitionSet.empty,
      methodExecutionForUnknownAllowed = false, dynamicPropertyAccessAllowed = false, SpelExpressionExcludeList.default)(ClassExtractionSettings.Default)
    parser.parse(expr, validationCtx, Typed.fromDetailedType[T])
  }

  protected implicit class ValidatedValue[E, A](validated: ValidatedNel[ExpressionParseError, A]) {
    def value: A = validated.valueOr(err => throw new ParseException(err.map(_.message).toList.mkString, -1))
  }

  protected implicit class EvaluateSync(expression: Expression) {
    def evaluateSync[T](ctx: Context): T  = expression.evaluate(ctx, Map.empty)
  }

}
