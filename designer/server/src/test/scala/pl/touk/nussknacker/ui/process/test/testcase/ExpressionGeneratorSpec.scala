package pl.touk.nussknacker.ui.process.test.testcase

import org.scalatest.OptionValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.typed.typing._
import pl.touk.nussknacker.engine.compile.ExpressionCompiler
import pl.touk.nussknacker.engine.definition.model.ModelDefinitionWithClasses
import pl.touk.nussknacker.engine.dict.SimpleDictRegistry
import pl.touk.nussknacker.engine.expression.ExpressionEvaluator
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.expression.Expression.Language.JsonTemplate
import pl.touk.nussknacker.engine.testing.ModelDefinitionBuilder
import pl.touk.nussknacker.engine.variables.GlobalVariablesPreparer
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage

class ExpressionGeneratorSpec
    extends AnyFunSuite
    with Matchers
    with OptionValues
    with TableDrivenPropertyChecks
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

  test("should generate expressions for various types") {
    val testCases = Table(
      ("description", "typingResult", "expectedExpression"),
      ("unknown", Unknown, "null"),
      ("string", Typed[String], "\"\""),
      ("byte", Typed[java.lang.Byte], "0"),
      ("short", Typed[java.lang.Short], "0"),
      ("integer", Typed[java.lang.Integer], "0"),
      ("long", Typed[java.lang.Long], "0"),
      ("float", Typed[java.lang.Float], "0.0"),
      ("double", Typed[java.lang.Double], "0.0"),
      ("boolean", Typed[java.lang.Boolean], "true"),
      ("big decimal", Typed[java.math.BigDecimal], "0.0"),
      ("enum", Typed.typedClass[java.time.Month], "#{ T(java.time.Month).JANUARY }"),
      ("instant", Typed[java.time.Instant], "\"1900-01-01T00:00:00Z\""),
      ("local date time", Typed[java.time.LocalDateTime], "\"1900-01-01T00:00:00\""),
      ("local date", Typed[java.time.LocalDate], "\"1900-01-01\""),
      ("local time", Typed[java.time.LocalTime], "\"00:00:00\""),
      ("UUID", Typed[java.util.UUID], "\"00000000-0000-0000-0000-000000000000\""),
      (
        "string with value",
        TypedObjectWithValue(Typed.typedClass[String], "Hello World!"),
        "\"Hello World!\""
      ),
      (
        "string with escaped characters",
        TypedObjectWithValue(Typed.typedClass[String], "Line1\nLine2\t\"quoted\"\\backslash"),
        "\"Line1\\nLine2\\t\\\"quoted\\\"\\\\backslash\""
      ),
      ("boolean with value true", TypedObjectWithValue(Typed.typedClass[java.lang.Boolean], true), "true"),
      ("boolean with value false", TypedObjectWithValue(Typed.typedClass[java.lang.Boolean], false), "false"),
      ("integer with value", TypedObjectWithValue(Typed.typedClass[Integer], 42), "42"),
      ("long with value", TypedObjectWithValue(Typed.typedClass[java.lang.Long], 9876543210L), "9876543210"),
      ("float with value", TypedObjectWithValue(Typed.typedClass[java.lang.Float], 3.14f), "3.14"),
      ("double with value", TypedObjectWithValue(Typed.typedClass[java.lang.Double], 2.71828), "2.71828"),
      (
        "instant with value",
        TypedObjectWithValue(
          Typed.typedClass[java.time.Instant],
          java.time.Instant.parse("2026-02-04T10:30:45Z")
        ),
        "\"2026-02-04T10:30:45Z\""
      ),
      (
        "local date time with value",
        TypedObjectWithValue(
          Typed.typedClass[java.time.LocalDateTime],
          java.time.LocalDateTime.parse("2026-02-04T10:30:45")
        ),
        "\"2026-02-04T10:30:45\""
      ),
      (
        "local date with value",
        TypedObjectWithValue(
          Typed.typedClass[java.time.LocalDate],
          java.time.LocalDate.parse("2026-02-04")
        ),
        "\"2026-02-04\""
      ),
      (
        "local time with value",
        TypedObjectWithValue(
          Typed.typedClass[java.time.LocalTime],
          java.time.LocalTime.parse("10:30:45")
        ),
        "\"10:30:45\""
      ),
      (
        "UUID with value",
        TypedObjectWithValue(
          Typed.typedClass[java.util.UUID],
          java.util.UUID.fromString("a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d")
        ),
        "\"a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d\""
      ),
      ("empty record", Typed.record(Map.empty[String, TypingResult]), "{}"),
      (
        "simple record",
        Typed.record(Map("first name" -> Typed[String], "age" -> Typed[Integer])),
        """{
          |  "first name": "",
          |  "age": 0
          |}""".stripMargin
      ),
      (
        "nested record",
        Typed.record(
          Map(
            "user"  -> Typed.record(Map("name" -> Typed[String], "active" -> Typed[java.lang.Boolean])),
            "count" -> Typed[Integer]
          )
        ),
        """{
          |  "user": {
          |    "name": "",
          |    "active": true
          |  },
          |  "count": 0
          |}""".stripMargin
      ),
      ("list", Typed.genericTypeClass(classOf[java.util.List[_]], List(Typed[String])), "[\"\"]"),
      (
        "list with zero elements",
        TypedObjectWithValue(
          Typed.genericTypeClass(classOf[java.util.List[_]], List(Typed[String])),
          java.util.Arrays.asList[String]()
        ),
        "[]"
      ),
      (
        "list with two elements",
        TypedObjectWithValue(
          Typed.genericTypeClass(classOf[java.util.List[_]], List(Typed[Integer])),
          java.util.Arrays.asList(10, 20)
        ),
        "[10, 20]"
      ),
      (
        "HTTP request/response record",
        httpRequestResponseType,
        """{
          |  "request": {
          |    "body": {},
          |    "headers": [{
          |      "name": "",
          |      "value": ""
          |    }],
          |    "method": "",
          |    "url": ""
          |  },
          |  "response": {
          |    "body": null,
          |    "headers": [{
          |      "name": "",
          |      "value": ""
          |    }],
          |    "statusCode": 0,
          |    "statusText": ""
          |  }
          |}""".stripMargin
      )
    )

    forAll(testCases) { (description, typingResult, expectedExpression) =>
      withClue(s"Test case: $description") {
        val result = ExpressionGenerator.generate(typingResult)

        result.value.expression shouldBe expectedExpression
        result.value.language shouldBe JsonTemplate
        verifyExpressionCompilesToExpectedType(result.value, typingResult)
      }
    }
  }

  private def httpRequestResponseType: TypedObjectTypingResult = {
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
    Typed.record(Map("request" -> requestType, "response" -> responseType))
  }

  private def verifyExpressionCompilesToExpectedType(
      generatedExpression: Expression,
      expectedType: TypingResult
  ): Unit = {
    val validationContext = ValidationContext.empty
    val compilationResult = expressionCompiler.compile(generatedExpression, None, validationContext, expectedType)

    withClue(s"Expression '$generatedExpression' should compile successfully to type '${expectedType.display}'") {
      compilationResult.validValue
    }
  }

}
