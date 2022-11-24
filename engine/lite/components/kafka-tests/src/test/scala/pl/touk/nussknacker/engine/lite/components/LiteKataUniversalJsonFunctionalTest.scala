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

class LiteKataUniversalJsonFunctionalTest extends AnyFunSuite with Matchers with ScalaCheckDrivenPropertyChecks with Inside
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
      (config(obj(), schemaObjNull, schemaObjNull, objOutputAsInputFieldSpEL), valid(sampleObjNull)),
      (config(sampleObjNull, schemaObjNull, schemaObjNull), valid(sampleObjNull)),
      (config(sampleObjNull, schemaObjNull, schemaObjNull, objOutputAsInputFieldSpEL), valid(sampleObjNull)),

      (config(obj(), schemaObjString, schemaObjString), valid(obj())),
      (config(obj(), schemaObjString, schemaObjString, objOutputAsInputFieldSpEL), valid(obj())), //FIXME: it should throw exception at runtime

      (config(obj(), schemaObjUnionNullString, schemaObjUnionNullString, objOutputAsInputFieldSpEL), valid(sampleObjNull)),
      (config(obj(), schemaObjUnionNullString, schemaObjUnionNullString), valid(obj())),
      (config(sampleObjNull, schemaObjUnionNullString, schemaObjUnionNullString), valid(sampleObjNull)),
      (config(sampleObjNull, schemaObjUnionNullString, schemaObjUnionNullString, objOutputAsInputFieldSpEL), valid(sampleObjNull)),
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
      (config(sampleObjStr, schemaObjString, schemaObjString), valid(sampleObjStr)),
      (spelConfig(schemaObjString, sampleObjStrSpEL), valid(sampleObjStr)),

      //Additional fields turn on
      (config(sampleObjMapAny, schemaObjMapAny, schemaObjMapAny), valid(sampleObjMapAny)),
      (spelConfig(schemaObjMapAny, sampleObjMapAnySpEL), valid(sampleObjMapAny)),
      (config(sampleObjMapPerson, schemaObjMapObjPerson, schemaObjMapAny), valid(sampleObjMapPerson)),
      (spelConfig(schemaObjMapAny, sampleObjMapPersonSpEL), valid(sampleObjMapPerson)),
      (config(sampleObjMapInt, schemaObjMapInteger, schemaObjMapAny), valid(sampleObjMapInt)),

      (config(sampleObjMapInt, schemaObjMapInteger, schemaObjMapInteger), valid(sampleObjMapInt)),
      (spelConfig(schemaObjMapInteger, sampleObjMapIntSpEL), valid(sampleObjMapInt)),

      (config(samplePerson, schemaPerson, schemaObjString), invalid(Nil, List("field"), List("age", "first", "last"))),
      (spelConfig(schemaObjString, samplePersonSpEL), invalid(Nil, List("field"), List("first", "last", "age"))),

      (config(sampleObjMapAny, schemaObjMapAny, schemaObjMapInteger), invalid(List("path 'field.value' actual: 'Unknown' expected: 'Long'"), Nil, Nil)),

      (config(samplePerson, nameAndLastNameSchema, nameAndLastNameSchema), valid(samplePerson)),
      (config(samplePerson, schemaPerson, nameAndLastNameSchema), valid(samplePerson)),
      (config(samplePerson, schemaPerson, nameAndLastNameSchema(schemaInteger)), valid(samplePerson)),
      (config(samplePerson, schemaPerson, nameAndLastNameSchema(schemaString)), invalid(List("path 'age' actual: 'Long' expected: 'String'"), Nil, Nil)),
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
    def invalidType(msg: String) = invalid(List(msg), Nil, Nil)

    //@formatter:off
    val testData = Table(
      ("sourceSchema",        "sinkSchema",                         "validationModes",  "result"),
      (schemaMapAny,          schemaMapAny,                         strictAndLax,       valid(obj())),
      (schemaMapString,       schemaMapAny,                         strictAndLax,       valid(obj())),
      (schemaMapObjPerson,    schemaMapAny,                         strictAndLax,       valid(obj())),
      (schemaListIntegers,    schemaMapAny,                         strictAndLax,       invalidType("actual: 'List[Long]' expected: 'Map[String, Any]'")),
      (schemaPerson,          schemaMapAny,                         strictAndLax,       valid(obj())),
      (schemaMapAny,          schemaMapString,                      strict,             invalidType("path 'value' actual: 'Unknown' expected: 'String'")),
      (schemaMapAny,          schemaMapString,                      lax,                valid(obj())),
      (schemaMapString,       schemaMapString,                      strictAndLax,       valid(obj())),
      (schemaMapStringOrInt,  schemaMapString,                      strict,             invalidType("path 'value' actual: 'String | Long' expected: 'String'")),
      (schemaMapStringOrInt,  schemaMapString,                      lax,                valid(obj())),
      (schemaMapObjPerson,    schemaMapString,                      strictAndLax,       invalidType("path 'value' actual: '{age: Long, first: String, last: String}' expected: 'String'")),
      (schemaListIntegers,    schemaMapString,                      strictAndLax,       invalidType("actual: 'List[Long]' expected: 'Map[String, String]'")),
      (schemaPerson,          schemaMapString,                      strictAndLax,       invalidType("path 'age' actual: 'Long' expected: 'String'")),
      (schemaMapAny,          schemaMapStringOrInt,                 strict,             invalidType("path 'value' actual: 'Unknown' expected: 'String | Long'")),
      (schemaMapAny,          schemaMapStringOrInt,                 lax,                valid(obj())),
      (schemaMapString,       schemaMapStringOrInt,                 strictAndLax,       valid(obj())),
      (schemaMapStringOrInt,  schemaMapStringOrInt,                 strictAndLax,       valid(obj())),
      (schemaMapObjPerson,    schemaMapStringOrInt,                 strictAndLax,       invalidType("path 'value' actual: '{age: Long, first: String, last: String}' expected: 'String | Long'")),
      (schemaListIntegers,    schemaMapStringOrInt,                 strictAndLax,       invalidType("actual: 'List[Long]' expected: 'Map[String, String | Long]'")),
      (schemaPerson,          schemaMapStringOrInt,                 strictAndLax,       valid(obj())),
      (schemaPerson,          nameAndLastNameSchema,                strictAndLax,       valid(obj())),
      (schemaPerson,          nameAndLastNameSchema(schemaInteger), strictAndLax,       valid(obj())),
      (schemaPerson,          nameAndLastNameSchema(schemaString),  strictAndLax,       invalidType("path 'age' actual: 'Long' expected: 'String'")),
    )
    //@formatter:on

    forAll(testData) {
      (sourceSchema: EveritSchema, sinkSchema: EveritSchema, validationModes: List[ValidationMode], expected: Validated[_, RunResult[_]]) =>
        validationModes.foreach { mode =>
          val results = runWithValueResults(config(obj(), sourceSchema, sinkSchema, output = Input, Some(mode)))
          results shouldBe expected
        }
    }
  }

  test("should catch runtime errors at deserialization") {
    val testData = Table(
      ("input", "sourceSchema", "expected"),
      (sampleObjStr, schemaObjInteger, s"#/$ObjectFieldName: expected type: Integer, found: String"),
      (JsonObj(Null), schemaObjInteger, s"#/$ObjectFieldName: expected type: Integer, found: Null"),
      (JsonObj(obj("t1" -> fromString("1"))), schemaObjMapInteger, s"#/$ObjectFieldName/t1: expected type: Integer, found: String"),
      (obj("first" -> sampleJStr), createObjSchema(true, true, schemaInteger), s"#: required key [$ObjectFieldName] not found"),
      (obj("t1" -> fromString("1"), ObjectFieldName -> fromString("1")), schemaObjString, "#: extraneous key [t1] is not permitted"),
    )

    forAll(testData) { (input: Json, sourceSchema: EveritSchema, expected: String) =>
      val cfg = config(input, sourceSchema, sourceSchema)
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

  case class ScenarioConfig(topic: String, inputData: Json, sourceSchema: EveritSchema, sinkSchema: EveritSchema, sinkDefinition: String, validationMode: Option[ValidationMode]) {
    lazy val validationModeName: String = validationMode.map(_.name).getOrElse(ValidationMode.strict.name)
    lazy val sourceTopic = s"$topic-input"
    lazy val sinkTopic = s"$topic-output"
  }

  //Special config for SpEL as output, on input we pass simple null
  private def spelConfig(outputSchema: EveritSchema, output: Any = Input, validationMode: Option[ValidationMode] = None): ScenarioConfig =
    config(Null, schemaNull, outputSchema, output, validationMode)

  private def config(inputData: Json, sourceSchema: EveritSchema, sinkSchema: EveritSchema, output: Any = Input, validationMode: Option[ValidationMode] = None): ScenarioConfig =
    ScenarioConfig(randomTopic, inputData, sourceSchema, sinkSchema, output.toSpELLiteral, validationMode)

}
