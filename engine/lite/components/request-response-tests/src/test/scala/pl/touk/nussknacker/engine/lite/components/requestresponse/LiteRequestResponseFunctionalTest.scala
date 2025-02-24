package pl.touk.nussknacker.engine.lite.components.requestresponse

import org.apache.pekko.http.scaladsl.model.{HttpMethods, HttpRequest}
import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import io.circe.Json
import io.circe.Json.{Null, arr, fromInt, fromString, obj}
import io.circe.syntax.EncoderOps
import org.everit.json.schema.{NumberSchema, Schema, StringSchema}
import org.scalatest.Inside.inside
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{
  CustomNodeError,
  ExpressionParserCompilationError
}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.validation.ValidationMode
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.json.JsonSchemaBuilder
import pl.touk.nussknacker.engine.lite.components.requestresponse.jsonschema.sinks.JsonRequestResponseSink
import pl.touk.nussknacker.engine.lite.util.test.RequestResponseTestScenarioRunner
import pl.touk.nussknacker.engine.lite.util.test.RequestResponseTestScenarioRunner._
import pl.touk.nussknacker.engine.spel.SpelExtension._
import pl.touk.nussknacker.engine.util.output.OutputValidatorErrorsMessageFormatter
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner
import pl.touk.nussknacker.test.SpecialSpELElement.{EmptyMap, Input}
import pl.touk.nussknacker.test.{EitherValuesDetailedMessage, SpecialSpELElement, ValidatedValuesDetailedMessage}

//More tests e2e with json are available at LiteKafkaUniversalJsonFunctionalTest
class LiteRequestResponseFunctionalTest
    extends AnyFunSuite
    with Matchers
    with EitherValuesDetailedMessage
    with TableDrivenPropertyChecks
    with ValidatedValuesDetailedMessage {

  import pl.touk.nussknacker.engine.lite.components.requestresponse.jsonschema.sinks.JsonRequestResponseSink._
  import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
  import pl.touk.nussknacker.test.LiteralSpELImplicits._

  private val runner: RequestResponseTestScenarioRunner = TestScenarioRunner
    .requestResponseBased()
    .build()

  private val ObjectFieldName = "field"

  private val AdditionalFieldName = "field2"

  private val InputField = SpecialSpELElement(s"#input.$ObjectFieldName")

  private val sinkName = "response"

  private val schemaNull = JsonSchemaBuilder.parseSchema("""{"type": "null"}""")

  private val schemaStr = StringSchema.builder().build()

  private val schemaNumber = NumberSchema.builder().requiresNumber(true).build()

  private val schemaIntInRange = NumberSchema.builder().requiresNumber(true).minimum(1).maximum(16).build()

  private val schemaObjNull = JsonSchemaBuilder.parseSchema(
    s"""{"type":"object","properties": {"$ObjectFieldName": $schemaNull}, "additionalProperties": false}"""
  )

  private val schemaObjStr = schemaObjString()

  private def schemaObjString(additionalProperties: Boolean = false) = JsonSchemaBuilder.parseSchema(
    s"""{"type":"object","properties": {"$ObjectFieldName": {"type": "string"}}, "additionalProperties": $additionalProperties}"""
  )

  private def schemaObjUnionNullString(additionalProperties: Boolean = false) = JsonSchemaBuilder.parseSchema(
    s"""{"type":"object","properties": {"$ObjectFieldName": {"type": ["null", "string"]}}, "additionalProperties": $additionalProperties}"""
  )

  private val sampleStr = "sample"

  private val sampleObjWithAdds: Json = obj(
    ObjectFieldName     -> fromString(sampleStr),
    AdditionalFieldName -> fromString("str")
  )

  private val sampleSpELObjWithAdds = Map(
    ObjectFieldName     -> sampleStr,
    AdditionalFieldName -> "str"
  )

  test("should handle nested non-raw mode") {
    val input =
      """
        |{
        | "type": "object",
        | "additionalProperties": {
        |    "type": "object",
        |    "properties": {
        |      "additionalField": { "type": "string" }
        |     }
        |   }
        |}
        |""".stripMargin
    val output =
      """
        |{
        |  "type": "object",
        |  "properties": {
        |    "field": {
        |      "type": "object",
        |      "additionalProperties": {
        |        "type": "object",
        |        "properties": {
        |          "additionalField": { "type": "number" }
        |        }
        |      }
        |    }
        |  }
        |}
        |""".stripMargin
    val params: List[(String, Expression)] = List(
      // TODO: currently inline map is not properly typed here :/
      "field" -> "#input".spel
    )
    val scenario = ScenarioBuilder
      .requestResponse("test")
      .additionalFields(properties =
        Map(
          "inputSchema"  -> input,
          "outputSchema" -> output
        )
      )
      .source("input", "request")
      .emptySink(sinkName, "response", (SinkRawEditorParamName.value -> ("false".spel: Expression)) :: params: _*)

    val result = runner.runWithRequests(scenario) { _ => }

    result should matchPattern {
      case Invalid(
            NonEmptyList(
              ExpressionParserCompilationError(message, `sinkName`, Some(ParameterName("field")), _, None),
              Nil
            )
          ) if message.startsWith("Bad expression type") =>
    }
  }

  test("should handle refs in sink") {
    val refSchema = JsonSchemaBuilder.parseSchema(
      """{
        |  "type": "object",
        |  "properties": {
        |    "item": {
        |      "$ref": "#/defs/RefSchema"
        |    }
        |  },
        |  "defs": {
        |    "RefSchema": {
        |      "type": "object",
        |      "properties": {
        |        "value": { "type": "string" }
        |      }
        |    }
        |  }
        |}""".stripMargin
    )
    val input    = fromString("")
    val scConfig = config(input, schemaStr, refSchema, SpecialSpELElement("{item: {value: #input}}"))
    runWithResults(scConfig) shouldBe Valid(obj("item" -> obj("value" -> input)))

    val invalidConfig = config(Null, schemaStr, refSchema, SpecialSpELElement("{item: {value: 11}}"))
    inside(runWithResults(invalidConfig)) {
      case Invalid(NonEmptyList(CustomNodeError(_, message, Some(SinkRawValueParamName)), Nil)) =>
        message should include("Provided value does not match")
    }

  }

  test("should handle empty object in non-raw mode") {
    val input  = "{}"
    val output = """{"type":"object", "additionalProperties": false}"""
    val scenario = ScenarioBuilder
      .requestResponse("test")
      .additionalFields(properties =
        Map(
          "inputSchema"  -> input,
          "outputSchema" -> output
        )
      )
      .source("input", "request")
      .emptySink(sinkName, "response", SinkRawEditorParamName.value -> "false".spel)

    val result = runner.runWithRequests(scenario) { invoker =>
      invoker(HttpRequest(HttpMethods.POST, entity = "{}}")).rightValue
    }
    result shouldBe Valid(Json.obj())
  }

  test("handles recursive schema encode/decode") {
    val schema = JsonSchemaBuilder.parseSchema(
      """{
        |  "type": "object",
        |  "properties": {
        |    "items": {
        |      "$ref": "#/defs/RecursiveList"
        |    }
        |  },
        |  "defs": {
        |    "RecursiveList": {
        |      "type": "object",
        |      "properties": {
        |        "value": { "type": "string" },
        |        "next": { "$ref": "#/defs/RecursiveList" }
        |      }
        |    }
        |  }
        |}""".stripMargin
    )
    // TODO: handle ref schemas on output...
    val scenarioConfig = config(
      obj("items" -> obj("value" -> fromString("1"), "next" -> obj("value" -> fromString("2")))),
      schema,
      schemaStr,
      output = SpecialSpELElement("#input.items.value")
    )
    runWithResults(scenarioConfig) shouldBe Valid(fromString("1"))
  }

  test("should test e2e request-response flow at sink and source / handling nulls and empty json") {
    val testData = Table(
      ("config", "result"),
      (config(obj(), schemaObjNull, schemaObjNull), Valid(obj())),
      (config(obj(), schemaObjNull, schemaObjNull, Map(ObjectFieldName -> InputField)), validJObj(Null)),
      (config(JsonObject(Null), schemaObjNull, schemaObjNull), validJObj(Null)),
      (config(JsonObject(Null), schemaObjNull, schemaObjNull, Map(ObjectFieldName -> InputField)), validJObj(Null)),
      (config(obj(), schemaObjStr, schemaObjStr), Valid(obj())),
      (
        config(obj(), schemaObjUnionNullString(), schemaObjUnionNullString(), Map(ObjectFieldName -> InputField)),
        validJObj(Null)
      ),
      (config(obj(), schemaObjUnionNullString(), schemaObjUnionNullString()), Valid(obj())),
      (config(JsonObject(Null), schemaObjUnionNullString(), schemaObjUnionNullString()), validJObj(Null)),
      (
        config(
          JsonObject(Null),
          schemaObjUnionNullString(),
          schemaObjUnionNullString(),
          Map(ObjectFieldName -> InputField)
        ),
        validJObj(Null)
      ),
    )

    forAll(testData) { (config: ScenarioConfig, expected: Validated[_, Json]) =>
      val results = runWithResults(config)
      results shouldBe expected
    }
  }

  // TODO: add more tests for primitives, logical tests, unions and so on, add random tests - like in LiteKafkaUniversalJsonFunctionalTest
  test("should test e2e request-response flow at sink and source / handling primitives..") {

    val testData = Table(
      ("config", "result"),
      // Primitive integer validations
      (config(fromInt(1), schemaIntInRange, schemaIntInRange), Valid(fromInt(1))),
      (conf(schemaIntInRange, 1), Valid(fromInt(1))),
      (conf(schemaIntInRange, 100), invalidRange("actual value: '100' should be between 1 and 16")),
    )

    forAll(testData) { (config: ScenarioConfig, expected: Validated[_, Json]) =>
      val results = runWithResults(config)
      results shouldBe expected
    }
  }

  test("should test e2e request-response flow at sink and source / handling objects..") {
    val testData = Table(
      ("config", "result"),
      (config(JsonObject(fromString(sampleStr)), schemaObjStr, schemaObjStr), validJObj(fromString(sampleStr))),
      (conf(schemaObjStr, SpELObject(sampleStr)), validJObj(fromString(sampleStr))),

      // Union testing
      (
        config(JsonObject(fromString(sampleStr)), schemaObjUnionNullString(), schemaObjUnionNullString()),
        validJObj(fromString(sampleStr))
      ),
      (
        config(JsonObject(fromString(sampleStr)), schemaObjStr, schemaObjUnionNullString()),
        validJObj(fromString(sampleStr))
      ),
      (config(JsonObject(Null), schemaObjNull, schemaObjUnionNullString()), validJObj(Null)),
      (
        config(JsonObject(fromString(sampleStr)), schemaObjUnionNullString(), schemaObjStr),
        validJObj(fromString(sampleStr))
      ),
      (conf(schemaObjUnionNullString(), SpELObject(sampleStr)), validJObj(fromString(sampleStr))),

      // Testing additional properties
      (
        config(sampleObjWithAdds, schemaObjUnionNullString(true), schemaObjUnionNullString(true)),
        Valid(sampleObjWithAdds)
      ),
      (conf(schemaObjUnionNullString(), sampleSpELObjWithAdds), invalid(Nil, Nil, List(AdditionalFieldName))),
      (conf(schemaObjUnionNullString(true), sampleSpELObjWithAdds), Valid(sampleObjWithAdds)),
      (config(sampleObjWithAdds, schemaObjString(true), schemaObjUnionNullString(true)), Valid(sampleObjWithAdds)),
      (conf(schemaObjStr, sampleSpELObjWithAdds), invalid(Nil, Nil, List(AdditionalFieldName))),
      (conf(schemaObjString(true), sampleSpELObjWithAdds), Valid(sampleObjWithAdds)),
    )

    forAll(testData) { (config: ScenarioConfig, expected: Validated[_, Json]) =>
      val results = runWithResults(config)
      results shouldBe expected
    }
  }

  test("should catch runtime errors") {
    val testData = Table(
      ("config", "result"),
      // Errors at source
      (
        config(sampleObjWithAdds, schemaObjUnionNullString(), schemaObjUnionNullString(true)),
        "#: extraneous key [field2] is not permitted"
      ),
      (config(fromInt(Int.MaxValue), schemaIntInRange, schemaNumber), s"#: ${Int.MaxValue} is not less or equal to 16"),
      // Errors at sink
      (config(fromInt(Int.MaxValue), schemaNumber, schemaIntInRange), s"#: ${Int.MaxValue} is not less or equal to 16"),
    )

    forAll(testData) { (config: ScenarioConfig, expected: String) =>
      val scenario: CanonicalProcess = createScenario(config)
      val result = runner
        .runWithRequests(scenario) { invoker =>
          val response = invoker(HttpRequest(HttpMethods.POST, entity = config.input.asJson.spaces2))
          response.leftValue.head.throwable.getMessage
        }
        .validValue

      result shouldBe expected
    }
  }

  test("validate pattern properties on sink in editor mode") {
    def invalidTypeInEditorMode(fieldName: String, error: String): Invalid[NonEmptyList[CustomNodeError]] = {
      val finalMessage = OutputValidatorErrorsMessageFormatter.makeMessage(List(error), Nil, Nil, Nil)
      Invalid(NonEmptyList.one(CustomNodeError(sinkName, finalMessage, Some(ParameterName(fieldName)))))
    }
    val objectWithNestedPatternPropertiesSchema = JsonSchemaBuilder.parseSchema("""{
        |  "type": "object",
        |  "properties": {
        |    "field": {
        |      "type": "object",
        |      "patternProperties": {
        |        "_int$": { "type": "integer" }
        |      }
        |    }
        |  }
        |}""".stripMargin)

    val testData = Table(
      ("sinkSchema", "sinkFields", "result"),
      (
        objectWithNestedPatternPropertiesSchema,
        Map("field" -> "{'foo_int': 1}"),
        Valid(obj("field" -> obj("foo_int" -> fromInt(1))))
      ),
      (
        objectWithNestedPatternPropertiesSchema,
        Map("field" -> "{'foo_int': '1'}"),
        invalidTypeInEditorMode("field", "actual: 'String(1)' expected: 'Long'")
      ),
    )

    forAll(testData) { (sinkSchema: Schema, sinkFields: Map[String, String], expected: Validated[_, Json]) =>
      val cfg = config(sampleObjWithAdds, schemaObjString(true), sinkSchema)
      val sinkParams = (Map(
        SinkRawEditorParamName.value          -> "false",
        SinkValidationModeParameterName.value -> s"'${cfg.validationModeName}'",
      ) ++ sinkFields).mapValuesNow(Expression.spel)
      val scenario = ScenarioBuilder
        .requestResponse("test")
        .additionalFields(properties =
          Map(
            "inputSchema"  -> cfg.sourceSchema.toString,
            "outputSchema" -> cfg.outputSchema.toString
          )
        )
        .source("input", "request")
        .emptySink(sinkName, "response", sinkParams.toList: _*)
      val result = runner.runWithRequests(scenario) { invoker =>
        invoker(HttpRequest(HttpMethods.POST, entity = cfg.input.asJson.spaces2)).rightValue
      }

      result shouldBe expected
    }
  }

  test("validate nested json schema and match to it fitting expression") {
    val nestedSchema = JsonSchemaBuilder.parseSchema("""{
            |    "properties": {
            |        "books": {
            |            "items": {
            |                "properties": {
            |                    "title": {
            |                        "type": "string"
            |                    },
            |                    "additionalInfo": {
            |                        "properties": {
            |                            "coverColor": {
            |                                "type": "string"
            |                            }
            |                        },
            |                        "type": "object"
            |                    }
            |                },
            |                "type": "object"
            |            },
            |            "type": "array"
            |        }
            |    },
            |    "type": "object"
            |}""".stripMargin)

    val sinkFields = Map(
      "books" ->
        """
              |{
              |   {
              |       "title": "Low-code with NU",
              |       "additionalInfo": {
              |            "coverColor": "Red"
              |        }
              |   }
              |}
            |""".stripMargin
    )
    val expectedResult = Valid(
      obj(
        "books" ->
          arr(
            obj("title" -> fromString("Low-code with NU"), "additionalInfo" -> obj("coverColor" -> fromString("Red")))
          )
      )
    )

    val cfg = config(sampleObjWithAdds, schemaObjString(true), nestedSchema)
    val sinkParams = (Map(
      SinkRawEditorParamName.value          -> "false",
      SinkValidationModeParameterName.value -> s"'${cfg.validationModeName}'",
    ) ++ sinkFields).mapValuesNow(Expression.spel)
    val scenario = ScenarioBuilder
      .requestResponse("test")
      .additionalFields(properties =
        Map(
          "inputSchema"  -> cfg.sourceSchema.toString,
          "outputSchema" -> cfg.outputSchema.toString
        )
      )
      .source("input", "request")
      .emptySink(sinkName, "response", sinkParams.toList: _*)
    val result = runner.runWithRequests(scenario) { invoker =>
      invoker(HttpRequest(HttpMethods.POST, entity = cfg.input.asJson.spaces2)).rightValue
    }

    result shouldBe expectedResult

  }

  private def runWithResults(config: ScenarioConfig): ValidatedNel[ProcessCompilationError, Json] = {
    val scenario: CanonicalProcess = createScenario(config)
    val result = runner.runWithRequests(scenario) { invoker =>
      invoker(HttpRequest(HttpMethods.POST, entity = config.input.asJson.spaces2)).rightValue
    }

    result
  }

  private def createScenario(config: ScenarioConfig): CanonicalProcess =
    ScenarioBuilder
      .requestResponse("test")
      .additionalFields(properties =
        Map(
          "inputSchema"  -> config.sourceSchema.toString,
          "outputSchema" -> config.outputSchema.toString
        )
      )
      .source("input", "request")
      .emptySink(
        sinkName,
        "response",
        SinkRawEditorParamName.value          -> "true".spel,
        SinkValidationModeParameterName.value -> s"'${config.validationModeName}'".spel,
        SinkRawValueParamName.value           -> config.sinkDefinition.spel
      )

  case class ScenarioConfig(
      input: Json,
      sourceSchema: Schema,
      outputSchema: Schema,
      sinkDefinition: String,
      validationMode: Option[ValidationMode]
  ) {
    lazy val validationModeName: String = validationMode.map(_.name).getOrElse(ValidationMode.strict.name)
  }

  // Special config for SpEL as output, on input we pass simple null
  private def conf(
      outputSchema: Schema,
      output: Any = Input,
      validationMode: Option[ValidationMode] = None
  ): ScenarioConfig =
    config(Null, schemaNull, outputSchema, output, validationMode)

  private def config(
      input: Json,
      inputSchema: Schema,
      outputSchema: Schema,
      output: Any = Input,
      validationMode: Option[ValidationMode] = None
  ): ScenarioConfig = {
    val sinkDefinition = output match {
      case element: SpecialSpELElement if List(EmptyMap, Input).contains(element) => element
      case any                                                                    => any
    }

    ScenarioConfig(input, inputSchema, outputSchema, sinkDefinition.toSpELLiteral, validationMode)
  }

  private def validJObj(value: Json): Valid[Json] = Valid(JsonObject(value))

  protected def invalidTypes(typeErrors: String*): Invalid[NonEmptyList[CustomNodeError]] =
    invalid(typeErrors.toList, Nil, Nil)

  protected def invalidRange(rangeErrors: String*): Invalid[NonEmptyList[CustomNodeError]] =
    invalid(Nil, Nil, Nil, rangeErrors.toList)

  protected def invalid(
      typeFieldErrors: List[String],
      missingFieldsError: List[String],
      redundantFieldsError: List[String]
  ): Invalid[NonEmptyList[CustomNodeError]] =
    invalid(typeFieldErrors, missingFieldsError, redundantFieldsError, Nil)

  protected def invalid(
      typeFieldErrors: List[String],
      missingFieldsError: List[String],
      redundantFieldsError: List[String],
      rangeTypeError: List[String]
  ): Invalid[NonEmptyList[CustomNodeError]] = {
    val finalMessage = OutputValidatorErrorsMessageFormatter.makeMessage(
      typeFieldErrors,
      missingFieldsError,
      redundantFieldsError,
      rangeTypeError
    )
    Invalid(
      NonEmptyList.one(CustomNodeError(sinkName, finalMessage, Some(JsonRequestResponseSink.SinkRawValueParamName)))
    )
  }

  object JsonObject {
    def apply(value: Json): Json = obj(ObjectFieldName -> value)
  }

  object SpELObject {
    def apply(value: String): Map[String, Any] = Map(ObjectFieldName -> fromString(value))
  }

}
