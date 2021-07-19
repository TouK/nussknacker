package pl.touk.nussknacker.engine.spel

import cats.data.Validated.Valid
import cats.data.ValidatedNel
import org.scalatest.{FunSuite, Matchers}
import org.springframework.expression.common.TemplateParserContext
import pl.touk.nussknacker.engine.TypeDefinitionSet
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.expression.ExpressionParseError
import pl.touk.nussknacker.engine.api.process.ClassExtractionSettings
import pl.touk.nussknacker.engine.api.typed.supertype.{CommonSupertypeFinder, SupertypeClassResolutionStrategy}
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.dict.{KeysDictTyper, SimpleDictRegistry}
import pl.touk.nussknacker.engine.expression.PositionRange
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap

class TyperSpec extends FunSuite with Matchers {

  test("simple expression") {
    typeExpression("#x + 2", "x" -> 2) shouldBe Valid(CollectedTypingResult(Map(
      PositionRange(0, 2) -> Typed[Integer],
      PositionRange(3, 4) -> Typed[Integer],
      PositionRange(5, 6) -> Typed[Integer]
    ), Typed[Integer]))
  }

  test("template") {
    typeTemplate("result: #{#x + 2}", "x" -> 2) shouldBe Valid(CollectedTypingResult(Map.empty, Typed[String]))
  }

  private val strictTypeChecking = false
  private val strictMethodsChecking = false
  private val staticMethodInvocationsChecking = false
  private val disableMethodExecutionForUnknown = false
  private val classResolutionStrategy = SupertypeClassResolutionStrategy.Union
  private val commonSupertypeFinder = new CommonSupertypeFinder(classResolutionStrategy, strictTypeChecking)
  private val dict = new SimpleDictRegistry(Map.empty)
  private val typer = new Typer(this.getClass.getClassLoader, commonSupertypeFinder, new KeysDictTyper(dict), strictMethodsChecking, staticMethodInvocationsChecking, TypeDefinitionSet.empty, evaluationContextPreparer = null, disableMethodExecutionForUnknown)(ClassExtractionSettings.Default)
  private val parser = new org.springframework.expression.spel.standard.SpelExpressionParser()

  private def typeExpression(expr: String, variables: (String, Any)*): ValidatedNel[ExpressionParseError, CollectedTypingResult] = {
    val parsed = parser.parseExpression(expr)
    val validationCtx = ValidationContext(variables.toMap.mapValuesNow(Typed.fromInstance))
    typer.typeExpression(parsed, validationCtx)
  }

  private def typeTemplate(expr: String, variables: (String, Any)*): ValidatedNel[ExpressionParseError, CollectedTypingResult] = {
    val parsed = parser.parseExpression(expr, new TemplateParserContext())
    val validationCtx = ValidationContext(variables.toMap.mapValuesNow(Typed.fromInstance))
    typer.typeExpression(parsed, validationCtx)
  }
}