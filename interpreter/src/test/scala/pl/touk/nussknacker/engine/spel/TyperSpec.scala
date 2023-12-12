package pl.touk.nussknacker.engine.spel

import cats.data.Validated.Valid
import cats.data.ValidatedNel
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.springframework.expression.common.TemplateParserContext
import org.springframework.expression.spel.standard
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.generics.ExpressionParseError
import pl.touk.nussknacker.engine.api.typed.supertype.{CommonSupertypeFinder, SupertypeClassResolutionStrategy}
import pl.touk.nussknacker.engine.definition.clazz.ClassDefinitionSet
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult, TypedObjectWithValue}
import pl.touk.nussknacker.engine.dict.{KeysDictTyper, SimpleDictRegistry}
import pl.touk.nussknacker.engine.expression.PositionRange
import pl.touk.nussknacker.engine.spel.Typer.TypingResultWithContext
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage

class TyperSpec extends AnyFunSuite with Matchers with ValidatedValuesDetailedMessage {

  private implicit val defaultTyper: Typer = buildTyper()
  private implicit val parser: standard.SpelExpressionParser =
    new org.springframework.expression.spel.standard.SpelExpressionParser()

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

  val testRecordExpr = "{int: 1, string: \"stringVal\", nestedRecord: {nestedRecordKey: 2}}"

  test("indexing on records with primitive values") {
    typeExpression(s"$testRecordExpr[\"string\"]").validValue.finalResult.typingResult shouldBe
      TypedObjectWithValue(Typed.typedClass[String], "stringVal")
    typeExpression(s"$testRecordExpr[string]").validValue.finalResult.typingResult shouldBe
      TypedObjectWithValue(Typed.typedClass[String], "stringVal")
    typeExpression(s"$testRecordExpr[\"int\"]").validValue.finalResult.typingResult shouldBe
      TypedObjectWithValue(Typed.typedClass[Int], 1)
    typeExpression(s"$testRecordExpr[int]").validValue.finalResult.typingResult shouldBe
      TypedObjectWithValue(Typed.typedClass[Int], 1)
  }

  test("indexing on records with record values") {
    typeExpression(s"$testRecordExpr[\"nestedRecord\"]").validValue.finalResult.typingResult shouldBe
      TypedObjectTypingResult(Map("nestedRecordKey" -> TypedObjectWithValue(Typed.typedClass[Int], 2)))
  }

  test("nested indexing on records") {
    typeExpression(
      s"$testRecordExpr[\"nestedRecord\"][\"nestedRecordKey\"]"
    ).validValue.finalResult.typingResult shouldBe
      TypedObjectWithValue(Typed.typedClass[Int], 2)
  }

  private def buildTyper(dynamicPropertyAccessAllowed: Boolean = false) = new Typer(
    commonSupertypeFinder = new CommonSupertypeFinder(
      classResolutionStrategy = SupertypeClassResolutionStrategy.Union,
      strictTaggedTypesChecking = false
    ),
    dictTyper = new KeysDictTyper(new SimpleDictRegistry(Map.empty)),
    strictMethodsChecking = false,
    staticMethodInvocationsChecking = false,
    classDefinitionSet = ClassDefinitionSet.forDefaultAdditionalClasses,
    evaluationContextPreparer = null,
    methodExecutionForUnknownAllowed = false,
    dynamicPropertyAccessAllowed = dynamicPropertyAccessAllowed
  )

  private def typeExpression(
      expr: String,
      variables: (String, Any)*
  )(
      implicit typer: Typer,
      parser: standard.SpelExpressionParser
  ): ValidatedNel[ExpressionParseError, CollectedTypingResult] = {
    val parsed        = parser.parseExpression(expr)
    val validationCtx = ValidationContext(variables.toMap.mapValuesNow(Typed.fromInstance))
    typer.typeExpression(parsed, validationCtx)
  }

  private def typeTemplate(
      expr: String,
      variables: (String, Any)*
  )(
      implicit typer: Typer,
      parser: standard.SpelExpressionParser
  ): ValidatedNel[ExpressionParseError, CollectedTypingResult] = {
    val parsed        = parser.parseExpression(expr, new TemplateParserContext())
    val validationCtx = ValidationContext(variables.toMap.mapValuesNow(Typed.fromInstance))
    typer.typeExpression(parsed, validationCtx)
  }

}
