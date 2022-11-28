package pl.touk.nussknacker.engine.lite.components

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
import pl.touk.nussknacker.engine.api.validation.ValidationMode
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.lite.util.test.KafkaConsumerRecord
import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer._
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.SchemaVersionOption
import pl.touk.nussknacker.engine.util.test.RunResult
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner.RunnerListResult
import pl.touk.nussknacker.test.{SpecialSpELElement, ValidatedValuesDetailedMessage}

class LiteKafkaUniversalJsonFunctionalTest extends AnyFunSuite with Matchers with ScalaCheckDrivenPropertyChecks with Inside
  with TableDrivenPropertyChecks with ValidatedValuesDetailedMessage with FunctionalTestMixin {

  import LiteKafkaComponentProvider._
  import SpecialSpELElement._
  import pl.touk.nussknacker.engine.lite.components.utils.JsonTestData._
  import pl.touk.nussknacker.engine.spel.Implicits._
  import pl.touk.nussknacker.test.LiteralSpELImplicits._

  test("should test end to end kafka json data at sink and source / handling nulls and empty json" ) {
    val testData = Table(
      ("config", "result"),
      (config(obj(), schemaObjNull, schemaObjNull), valid(obj())),
      (config(obj(), schemaObjNull, schemaObjNull, objOutputAsInputField), valid(sampleObjNull)),
      (config(sampleObjNull, schemaObjNull, schemaObjNull), valid(sampleObjNull)),
      (config(sampleObjNull, schemaObjNull, schemaObjNull, objOutputAsInputField), valid(sampleObjNull)),

      (config(obj(), schemaObjStr, schemaObjStr), valid(obj())),

      (config(obj(), schemaObjUnionNullStr, schemaObjUnionNullStr, objOutputAsInputField), valid(sampleObjNull)),
      (config(obj(), schemaObjUnionNullStr, schemaObjUnionNullStr), valid(obj())),
      (config(sampleObjNull, schemaObjUnionNullStr, schemaObjUnionNullStr), valid(sampleObjNull)),
      (config(sampleObjNull, schemaObjUnionNullStr, schemaObjUnionNullStr, objOutputAsInputField), valid(sampleObjNull)),
    )

    forAll(testData) { (config: ScenarioConfig, expected: Validated[_, RunResult[_]]) =>
      val results = runWithValueResults(config)
      results shouldBe expected
    }
  }

  // TODO: add more tests for primitives, logical tests, unions and so on, add random tests - like in LiteKafkaAvroSchemaFunctionalTest
  test("should test end to end kafka json data at sink and source / handling primitives..") {
    val testData = Table(
      ("config", "result"),
      //Primitive integer validations
      // FIXME handle minimum > MIN_VALUE && maximum < MAX_VALUE) as an Integer to make better interoperability between json and avro?
      //      (sConfig(fromLong(Integer.MAX_VALUE.toLong + 1), longSchema, integerRangeSchema), invalidTypes("path 'Value' actual: 'Long' expected: 'Integer'")),
      (config(sampleJInt, schemaIntegerRange, schemaInteger), valid(sampleJInt)),
      (config(fromLong(Integer.MAX_VALUE), schemaIntegerRange, schemaIntegerRange), valid(fromInt(Integer.MAX_VALUE))),
    )

    forAll(testData) { (config: ScenarioConfig, expected: Validated[_, RunResult[_]]) =>
      val results = runWithValueResults(config)
      results shouldBe expected
    }
  }

  // TODO: add more tests for handling objects..
  test("should test end to end kafka json data at sink and source / handling objects..") {
    val testData = Table(
      ("config", "result"),
      (config(sampleObjStr, schemaObjStr, schemaObjStr), valid(sampleObjStr)),
      (conf(schemaObjStr, sampleObjStrOutput), valid(sampleObjStr)),

      //Additional fields turn on
      (config(sampleObjMapAny, schemaObjMapAny, schemaObjMapAny), valid(sampleObjMapAny)),
      (conf(schemaObjMapAny, sampleObjMapAnyOutput), valid(sampleObjMapAny)),
      (config(sampleObjMapPerson, schemaObjMapObjPerson, schemaObjMapAny), valid(sampleObjMapPerson)),
      (conf(schemaObjMapAny, sampleObjMapPersonOutput), valid(sampleObjMapPerson)),
      (config(sampleObjMapInt, schemaObjMapInt, schemaObjMapAny), valid(sampleObjMapInt)),

      (config(sampleObjMapInt, schemaObjMapInt, schemaObjMapInt), valid(sampleObjMapInt)),
      (conf(schemaObjMapInt, sampleObjMapIntOutput), valid(sampleObjMapInt)),

      (config(samplePerson, schemaPerson, schemaObjStr), invalid(Nil, List("field"), List("age", "first", "last"))),
      (conf(schemaObjStr, samplePersonOutput), invalid(Nil, List("field"), List("first", "last", "age"))),

      (config(sampleObjMapAny, schemaObjMapAny, schemaObjMapInt), invalidTypes("path 'field.value' actual: 'Unknown' expected: 'Long'")),

      (config(samplePerson, nameAndLastNameSchema, nameAndLastNameSchema), valid(samplePerson)),
      (config(samplePerson, schemaPerson, nameAndLastNameSchema), valid(samplePerson)),
      (config(samplePerson, schemaPerson, nameAndLastNameSchema(schemaInteger)), valid(samplePerson)),
      (config(samplePerson, schemaPerson, nameAndLastNameSchema(schemaString)), invalidTypes("path 'age' actual: 'Long' expected: 'String'")),
    )

    forAll(testData) { (config: ScenarioConfig, expected: Validated[_, RunResult[_]]) =>
      val results = runWithValueResults(config)
      results shouldBe expected
    }
  }

  test("sink with schema with additionalProperties: true/{schema}") {
    //This tests runs scenario which passes #input directly to Sink using raw editor. Scenario is triggered with `{}` message.
    val lax = List(ValidationMode.lax)
    val strict = List(ValidationMode.strict)
    val strictAndLax = ValidationMode.values

    //@formatter:off
    val testData = Table(
      ("input",             "sourceSchema",        "sinkSchema",                         "validationModes",  "result"),
      (sampleMapAny,        schemaMapAny,          schemaMapAny,                         strictAndLax,       valid(sampleMapAny)),
      (sampleMapStr,        schemaMapStr,          schemaMapAny,                         strictAndLax,       valid(sampleMapStr)),
      (sampleMapPerson,     schemaMapObjPerson,    schemaMapAny,                         strictAndLax,       valid(sampleMapPerson)),
      (sampleArrayInt,      schemaArrayInt,        schemaMapAny,                         strictAndLax,       invalidTypes("actual: 'List[Long]' expected: 'Map[String, Any]'")),
      (samplePerson,        schemaPerson,          schemaMapAny,                         strictAndLax,       valid(samplePerson)),
      (sampleMapAny,        schemaMapAny,          schemaMapStr,                         strict,             invalidTypes("path 'value' actual: 'Unknown' expected: 'String'")),
      (sampleMapStr,        schemaMapAny,          schemaMapStr,                         lax,                valid(sampleMapStr)),
      (sampleMapStr,        schemaMapStr,          schemaMapStr,                         strictAndLax,       valid(sampleMapStr)),
      (sampleMapStr,        schemaMapStringOrInt,  schemaMapStr,                         strict,             invalidTypes("path 'value' actual: 'String | Long' expected: 'String'")),
      (sampleMapStr,        schemaMapStringOrInt,  schemaMapStr,                         lax,                valid(sampleMapStr)),
      (sampleMapPerson,     schemaMapObjPerson,    schemaMapStr,                         strictAndLax,       invalidTypes("path 'value' actual: '{age: Long, first: String, last: String}' expected: 'String'")),
      (sampleArrayInt,      schemaArrayInt,        schemaMapStr,                         strictAndLax,       invalidTypes("actual: 'List[Long]' expected: 'Map[String, String]'")),
      (samplePerson,        schemaPerson,          schemaMapStr,                         strictAndLax,       invalidTypes("path 'age' actual: 'Long' expected: 'String'")),
      (sampleMapAny,        schemaMapAny,          schemaMapStringOrInt,                 strict,             invalidTypes("path 'value' actual: 'Unknown' expected: 'String | Long'")),
      (sampleMapInt,        schemaMapAny,          schemaMapStringOrInt,                 lax,                valid(sampleMapInt)),
      (sampleMapStr,        schemaMapStr,          schemaMapStringOrInt,                 strictAndLax,       valid(sampleMapStr)),
      (sampleMapStr,        schemaMapStringOrInt,  schemaMapStringOrInt,                 strictAndLax,       valid(sampleMapStr)),
      (sampleMapInt,        schemaMapStringOrInt,  schemaMapStringOrInt,                 strictAndLax,       valid(sampleMapInt)),
      (samplePerson,        schemaMapObjPerson,    schemaMapStringOrInt,                 strictAndLax,       invalidTypes("path 'value' actual: '{age: Long, first: String, last: String}' expected: 'String | Long'")),
      (sampleArrayInt,      schemaArrayInt,        schemaMapStringOrInt,                 strictAndLax,       invalidTypes("actual: 'List[Long]' expected: 'Map[String, String | Long]'")),
      (samplePerson,        schemaPerson,          schemaMapStringOrInt,                 strictAndLax,       valid(samplePerson)),
      (samplePerson,        schemaPerson,          nameAndLastNameSchema,                strictAndLax,       valid(samplePerson)),
      (samplePerson,        schemaPerson,          nameAndLastNameSchema(schemaInteger), strictAndLax,       valid(samplePerson)),
      (samplePerson,        schemaPerson,          nameAndLastNameSchema(schemaString),  strictAndLax,       invalidTypes("path 'age' actual: 'Long' expected: 'String'")),
    )
    //@formatter:on

    forAll(testData) {
      (input: Json, sourceSchema: EveritSchema, sinkSchema: EveritSchema, validationModes: List[ValidationMode], expected: Validated[_, RunResult[_]]) =>
        validationModes.foreach { mode =>
          val cfg = config(input, sourceSchema, sinkSchema, output = Input, Some(mode))
          val results = runWithValueResults(cfg)
          results shouldBe expected
        }
    }
  }

  test("should catch runtime errors at deserialization - source") {
    val testData = Table(
      ("input", "sourceSchema", "expected"),
      (sampleObjStr, schemaObjInt, s"#/$ObjectFieldName: expected type: Integer, found: String"),
      (JsonObj(Null), schemaObjInt, s"#/$ObjectFieldName: expected type: Integer, found: Null"),
      (JsonObj(obj("t1" -> fromString("1"))), schemaObjMapInt, s"#/$ObjectFieldName/t1: expected type: Integer, found: String"),
      (obj("first" -> sampleJStr), createObjSchema(true, true, schemaInteger), s"#: required key [$ObjectFieldName] not found"),
      (obj("t1" -> fromString("1"), ObjectFieldName -> fromString("1")), schemaObjStr, "#: extraneous key [t1] is not permitted"),
    )

    forAll(testData) { (input: Json, sourceSchema: EveritSchema, expected: String) =>
      val cfg = config(input, sourceSchema, sourceSchema)
      val results = runWithValueResults(cfg)
      val message = results.validValue.errors.head.throwable.asInstanceOf[RuntimeException].getMessage

      message shouldBe expected
    }
  }

  test("should catch runtime errors at encoding - sink") {
    val testData = Table(
      ("config", "expected"),
      (config(obj(), schemaObjStr, schemaObjStr, objOutputAsInputField), s"Not expected type: null for field: 'field' with schema: $schemaString."),
    )

    forAll(testData) { (cfg: ScenarioConfig, expected: String) =>
      val results = runWithValueResults(cfg)
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

  private def createScenario(config: ScenarioConfig): CanonicalProcess =
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

  case class ScenarioConfig(topic: String, inputData: Json, sourceSchema: EveritSchema, sinkSchema: EveritSchema, sinkDefinition: String, validationMode: Option[ValidationMode]) {
    lazy val validationModeName: String = validationMode.map(_.name).getOrElse(ValidationMode.strict.name)
    lazy val sourceTopic = s"$topic-input"
    lazy val sinkTopic = s"$topic-output"
  }

  private def conf(outputSchema: EveritSchema, output: Any = Input, validationMode: Option[ValidationMode] = None): ScenarioConfig =
    config(Null, schemaNull, outputSchema, output, validationMode)

  private def config(inputData: Json, sourceSchema: EveritSchema, sinkSchema: EveritSchema, output: Any = Input, validationMode: Option[ValidationMode] = None): ScenarioConfig =
    ScenarioConfig(randomTopic, inputData, sourceSchema, sinkSchema, output.toSpELLiteral, validationMode)

}
