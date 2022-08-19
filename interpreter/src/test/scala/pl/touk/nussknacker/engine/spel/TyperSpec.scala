package pl.touk.nussknacker.engine.spel

import cats.data.Validated.Valid
import cats.data.ValidatedNel
import org.scalatest.prop.TableDrivenPropertyChecks.forAll
import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.springframework.expression.common.TemplateParserContext
import pl.touk.nussknacker.engine.TypeDefinitionSet
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.generics.ExpressionParseError
import pl.touk.nussknacker.engine.api.process.ClassExtractionSettings
import pl.touk.nussknacker.engine.api.typed.supertype.{CommonSupertypeFinder, SupertypeClassResolutionStrategy}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.dict.{KeysDictTyper, SimpleDictRegistry}
import pl.touk.nussknacker.engine.expression.PositionRange
import pl.touk.nussknacker.engine.spel.Typer.TypingResultWithContext
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap

class TyperSpec extends AnyFunSuite with Matchers {

  test("simple expression") {
    println(typeExpression("#x + 2", "x" -> 2).toOption.get.finalResult)
    typeExpression("#x + 2", "x" -> 2) shouldBe Valid(CollectedTypingResult(Map(
      PositionRange(0, 2) -> TypingResultWithContext(Typed.fromInstance(2)),
      PositionRange(3, 4) -> TypingResultWithContext(Typed.fromInstance(4)),
      PositionRange(5, 6) -> TypingResultWithContext(Typed.fromInstance(2))
    ), TypingResultWithContext(Typed.fromInstance(4))))
  }

  test("template") {
    typeTemplate("result: #{#x + 2}", "x" -> 2) shouldBe Valid(CollectedTypingResult(Map.empty, TypingResultWithContext(Typed[String])))
  }

  test("detect proper selection types") {
    typeExpression("{1,2}.?[(#this==1)]").toOption.get.finalResult.typingResult shouldBe
      Typed.genericTypeClass(classOf[java.util.List[_]], List(Typed.typedClass[Int]))
  }

  test("detect proper first selection types") {
    typeExpression("{1,2}.$[(#this==1)]").toOption.get.finalResult.typingResult shouldBe Typed.typedClass[Int]
  }

  test("detect proper last selection types") {
    typeExpression("{1,2}.^[(#this==1)]").toOption.get.finalResult.typingResult shouldBe Typed.typedClass[Int]
  }

  test("detect proper nested selection types") {
    typeExpression("{{1},{1,2}}.$[(#this.size > 1)]").toOption.get.finalResult.typingResult shouldBe
      Typed.genericTypeClass(classOf[java.util.List[_]], List(Typed.typedClass[Int]))
  }

  test("detect proper chained selection types") {
    typeExpression("{{1},{1,2}}.$[(#this.size > 1)].^[(#this==1)]").toOption.get.finalResult.typingResult shouldBe
      Typed.typedClass[Int]
  }

  test("restricting simple type selection") {
    typeExpression("1.$[(#this.size > 1)].^[(#this==1)]").toEither.left.get.head.message shouldBe
      s"Cannot do projection/selection on ${Typed.fromInstance(1).display}"
  }

  test("should calculate values of operators") {
    def checkFinalResult(expr: String, expected: Any): Unit = {
      typeExpression(expr).toOption.get.finalResult.typingResult shouldBe Typed.fromInstance(expected)
    }

    val table = Table(
      ("expr", "expected"),
      ("--2", 1),
      ("++2", 3),
      ("2 / 2", 1),
      ("2 - 2", 0),
      ("-2", -2),
      ("2 % 2", 0),
      ("2 * 2", 4),
      ("2 + 2", 4),
      ("+5", 5),
      ("'a' + 'a'", "aa"),
      ("'a' + 1", "a1"),
      ("1 + 'a'", "1a"),
      ("5 == 5", true),
      ("5 != 5", false),
      ("5 > 4", true),
      ("5 >= 6", false),
      ("3 < 2", false),
      ("4 <= 4", true)
    )
    forAll(table)(checkFinalResult)
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
