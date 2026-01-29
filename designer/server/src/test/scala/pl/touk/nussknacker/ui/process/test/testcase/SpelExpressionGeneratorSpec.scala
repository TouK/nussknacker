package pl.touk.nussknacker.ui.process.test.testcase

import org.scalatest.OptionValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.typed.typing._
import pl.touk.nussknacker.engine.compile.ExpressionCompiler
import pl.touk.nussknacker.engine.definition.model.ModelDefinitionWithClasses
import pl.touk.nussknacker.engine.dict.SimpleDictRegistry
import pl.touk.nussknacker.engine.expression.ExpressionEvaluator
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.testing.ModelDefinitionBuilder
import pl.touk.nussknacker.engine.variables.GlobalVariablesPreparer
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage

class SpelExpressionGeneratorSpec
    extends AnyFunSuite
    with Matchers
    with OptionValues
    with ValidatedValuesDetailedMessage {

  private implicit val nodeId: NodeId = NodeId("test-node")

  private val modelDefinitionWithClasses = ModelDefinitionWithClasses(
    ModelDefinitionBuilder.empty.build
  )

  private val globalVariablesPreparer =
    GlobalVariablesPreparer.apply(modelDefinitionWithClasses.modelDefinition.expressionConfig)

  private val expressionCompiler = ExpressionCompiler.withOptimization(
    getClass.getClassLoader,
    new SimpleDictRegistry(Map.empty),
    modelDefinitionWithClasses.modelDefinition.expressionConfig,
    modelDefinitionWithClasses.classDefinitions,
    ExpressionEvaluator.unOptimizedEvaluator(globalVariablesPreparer)
  )

  test("should generate expression for String") {
    val expectedType = Typed[String]
    val result       = SpelExpressionGenerator.generate(expectedType)

    result shouldBe Some("'string'")
    verifyExpressionCompilesToExpectedType(result.value, expectedType)
  }

  test("should generate expression for Integer") {
    val expectedType = Typed[Integer]
    val result       = SpelExpressionGenerator.generate(expectedType)

    result shouldBe Some("42")
    verifyExpressionCompilesToExpectedType(result.value, expectedType)
  }

  test("should generate expression for Long") {
    val expectedType = Typed[java.lang.Long]
    val result       = SpelExpressionGenerator.generate(expectedType)

    result shouldBe Some("42")
    verifyExpressionCompilesToExpectedType(result.value, expectedType)
  }

  test("should generate expression for Double") {
    val expectedType = Typed[java.lang.Double]
    val result       = SpelExpressionGenerator.generate(expectedType)

    result shouldBe Some("42.0")
    verifyExpressionCompilesToExpectedType(result.value, expectedType)
  }

  test("should generate expression for Boolean") {
    val expectedType = Typed[java.lang.Boolean]
    val result       = SpelExpressionGenerator.generate(expectedType)

    result shouldBe Some("true")
    verifyExpressionCompilesToExpectedType(result.value, expectedType)
  }

  test("should generate expression for record type") {
    val expectedType = Typed.record(
      Map(
        "name" -> Typed[String],
        "age"  -> Typed[Integer]
      )
    )

    val result = SpelExpressionGenerator.generate(expectedType)

    result shouldBe Some("{name: 'string', age: 42}")
    verifyExpressionCompilesToExpectedType(result.value, expectedType)
  }

  test("should generate expression for nested record") {
    val expectedType = Typed.record(
      Map(
        "user" -> Typed.record(
          Map(
            "name"   -> Typed[String],
            "active" -> Typed[java.lang.Boolean]
          )
        ),
        "count" -> Typed[Integer]
      )
    )

    val result = SpelExpressionGenerator.generate(expectedType)

    result shouldBe Some("{user: {name: 'string', active: true}, count: 42}")
    verifyExpressionCompilesToExpectedType(result.value, expectedType)
  }

  test("should generate expression for List") {
    val expectedType = Typed.genericTypeClass(classOf[java.util.List[_]], List(Typed[String]))

    val result = SpelExpressionGenerator.generate(expectedType)

    result shouldBe Some("{'string'}")
    verifyExpressionCompilesToExpectedType(result.value, expectedType)
  }

  test("should generate expression for Map") {
    val expectedType = Typed.genericTypeClass(
      classOf[java.util.Map[_, _]],
      List(Typed[String], Typed[Integer])
    )

    val result = SpelExpressionGenerator.generate(expectedType)

    result shouldBe Some("{'key': 42}")
    verifyExpressionCompilesToExpectedType(result.value, expectedType)
  }

  test("should return null for Unknown type") {
    val expectedType = Unknown
    val result       = SpelExpressionGenerator.generate(expectedType)

    result shouldBe Some("null")
    verifyExpressionCompilesToExpectedType(result.value, expectedType)
  }

  test("should generate expression for complex HTTP request/response record") {
    val headerRecordType = Typed.record(
      Map(
        "name"  -> Typed[String],
        "value" -> Typed[String]
      )
    )
    val requestType = Typed.record(
      Map(
        "body"    -> Typed.record(Map.empty[String, TypingResult]),
        "headers" -> Typed.genericTypeClass(classOf[java.util.List[_]], List(headerRecordType)),
        "method"  -> Typed[String],
        "url"     -> Typed[String]
      )
    )
    val responseType = Typed.record(
      Map(
        "body"       -> Unknown,
        "headers"    -> Typed.genericTypeClass(classOf[java.util.List[_]], List(headerRecordType)),
        "statusCode" -> Typed[Integer],
        "statusText" -> Typed[String]
      )
    )
    val expectedType = Typed.record(
      Map(
        "request"  -> requestType,
        "response" -> responseType
      )
    )

    val result = SpelExpressionGenerator.generate(expectedType)

    result shouldBe Some(
      "{request: {body: {}, headers: {{name: 'string', value: 'string'}}, method: 'string', url: 'string'}, response: {body: null, headers: {{name: 'string', value: 'string'}}, statusCode: 42, statusText: 'string'}}"
    )
    verifyExpressionCompilesToExpectedType(result.value, expectedType)
  }

  private def verifyExpressionCompilesToExpectedType(generatedExpression: String, expectedType: TypingResult): Unit = {
    val expr              = Expression.spel(generatedExpression)
    val validationContext = ValidationContext.empty
    val compilationResult = expressionCompiler.compile(expr, None, validationContext, expectedType)

    withClue(s"Expression '$generatedExpression' should compile successfully to type '${expectedType.display}'") {
      compilationResult.validValue
    }
  }

}
