package pl.touk.nussknacker.engine.lite.components.requestresponse

import akka.http.scaladsl.model.{HttpMethods, HttpRequest}
import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import io.circe.Json
import io.circe.Json.{Null, fromInt, fromString, obj}
import io.circe.syntax.EncoderOps
import org.everit.json.schema.{NumberSchema, Schema, StringSchema}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{CustomNodeError, ExpressionParserCompilationError}
import pl.touk.nussknacker.engine.api.validation.ValidationMode
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.json.JsonSchemaBuilder
import pl.touk.nussknacker.engine.lite.components.requestresponse.jsonschema.sinks.JsonRequestResponseSink
import pl.touk.nussknacker.engine.lite.util.test.RequestResponseTestScenarioRunner
import pl.touk.nussknacker.engine.lite.util.test.RequestResponseTestScenarioRunner._
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.util.output.OutputValidatorErrorsMessageFormatter
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner
import pl.touk.nussknacker.test.SpecialSpELElement.{EmptyMap, Input}
import pl.touk.nussknacker.test.{EitherValuesDetailedMessage, SpecialSpELElement, ValidatedValuesDetailedMessage}

//More tests e2e with json are available at LiteKafkaUniversalJsonFunctionalTest
class LiteRequestResponseFunctionalTest extends AnyFunSuite with Matchers with EitherValuesDetailedMessage
  with TableDrivenPropertyChecks with ValidatedValuesDetailedMessage {

  import pl.touk.nussknacker.engine.lite.components.requestresponse.jsonschema.sinks.JsonRequestResponseSink._
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

  private val schemaInt = NumberSchema.builder().requiresNumber(true).minimum(1).maximum(16).build()

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
    ObjectFieldName -> fromString(sampleStr),
    AdditionalFieldName -> fromString("str")
  )

  private val sampleSpELObjWithAdds = Map(
    ObjectFieldName -> sampleStr,
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
      //TODO: currently inline map is not properly typed here :/
      "field" -> "#input"
    )
    val scenario = ScenarioBuilder
      .requestResponse("test")
      .additionalFields(properties = Map(
        "inputSchema" -> input,
        "outputSchema" -> output
      ))
      .source("input", "request")
      .emptySink(sinkName, "response",
        (SinkRawEditorParamName -> ("false": Expression)) :: params: _*
      )

    val result = runner.runWithRequests(scenario) { _ => }

    result should matchPattern {
      case Invalid(NonEmptyList(ExpressionParserCompilationError(message, `sinkName`, Some("field"), _), Nil)) if message.startsWith("Bad expression type") =>
    }
  }

  test("should test e2e request-response flow at sink and source / handling nulls and empty json" ) {
    val testData = Table(
      ("config", "result"),
      (config(obj(), schemaObjNull, schemaObjNull), Valid(obj())),
      (config(obj(), schemaObjNull, schemaObjNull, Map(ObjectFieldName -> InputField)), validJObj(Null)),
      (config(JsonObject(Null), schemaObjNull, schemaObjNull), validJObj(Null)),
      (config(JsonObject(Null), schemaObjNull, schemaObjNull, Map(ObjectFieldName -> InputField)), validJObj(Null)),

      (config(obj(), schemaObjStr, schemaObjStr), Valid(obj())),

      (config(obj(), schemaObjUnionNullString(), schemaObjUnionNullString(), Map(ObjectFieldName -> InputField)), validJObj(Null)),
      (config(obj(), schemaObjUnionNullString(), schemaObjUnionNullString()), Valid(obj())),
      (config(JsonObject(Null), schemaObjUnionNullString(), schemaObjUnionNullString()), validJObj(Null)),
      (config(JsonObject(Null), schemaObjUnionNullString(), schemaObjUnionNullString(), Map(ObjectFieldName -> InputField)), validJObj(Null)),
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
      //Primitive integer validations
      (config(fromInt(1), schemaInt, schemaInt), Valid(fromInt(1))),
      (conf(schemaInt, 1), Valid(fromInt(1))),
    )

    forAll(testData) { (config: ScenarioConfig, expected: Validated[_, Json]) =>
      val results = runWithResults(config)
      results shouldBe expected
    }
  }

  test("should test e2e request-response flow at sink and source / handling objects.." ) {
    val testData = Table(
      ("config", "result"),
      (config(JsonObject(fromString(sampleStr)), schemaObjStr, schemaObjStr), validJObj(fromString(sampleStr))),
      (conf(schemaObjStr, SpELObject(sampleStr)), validJObj(fromString(sampleStr))),

      //Union testing
      (config(JsonObject(fromString(sampleStr)), schemaObjUnionNullString(), schemaObjUnionNullString()), validJObj(fromString(sampleStr))),
      (config(JsonObject(fromString(sampleStr)), schemaObjStr, schemaObjUnionNullString()), validJObj(fromString(sampleStr))),
      (config(JsonObject(Null), schemaObjNull, schemaObjUnionNullString()), validJObj(Null)),
      (config(JsonObject(fromString(sampleStr)), schemaObjUnionNullString(), schemaObjStr), validJObj(fromString(sampleStr))),
      (conf(schemaObjUnionNullString(), SpELObject(sampleStr)), validJObj(fromString(sampleStr))),

      //Testing additional properties
      (config(sampleObjWithAdds, schemaObjUnionNullString(true), schemaObjUnionNullString(true)), Valid(sampleObjWithAdds)),
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
      //Errors at source
      (config(sampleObjWithAdds, schemaObjUnionNullString(), schemaObjUnionNullString(true)), "#: extraneous key [field2] is not permitted"),

      //Errors at sink
      (config(sampleObjWithAdds, schemaObjString(true), schemaObjStr), s"Not expected field with name: field2 for schema: $schemaObjStr."),
      (config(obj(), schemaObjString(), schemaObjString(), Map(ObjectFieldName -> InputField)), s"Not expected type: null for field: 'field' with schema: $schemaStr."),
      (config(sampleObjWithAdds, schemaObjUnionNullString(true), schemaObjUnionNullString()), s"Not expected field with name: field2 for schema: ${schemaObjUnionNullString()}."),
      (config(fromInt(Int.MaxValue), schemaInt, schemaInt), s"#: ${Int.MaxValue} is not less or equal to 16"),
      (conf(schemaInt, Int.MaxValue), s"#: ${Int.MaxValue} is not less or equal to 16"),
    )

    forAll(testData) { (config: ScenarioConfig, expected: String) =>
      val scenario: CanonicalProcess = createScenario(config)
      val result = runner.runWithRequests(scenario) { invoker =>
        val response = invoker(HttpRequest(HttpMethods.POST, entity = config.input.asJson.spaces2))
        response.leftValue.head.throwable.getMessage
      }.validValue

      result shouldBe expected
    }
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
      .additionalFields(properties = Map(
        "inputSchema" -> config.sourceSchema.toString,
        "outputSchema" -> config.outputSchema.toString
      ))
      .source("input", "request")
      .emptySink(sinkName, "response",
        SinkRawEditorParamName -> "true",
        SinkValidationModeParameterName -> s"'${config.validationModeName}'",
        SinkRawValueParamName -> config.sinkDefinition
      )

  case class ScenarioConfig(input: Json, sourceSchema: Schema, outputSchema: Schema, sinkDefinition: String, validationMode: Option[ValidationMode]) {
    lazy val validationModeName: String = validationMode.map(_.name).getOrElse(ValidationMode.strict.name)
  }

  //Special config for SpEL as output, on input we pass simple null
  private def conf(outputSchema: Schema, output: Any = Input, validationMode: Option[ValidationMode] = None): ScenarioConfig =
    config(Null, schemaNull, outputSchema, output, validationMode)

  private def config(input: Json, inputSchema: Schema, outputSchema: Schema, output: Any = Input, validationMode: Option[ValidationMode] = None): ScenarioConfig = {
    val sinkDefinition = output match {
      case element: SpecialSpELElement if List(EmptyMap, Input).contains(element) => element
      case any => any
    }

    ScenarioConfig(input, inputSchema, outputSchema, sinkDefinition.toSpELLiteral, validationMode)
  }

  private def validJObj(value: Json): Valid[Json] = Valid(JsonObject(value))

  protected def invalidTypes(typeErrors: String*): Invalid[NonEmptyList[CustomNodeError]] =
    invalid(typeErrors.toList, Nil, Nil)

  protected def invalid(typeFieldErrors: List[String], missingFieldsError: List[String], redundantFieldsError: List[String]): Invalid[NonEmptyList[CustomNodeError]] = {
    val finalMessage = OutputValidatorErrorsMessageFormatter.makeMessage(typeFieldErrors, missingFieldsError, redundantFieldsError)
    Invalid(NonEmptyList.one(CustomNodeError(sinkName, finalMessage, Some(JsonRequestResponseSink.SinkRawValueParamName))))
  }

  object JsonObject {
    def apply(value: Json): Json = obj(ObjectFieldName -> value)
  }

  object SpELObject {
    def apply(value: String): Map[String, Any] = Map(ObjectFieldName -> fromString(value))
  }
}
