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
import pl.touk.nussknacker.engine.spel.Typer.TypingResultWithContext
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap

class TyperSpec extends FunSuite with Matchers {

  test("simple expression") {
    typeExpression("#x + 2", "x" -> 2) shouldBe Valid(CollectedTypingResult(Map(
      PositionRange(0, 2) -> TypingResultWithContext(Typed[Integer]),
      PositionRange(3, 4) -> TypingResultWithContext(Typed[Integer]),
      PositionRange(5, 6) -> TypingResultWithContext(Typed[Integer])
    ), TypingResultWithContext(Typed[Integer])))
  }

  test("template") {
    typeTemplate("result: #{#x + 2}", "x" -> 2) shouldBe Valid(CollectedTypingResult(Map.empty, TypingResultWithContext(Typed[String])))
  }

  test("detect proper selection types") {
    typeExpression("{1,2}.?[(#this==1)]").toOption.get.finalResult.display shouldBe "List[Integer]"
  }

  test("detect proper first selection types") {
    typeExpression("{1,2}.$[(#this==1)]").toOption.get.finalResult.typingResult.display shouldBe "Integer"
  }

  test("detect proper last selection types") {
    typeExpression("{1,2}.^[(#this==1)]").toOption.get.finalResult.typingResult.display shouldBe "Integer"
  }

  test("detect proper nested selection types") {
    typeExpression("{{1},{1,2}}.$[(#this.size > 1)]").toOption.get.finalResult.typingResult.display shouldBe "List[Integer]"
  }

  test("detect proper chained selection types") {
    typeExpression("{{1},{1,2}}.$[(#this.size > 1)].^[(#this==1)]").toOption.get.finalResult.typingResult.display shouldBe "Integer"
  }

  test("restricting simple type selection") {
    typeExpression("1.$[(#this.size > 1)].^[(#this==1)]").toEither.left.get.head.message shouldBe "Cannot do projection/selection on Integer"
  }

  private val strictTypeChecking = false
  private val strictMethodsChecking = false
  private val staticMethodInvocationsChecking = false
  private val methodExecutionForUnknownAllowed = false
  private val dynamicPropertyAccessAllowed = false
  private val classResolutionStrategy = SupertypeClassResolutionStrategy.Union
  private val commonSupertypeFinder = new CommonSupertypeFinder(classResolutionStrategy, strictTypeChecking)
  private val dict = new SimpleDictRegistry(Map.empty)
  private val typer = new Typer(this.getClass.getClassLoader, commonSupertypeFinder, new KeysDictTyper(dict), strictMethodsChecking, staticMethodInvocationsChecking,
    TypeDefinitionSet.empty, evaluationContextPreparer = null, methodExecutionForUnknownAllowed, dynamicPropertyAccessAllowed)(ClassExtractionSettings.Default)
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
