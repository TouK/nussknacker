package pl.touk.nussknacker.engine.spel

import cats.data.Validated.Valid
import cats.data.{NonEmptyList, ValidatedNel}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.springframework.expression.common.TemplateParserContext
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.generics.ExpressionParseError
import pl.touk.nussknacker.engine.api.typed.supertype.{CommonSupertypeFinder, SupertypeClassResolutionStrategy}
import pl.touk.nussknacker.engine.definition.clazz.ClassDefinitionSet
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult, TypedObjectWithValue}
import pl.touk.nussknacker.engine.dict.{KeysDictTyper, SimpleDictRegistry}
import pl.touk.nussknacker.engine.expression.PositionRange
import pl.touk.nussknacker.engine.spel.SpelExpressionParseError.IllegalOperationError.DynamicPropertyAccessError
import pl.touk.nussknacker.engine.spel.Typer.TypingResultWithContext
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage

class TyperSpec extends AnyFunSuite with Matchers with ValidatedValuesDetailedMessage {

  test("simple expression") {
    typeExpression("#x + 2", "x" -> 2) shouldBe Valid(
      CollectedTypingResult(
        Map(
          PositionRange(0, 2) -> TypingResultWithContext(Typed.fromInstance(2)),
          PositionRange(3, 4) -> TypingResultWithContext(Typed.fromInstance(4)),
          PositionRange(5, 6) -> TypingResultWithContext(Typed.fromInstance(2))
        ),
        TypingResultWithContext(Typed.fromInstance(4))
      )
    )
  }

  test("template") {
    typeTemplate("result: #{#x + 2}", "x" -> 2) shouldBe Valid(
      CollectedTypingResult(Map.empty, TypingResultWithContext(Typed[String]))
    )
  }

  test("detect proper selection types") {
    typeExpression("{1,2}.?[(#this==1)]").validValue.finalResult.typingResult shouldBe
      Typed.genericTypeClass(classOf[java.util.List[_]], List(Typed.typedClass[Int]))
  }

  test("detect proper first selection types") {
    typeExpression("{1,2}.$[(#this==1)]").validValue.finalResult.typingResult shouldBe Typed.typedClass[Int]
  }

  test("detect proper last selection types") {
    typeExpression("{1,2}.^[(#this==1)]").validValue.finalResult.typingResult shouldBe Typed.typedClass[Int]
  }

  test("detect proper nested selection types") {
    typeExpression("{{1},{1,2}}.$[(#this.size > 1)]").validValue.finalResult.typingResult shouldBe
      Typed.genericTypeClass(classOf[java.util.List[_]], List(Typed.typedClass[Int]))
  }

  test("detect proper chained selection types") {
    typeExpression("{{1},{1,2}}.$[(#this.size > 1)].^[(#this==1)]").validValue.finalResult.typingResult shouldBe
      Typed.typedClass[Int]
  }

  test("restricting simple type selection") {
    typeExpression("1.$[(#this.size > 1)].^[(#this==1)]").invalidValue.head.message shouldBe
      s"Cannot do projection/selection on ${Typed.fromInstance(1).display}"
  }

  test("indexing on records with primitive values") {
    typeExpression("{\"key1\": \"val1\", \"key2\": \"val2\"}[\"key2\"]").validValue.finalResult.typingResult shouldBe
      TypedObjectWithValue(Typed.typedClass[String], "val2")
    typeExpression("{\"key1\": \"val1\", \"key2\": 1}[\"key2\"]").validValue.finalResult.typingResult shouldBe
      TypedObjectWithValue(Typed.typedClass[Int], 1)
  }

  test("indexing on records with record values") {
    typeExpression(
      "{\"key1\": \"val1\", \"key2\": {\"nestedKey1\": \"nestedVal1\"}}[\"key2\"]"
    ).validValue.finalResult.typingResult shouldBe
      TypedObjectTypingResult(Map("nestedKey1" -> TypedObjectWithValue(Typed.typedClass[String], "nestedVal1")))
  }

  test("nested indexing on records") {
    typeExpression(
      "{\"key1\": \"val1\", \"key2\": {\"nestedKey1\": \"nestedVal1\"}}[\"key2\"][\"nestedKey1\"]"
    ).validValue.finalResult.typingResult shouldBe
      TypedObjectWithValue(Typed.typedClass[String], "nestedVal1")
  }

  test("dynamic property access on records returns error when disabled") {
    typeExpression("{\"key1\": \"val1\", \"key2\": 1}[\"key\" + 2]").invalidValue shouldBe
      NonEmptyList(DynamicPropertyAccessError, Nil)
  }

  private val strictTypeChecking               = false
  private val strictMethodsChecking            = false
  private val staticMethodInvocationsChecking  = false
  private val methodExecutionForUnknownAllowed = false
  private val dynamicPropertyAccessAllowed     = false
  private val classResolutionStrategy          = SupertypeClassResolutionStrategy.Union
  private val commonSupertypeFinder            = new CommonSupertypeFinder(classResolutionStrategy, strictTypeChecking)
  private val dict                             = new SimpleDictRegistry(Map.empty)

  private val typer = new Typer(
    commonSupertypeFinder,
    new KeysDictTyper(dict),
    strictMethodsChecking,
    staticMethodInvocationsChecking,
    ClassDefinitionSet.forDefaultAdditionalClasses,
    evaluationContextPreparer = null,
    methodExecutionForUnknownAllowed,
    dynamicPropertyAccessAllowed
  )

  private val parser = new org.springframework.expression.spel.standard.SpelExpressionParser()

  private def typeExpression(
      expr: String,
      variables: (String, Any)*
  ): ValidatedNel[ExpressionParseError, CollectedTypingResult] = {
    val parsed        = parser.parseExpression(expr)
    val validationCtx = ValidationContext(variables.toMap.mapValuesNow(Typed.fromInstance))
    typer.typeExpression(parsed, validationCtx)
  }

  private def typeTemplate(
      expr: String,
      variables: (String, Any)*
  ): ValidatedNel[ExpressionParseError, CollectedTypingResult] = {
    val parsed        = parser.parseExpression(expr, new TemplateParserContext())
    val validationCtx = ValidationContext(variables.toMap.mapValuesNow(Typed.fromInstance))
    typer.typeExpression(parsed, validationCtx)
  }

}
