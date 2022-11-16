package pl.touk.nussknacker.engine.lite.components

import cats.data.Validated.Valid
import cats.data.Validated
import io.circe.Json
import io.circe.Json.{Null, fromInt, fromLong, fromString, obj}
import org.apache.kafka.clients.producer.ProducerRecord
import org.everit.json.schema.{Schema => EveritSchema}
import org.scalatest.Inside
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import pl.touk.nussknacker.engine.api.CirceUtil
import pl.touk.nussknacker.engine.api.typed.CustomNodeValidationException
import pl.touk.nussknacker.engine.api.validation.ValidationMode
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.lite.components.utils.JsonTestData.{InputEmptyObject, schemaObjNull, _}
import pl.touk.nussknacker.engine.lite.util.test.{KafkaConsumerRecord, LiteKafkaTestScenarioRunner}
import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer._
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.SchemaVersionOption
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner.RunnerListResult
import pl.touk.nussknacker.engine.util.test.{RunListResult, RunResult, TestScenarioRunner}
import pl.touk.nussknacker.test.{SpecialSpELElement, ValidatedValuesDetailedMessage}

import java.util.UUID

class UniversalSourceJsonFunctionalTest extends AnyFunSuite with Matchers with ScalaCheckDrivenPropertyChecks with Inside
  with TableDrivenPropertyChecks with ValidatedValuesDetailedMessage {

  import pl.touk.nussknacker.test.LiteralSpELImplicits._
  import LiteKafkaComponentProvider._
  import LiteKafkaTestScenarioRunner._
  import SpecialSpELElement._
  import pl.touk.nussknacker.engine.spel.Implicits._

  private val sourceName = "my-source"
  private val sinkName = "my-sink"

  private val runner: LiteKafkaTestScenarioRunner = TestScenarioRunner.kafkaLiteBased().build()

  // TODO: add more tests for primitives, logical tests, unions and so on, add random tests - like in LiteKafkaAvroSchemaFunctionalTest

  test("should test end to end kafka json data at sink / source") {
    val testData = Table(
      ("config", "result"),
      //Primitive integer validations
      // FIXME handle minimum > MIN_VALUE && maximum < MAX_VALUE) as an Integer to make better interoperability between json and avro?
      //      (sConfig(fromLong(Integer.MAX_VALUE.toLong + 1), longSchema, integerRangeSchema), invalidTypes("path 'Value' actual: 'Long' expected: 'Integer'")),
      (sConfig(fromInt(1), integerRangeSchema, longSchema), valid(fromInt(1))),
      (sConfig(fromLong(Integer.MAX_VALUE), integerRangeSchema, integerRangeSchema), valid(fromInt(Integer.MAX_VALUE))),
      (sConfig(obj(), objectSchema, objectSchema), valid(obj())),
      (oConfig(obj("first" -> fromString(""), "last" -> fromString("")), objectSchema, objectSchema, Input), oValid(obj("first" -> fromString(""), "last" -> fromString("")))),

      //trimming not nullable and not required field when we sent null / empty object
      (oConfig(InputEmptyObject, schemaObjNull, schemaObjNull), valid(obj())),
      (oConfig(InputEmptyObject, schemaObjNull, schemaObjNull, SpecialSpELElement("#input.field")), oValid(Null)),
      (oConfig(Null, schemaObjNull, schemaObjNull), oValid(Null)),
      (oConfig(Null, schemaObjNull, schemaObjNull, SpecialSpELElement("#input.field")), oValid(Null)),

      (oConfig(InputEmptyObject, schemaObjString, schemaObjString), valid(obj())),
      (oConfig(InputEmptyObject, schemaObjString, schemaObjString, SpecialSpELElement("#input.field")), valid(obj())),

      (oConfig(InputEmptyObject, schemaObjUnionNullString, schemaObjUnionNullString, SpecialSpELElement("#input.field")), oValid(Null)),
      (oConfig(InputEmptyObject, schemaObjUnionNullString, schemaObjUnionNullString), valid(obj())),
      (oConfig(Null, schemaObjUnionNullString, schemaObjUnionNullString), oValid(Null)),
      (oConfig(Null, schemaObjUnionNullString, schemaObjUnionNullString, SpecialSpELElement("#input.field")), oValid(Null)),
    )

    forAll(testData) { (config: ScenarioConfig, expected: Validated[_, RunResult[_]]) =>
      val results = runWithValueResults(config)
      results shouldBe expected
    }
  }

  test("should catch runtime errors") {
    val testData = Table(
      ("config", "result"),
      (oConfig(Json.Null, objectSchema, objectSchema), "#/field: expected type: JSONObject, found: Null"),
      (oConfig(obj("first" -> fromString("")), objectSchema, objectSchema), "#/field: required key [last] not found"),
      (oConfig(fromString("invalid"), objectSchema, objectSchema), "#/field: expected type: JSONObject, found: String"),
      (oConfig(InputEmptyObject, schemaObjNull, schemaObjRequiredUnionNullString), "Missing required field: field."),
    )

    forAll(testData) { (config: ScenarioConfig, expected: String) =>
      val results = runWithValueResults(config)
      val message = results.validValue.errors.head.throwable.asInstanceOf[RuntimeException].getMessage

      message shouldBe expected
    }
  }

  private def runWithValueResults(config: ScenarioConfig) =
    runWithResults(config).map(_.mapSuccesses(r => CirceUtil.decodeJsonUnsafe[Json](r.value(), "invalid json string")))

  private def runWithResults(config: ScenarioConfig): RunnerListResult[ProducerRecord[String, String]] = {
    val jsonScenario: CanonicalProcess = createScenario(config)
    runner.registerJsonSchema(config.sourceTopic, config.sourceSchema)
    runner.registerJsonSchema(config.sinkTopic, config.sinkSchema)

    val input = KafkaConsumerRecord[String, String](config.sourceTopic, config.inputData.toString())
    val result = runner.runWithStringData(jsonScenario, List(input))
    result
  }

  private def createScenario(config: ScenarioConfig) =
    ScenarioBuilder
      .streamingLite("check json validation")
      .source(sourceName, KafkaUniversalName,
        TopicParamName -> s"'${config.sourceTopic}'",
        SchemaVersionParamName -> s"'${SchemaVersionOption.LatestOptionName}'"
      )
      .emptySink(sinkName, KafkaUniversalName,
        TopicParamName -> s"'${config.sinkTopic}'",
        SchemaVersionParamName -> s"'${SchemaVersionOption.LatestOptionName}'",
        SinkKeyParamName -> "",
        SinkValueParamName -> s"${config.sinkDefinition}",
        SinkRawEditorParamName -> "true",
        SinkValidationModeParameterName -> s"'${config.validationModeName}'"
      )

  private def randomTopic = UUID.randomUUID().toString

  case class ScenarioConfig(topic: String, inputData: Json, sourceSchema: EveritSchema, sinkSchema: EveritSchema, sinkDefinition: String, validationMode: Option[ValidationMode]) {
    lazy val validationModeName: String = validationMode.map(_.name).getOrElse(ValidationMode.strict.name)
    lazy val sourceTopic = s"$topic-input"
    lazy val sinkTopic = s"$topic-output"
  }

  //ObjectValid -> config with object as a input
  private def oConfig(inputData: Any, sourceSchema: EveritSchema, sinkSchema: EveritSchema, output: Any = Input, validationMode: Option[ValidationMode] = None): ScenarioConfig = {
    val sinkDefinition = output match {
      case element: SpecialSpELElement if List(EmptyMap, Input).contains(element) => element
      case any => Map(ObjectFieldName -> any)
    }

    val input = inputData match {
      case InputEmptyObject => obj()
      case in: Json => obj(ObjectFieldName -> in)
      case in => throw new IllegalArgumentException(s"Not allowed type of data: $in.")
    }

    ScenarioConfig(randomTopic, input, sourceSchema, sinkSchema, sinkDefinition.toSpELLiteral, validationMode)
  }

  //ObjectValid -> valid success object with base field
  private def oValid(data: Json): Valid[RunListResult[Json]] =
    valid(obj(ObjectFieldName -> data))

  private def valid[T](data: T): Valid[RunListResult[T]] =
    Valid(RunResult.success(data))

  private def sConfig(inputData: Json, sourceSchema: EveritSchema, sinkSchema: EveritSchema, output: SpecialSpELElement = Input, validationMode: Option[ValidationMode] = None): ScenarioConfig =
    ScenarioConfig(randomTopic, inputData, sourceSchema, sinkSchema, output.value, validationMode)

}
