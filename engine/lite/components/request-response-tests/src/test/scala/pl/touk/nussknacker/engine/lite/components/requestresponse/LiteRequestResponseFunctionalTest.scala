package pl.touk.nussknacker.engine.lite.components.requestresponse

import akka.http.scaladsl.model.{HttpMethods, HttpRequest}
import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import io.circe.Json
import io.circe.Json.{Null, fromString, obj}
import io.circe.syntax.EncoderOps
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError
import pl.touk.nussknacker.engine.api.validation.ValidationMode
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.lite.components.requestresponse.jsonschema.sinks.JsonRequestResponseSink
import pl.touk.nussknacker.engine.lite.util.test.RequestResponseTestScenarioRunner
import pl.touk.nussknacker.engine.lite.util.test.RequestResponseTestScenarioRunner._
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.util.output.OutputValidatorErrorsMessageFormatter
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner
import pl.touk.nussknacker.test.SpecialSpELElement.{EmptyMap, Input}
import pl.touk.nussknacker.test.{EitherValuesDetailedMessage, SpecialSpELElement}

class LiteRequestResponseFunctionalTest extends AnyFunSuite with Matchers with EitherValuesDetailedMessage
  with TableDrivenPropertyChecks {

  import pl.touk.nussknacker.engine.lite.components.requestresponse.jsonschema.sinks.JsonRequestResponseSink._
  import pl.touk.nussknacker.test.LiteralSpELImplicits._

  private val runner: RequestResponseTestScenarioRunner = TestScenarioRunner
    .requestResponseBased()
    .build()

  private val ObjectFieldName = "field"

  private val AdditionalFieldName = "field2"

  private val InputField = SpecialSpELElement(s"#input.$ObjectFieldName")

  private val sinkName = "response"

  private val schemaNull = """{"type": "null"}"""

  private val schemaObjNull =
    s"""{"type":"object","properties": {"$ObjectFieldName": $schemaNull}, "additionalProperties": false}"""

  private def schemaObjString(additionalProperties: Boolean = false) =
    s"""{"type":"object","properties": {"$ObjectFieldName": {"type": "string"}}, "additionalProperties": $additionalProperties}"""

  private def schemaObjUnionNullString(additionalProperties: Boolean = false) =
    s"""{"type":"object","properties": {"$ObjectFieldName": {"type": ["null", "string"]}}, "additionalProperties": $additionalProperties}"""

  private val sampleStr = "sample"

  private val sampleObjWithAdds: Json = obj(
    ObjectFieldName -> fromString(sampleStr),
    AdditionalFieldName -> fromString("str")
  )

  private val sampleSpELObjWithAdds = Map(
    ObjectFieldName -> sampleStr,
    AdditionalFieldName -> "str"
  )

  test("should test e2e request-response flow at sink and source / handling nulls and empty json" ) {
    val testData = Table(
      ("config", "result"),
      (config(obj(), schemaObjNull, schemaObjNull), Valid(obj())),
      (config(obj(), schemaObjNull, schemaObjNull, Map(ObjectFieldName -> InputField)), Valid(obj())), //FIXME: it should return {field: null} => DefaultResponseRequestSinkImplFactory.prepareResponse:21 convert Map(field->null) to empty json
      (config(JsonObject(Null), schemaObjNull, schemaObjNull), Valid(obj())), //FIXME: it should return {field: null} => DefaultResponseRequestSinkImplFactory.prepareResponse:21 convert Map(field->null) to empty json
      (config(JsonObject(Null), schemaObjNull, schemaObjNull, Map(ObjectFieldName -> InputField)), Valid(obj())), //FIXME: it should return {field: null} => DefaultResponseRequestSinkImplFactory.prepareResponse:21 convert Map(field->null) to empty json

      (config(obj(), schemaObjString(), schemaObjString()), Valid(obj())),
      (config(obj(), schemaObjString(), schemaObjString(), Map(ObjectFieldName -> InputField)), Valid(obj())), //FIXME: it should throw exception at runtime

      (config(obj(), schemaObjUnionNullString(), schemaObjUnionNullString(), Map(ObjectFieldName -> InputField)), Valid(obj())), //FIXME: it should return {field: null} => DefaultResponseRequestSinkImplFactory.prepareResponse:21 convert Map(field->null) to empty json
      (config(obj(), schemaObjUnionNullString(), schemaObjUnionNullString()), Valid(obj())),
      (config(JsonObject(Null), schemaObjUnionNullString(), schemaObjUnionNullString()), Valid(obj())), //FIXME: it should return {field: null} => DefaultResponseRequestSinkImplFactory.prepareResponse:21 convert Map(field->null) to empty json
      (config(JsonObject(Null), schemaObjUnionNullString(), schemaObjUnionNullString(), Map(ObjectFieldName -> InputField)), Valid(obj())), //FIXME: it should return {field: null} => DefaultResponseRequestSinkImplFactory.prepareResponse:21 convert Map(field->null) to empty json
    )

    forAll(testData) { (config: ScenarioConfig, expected: Validated[_, Json]) =>
      val results = runWithResults(config)
      results shouldBe expected
    }
  }

  test("should test e2e request-response flow at sink and source / handling objects.." ) {
    val testData = Table(
      ("config", "result"),
      (config(JsonObject(fromString(sampleStr)), schemaObjString(), schemaObjString()), validJObj(fromString(sampleStr))),
      (spelConfig(schemaObjString(), SpELObject(sampleStr)), validJObj(fromString(sampleStr))),

      //Union testing
      (config(JsonObject(fromString(sampleStr)), schemaObjUnionNullString(), schemaObjUnionNullString()), validJObj(fromString(sampleStr))),
      (config(JsonObject(fromString(sampleStr)), schemaObjString(), schemaObjUnionNullString()), validJObj(fromString(sampleStr))),
      (config(JsonObject(Null), schemaObjNull, schemaObjUnionNullString()), Valid(obj())), //FIXME: it should return {field: null} => DefaultResponseRequestSinkImplFactory.prepareResponse:21 convert Map(field->null) to empty json
      (config(JsonObject(fromString(sampleStr)), schemaObjUnionNullString(), schemaObjString()), validJObj(fromString(sampleStr))),
      (spelConfig(schemaObjUnionNullString(), SpELObject(sampleStr)), validJObj(fromString(sampleStr))),

      //Testing additional properties
      //(config(sampleObjWithAdds, schemaObjUnionNullString(true), schemaObjUnionNullString()), invalid(Nil, Nil, List(AdditionalFieldName))), //FIXME: on source additional field is trimmed by JsonToNuStruct:34, missing support for additionalProperties on objects with fields..
      (config(sampleObjWithAdds, schemaObjUnionNullString(true), schemaObjUnionNullString(true)), Valid(sampleObjWithAdds)),
      (spelConfig(schemaObjUnionNullString(), sampleSpELObjWithAdds), invalid(Nil, Nil, List(AdditionalFieldName))),
      (spelConfig(schemaObjUnionNullString(true), sampleSpELObjWithAdds), Valid(sampleObjWithAdds)),

      //(config(sampleObjWithAdds, schemaObjString(true), schemaObjUnionNullString()), invalid(Nil, Nil, List(AdditionalFieldName))), //FIXME: on source additional field is trimmed by JsonToNuStruct:34, missing support for additionalProperties on objects with fields..
      (config(sampleObjWithAdds, schemaObjString(true), schemaObjUnionNullString(true)), Valid(sampleObjWithAdds)),
      (spelConfig(schemaObjString(), sampleSpELObjWithAdds), invalid(Nil, Nil, List(AdditionalFieldName))),
      (spelConfig(schemaObjString(true), sampleSpELObjWithAdds), Valid(sampleObjWithAdds)),
    )

    forAll(testData) { (config: ScenarioConfig, expected: Validated[_, Json]) =>
      val results = runWithResults(config)
      results shouldBe expected
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
        "inputSchema" -> config.sourceSchema,
        "outputSchema" -> config.outputSchema
      ))
      .source("input", "request")
      .emptySink(sinkName, "response",
        SinkRawEditorParamName -> "true",
        SinkValidationModeParameterName -> s"'${config.validationModeName}'",
        SinkRawValueParamName -> config.sinkDefinition
      )

  case class ScenarioConfig(input: Json, sourceSchema: String, outputSchema: String, sinkDefinition: String, validationMode: Option[ValidationMode]) {
    lazy val validationModeName: String = validationMode.map(_.name).getOrElse(ValidationMode.strict.name)
  }

  //Special config for SpEL as output, on input we pass simple null
  private def spelConfig(outputSchema: String, output: Any = Input, validationMode: Option[ValidationMode] = None): ScenarioConfig =
    config(Null, schemaNull, outputSchema, output, validationMode)

  private def config(input: Json, inputSchema: String, outputSchema: String, output: Any = Input, validationMode: Option[ValidationMode] = None): ScenarioConfig = {
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
