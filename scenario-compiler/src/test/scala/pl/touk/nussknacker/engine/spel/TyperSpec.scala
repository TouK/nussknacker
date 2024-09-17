package pl.touk.nussknacker.engine.spel

import cats.data.Validated.Valid
import cats.data.ValidatedNel
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.springframework.expression.common.TemplateParserContext
import org.springframework.expression.spel.standard
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.generics.ExpressionParseError
import pl.touk.nussknacker.engine.api.typed.typing.Typed.typedListWithElementValues
import pl.touk.nussknacker.engine.api.typed.typing._
import pl.touk.nussknacker.engine.definition.clazz.ClassDefinitionTestUtils
import pl.touk.nussknacker.engine.dict.{KeysDictTyper, SimpleDictRegistry}
import pl.touk.nussknacker.engine.expression.PositionRange
import pl.touk.nussknacker.engine.spel.SpelExpressionParseError.IllegalOperationError.DynamicPropertyAccessError
import pl.touk.nussknacker.engine.spel.SpelExpressionParseError.MissingObjectError.NoPropertyError
import pl.touk.nussknacker.engine.spel.Typer.TypingResultWithContext
import pl.touk.nussknacker.engine.spel.TyperSpecTestData.TestRecord._
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage

import scala.jdk.CollectionConverters._

class TyperSpec extends AnyFunSuite with Matchers with ValidatedValuesDetailedMessage {

  private implicit val defaultTyper: Typer = buildTyper()
  private val dynamicAccessTyper: Typer    = buildTyper(dynamicPropertyAccessAllowed = true)
  private val parser: standard.SpelExpressionParser =
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

  test("detect proper List type with value - record inside") {
    typeExpression(s"{$testRecordExpr}").validValue.finalResult.typingResult shouldBe
      typedListWithElementValues(
        testRecordTyped.withoutValue,
        List(testRecordTyped.valueOpt.get).asJava
      )
  }

  test("detect proper List type with value") {
    typeExpression("{1,2}").validValue.finalResult.typingResult shouldBe
      typedListWithElementValues(Typed.typedClass[Int], List(1, 2).asJava)
  }

  test("detect proper selection types - List") {
    typeExpression("{1,2}.?[(#this==1)]").validValue.finalResult.typingResult shouldBe
      Typed.genericTypeClass(classOf[java.util.List[_]], List(Typed.typedClass[Int]))
    // see comment in Typer.resolveSelectionTypingResult
  }

  test("detect proper selection types - Map") {
    typeExpression("{'field1': 1, 'field2': 2}.?[(#this.value==1)]").validValue.finalResult.typingResult shouldBe
      Typed.record(Map.empty) // see comment in Typer.resolveSelectionTypingResult
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

  test("type record expression") {
    typeExpression(testRecordExpr).validValue.finalResult.typingResult shouldBe
      testRecordTyped
  }

  test("indexing on records for primitive types") {
    typeExpression(s"$testRecordExpr['string']").validValue.finalResult.typingResult shouldBe
      TypedObjectWithValue(Typed.typedClass[String], "stringVal")
    typeExpression(s"$testRecordExpr['int']").validValue.finalResult.typingResult shouldBe
      TypedObjectWithValue(Typed.typedClass[Int], 1)
    typeExpression(s"$testRecordExpr['boolean']").validValue.finalResult.typingResult shouldBe
      TypedObjectWithValue(Typed.typedClass[Boolean], true)
    typeExpression(s"$testRecordExpr['null']").validValue.finalResult.typingResult shouldBe
      TypedNull
  }

  test("indexing on records with string literal index") {
    typeExpression(s"$testRecordExpr['string']").validValue.finalResult.typingResult shouldBe
      TypedObjectWithValue(Typed.typedClass[String], "stringVal")
  }

  test("indexing on records with variable reference index") {
    typeExpression(s"$testRecordExpr[#var]", "var" -> "string").validValue.finalResult.typingResult shouldBe
      TypedObjectWithValue(Typed.typedClass[String], "stringVal")
  }

  test("indexing on records with property reference index") {
    typeExpression(s"$testRecordExpr[string]").validValue.finalResult.typingResult shouldBe
      TypedObjectWithValue(Typed.typedClass[String], "stringVal")
  }

  test("indexing on records for record values") {
    typeExpression(s"$testRecordExpr['nestedRecord']").validValue.finalResult.typingResult shouldBe
      Typed.record(Map("nestedRecordKey" -> TypedObjectWithValue(Typed.typedClass[Int], 2)))
  }

  test("indexing on records for nested record values") {
    typeExpression(
      s"$testRecordExpr['nestedRecord']['nestedRecordKey']"
    ).validValue.finalResult.typingResult shouldBe
      TypedObjectWithValue(Typed.typedClass[Int], 2)
  }

  test(
    "indexing on records with string literal or variable reference for not present keys returns error when dynamic access is disabled"
  ) {
    typeExpression(s"$testRecordExpr['$nonPresentKey']").invalidValue.toList should matchPattern {
      case NoPropertyError(typingResult, key) :: Nil if typingResult == testRecordTyped && key == nonPresentKey =>
    }
    typeExpression(s"$testRecordExpr[#var]", "var" -> s"$nonPresentKey").invalidValue.toList should matchPattern {
      case NoPropertyError(typingResult, key) :: Nil if typingResult == testRecordTyped && key == nonPresentKey =>
    }
    // TODO: this behavior is to be fixed - ideally this should behave the same as above
    typeExpression(s"$testRecordExpr[$nonPresentKey]").invalidValue.toList should matchPattern {
      case NoPropertyError(typingResult, key) :: DynamicPropertyAccessError :: Nil
          if typingResult == testRecordTyped && key == nonPresentKey =>
    }
  }

  test("indexing on records for not present keys returns unknown type or error when dynamic access is enabled") {
    typeExpression(s"$testRecordExpr['$nonPresentKey']")(
      dynamicAccessTyper
    ).validValue.finalResult.typingResult shouldBe
      Unknown
    typeExpression(s"$testRecordExpr[#var]", "var" -> nonPresentKey)(
      dynamicAccessTyper
    ).validValue.finalResult.typingResult shouldBe
      Unknown
    typeExpression(s"$testRecordExpr[$nonPresentKey]")(dynamicAccessTyper).invalidValue.toList should matchPattern {
      case NoPropertyError(typingResult, key) :: Nil if typingResult == testRecordTyped && key == nonPresentKey =>
    }
  }

  private def buildTyper(dynamicPropertyAccessAllowed: Boolean = false) = new Typer(
    dictTyper = new KeysDictTyper(new SimpleDictRegistry(Map.empty)),
    strictMethodsChecking = false,
    staticMethodInvocationsChecking = false,
    classDefinitionSet = ClassDefinitionTestUtils.buildDefinitionForDefaultAdditionalClasses,
    evaluationContextPreparer = null,
    methodExecutionForUnknownAllowed = false,
    dynamicPropertyAccessAllowed = dynamicPropertyAccessAllowed
  )

  private def typeExpression(
      expr: String,
      variables: (String, Any)*
  )(
      implicit typer: Typer
  ): ValidatedNel[ExpressionParseError, CollectedTypingResult] = {
    val parsed        = parser.parseExpression(expr)
    val validationCtx = ValidationContext(variables.toMap.mapValuesNow(Typed.fromInstance))
    typer.typeExpression(parsed, validationCtx)
  }

  private def typeTemplate(
      expr: String,
      variables: (String, Any)*
  )(
      implicit typer: Typer
  ): ValidatedNel[ExpressionParseError, CollectedTypingResult] = {
    val parsed        = parser.parseExpression(expr, new TemplateParserContext())
    val validationCtx = ValidationContext(variables.toMap.mapValuesNow(Typed.fromInstance))
    typer.typeExpression(parsed, validationCtx)
  }

}

object TyperSpecTestData {

  object TestRecord {
    val nonPresentKey: String = "nonPresentKey"
    val testRecordExpr: String =
      "{int: 1, string: 'stringVal', boolean: true, 'null': null, nestedRecord: {nestedRecordKey: 2}}"

    val testRecordTyped: TypedObjectTypingResult = Typed.record(
      Map(
        "string"  -> TypedObjectWithValue(Typed.typedClass[String], "stringVal"),
        "null"    -> TypedNull,
        "boolean" -> TypedObjectWithValue(Typed.typedClass[Boolean], true),
        "int"     -> TypedObjectWithValue(Typed.typedClass[Int], 1),
        "nestedRecord" -> Typed.record(
          Map(
            "nestedRecordKey" -> TypedObjectWithValue(Typed.typedClass[Int], 2)
          )
        ),
      )
    )

  }

}
