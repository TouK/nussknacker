package pl.touk.nussknacker.engine.lite.components

import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.data.Validated.{invalidNel, Invalid, Valid}
import io.circe.Json
import io.circe.Json.{fromFields, fromInt, fromLong, fromString, obj, Null}
import org.apache.kafka.clients.producer.ProducerRecord
import org.everit.json.schema.{ObjectSchema, Schema => EveritSchema, StringSchema}
import org.scalatest.{Assertion, Inside}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import pl.touk.nussknacker.engine.api.CirceUtil
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{
  CustomNodeError,
  EmptyMandatoryParameter,
  ExpressionParserCompilationError
}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process.TopicName
import pl.touk.nussknacker.engine.api.validation.ValidationMode
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.json.JsonSchemaBuilder
import pl.touk.nussknacker.engine.kafka.UnspecializedTopicName.ToUnspecializedTopicName
import pl.touk.nussknacker.engine.lite.util.test.KafkaConsumerRecord
import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer._
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.SchemaVersionOption
import pl.touk.nussknacker.engine.util.output.OutputValidatorErrorsMessageFormatter
import pl.touk.nussknacker.engine.util.test.RunResult
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner.RunnerListResult
import pl.touk.nussknacker.test.{SpecialSpELElement, ValidatedValuesDetailedMessage}

class LiteKafkaUniversalJsonFunctionalTest
    extends AnyFunSuite
    with Matchers
    with ScalaCheckDrivenPropertyChecks
    with Inside
    with TableDrivenPropertyChecks
    with ValidatedValuesDetailedMessage
    with FunctionalTestMixin {

  import pl.touk.nussknacker.engine.lite.components.utils.JsonTestData._
  import pl.touk.nussknacker.engine.spel.SpelExtension._
  import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
  import pl.touk.nussknacker.test.LiteralSpELImplicits._

  import LiteKafkaComponentProvider._
  import SpecialSpELElement._

  private val lax          = List(ValidationMode.lax)
  private val strict       = List(ValidationMode.strict)
  private val strictAndLax = ValidationMode.values

  test("should test end to end kafka json data at sink and source / handling nulls and empty json") {
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
      (
        config(sampleObjNull, schemaObjUnionNullStr, schemaObjUnionNullStr, objOutputAsInputField),
        valid(sampleObjNull)
      ),
    )

    forAll(testData) { (config: ScenarioConfig, expected: Validated[_, RunResult]) =>
      val results = runWithValueResults(config)
      results shouldBe expected
    }
  }

  // TODO: add more tests for primitives, logical tests, unions and so on, add random tests - like in LiteKafkaAvroSchemaFunctionalTest
  test("should test end to end kafka json data at sink and source / handling primitives..") {
    val testData = Table(
      ("config", "result"),
      // Primitive integer validations
      (config(sampleJLong, schemaLong, schemaInt), invalidTypes("actual: 'Long' expected: 'Integer'")),
      (
        config(sampleJBigDecimalFromInt, schemaBigDecimal, schemaInt),
        invalidTypes("actual: 'BigDecimal' expected: 'Integer'")
      ),
      (
        config(sampleJBigDecimalFromInt, schemaBigDecimal, schemaLong),
        invalidTypes("actual: 'BigDecimal' expected: 'Long'")
      ),
      (
        config(sampleJInt, schemaLong, schemaIntRange0to100, fromInt(200)),
        invalidRanges("actual value: '200' should be between 0 and 100")
      ),
      (
        config(sampleJInt, schemaLong, schemaIntRangeTo100, fromInt(200)),
        invalidRanges("actual value: '200' should be less than or equal to 100")
      ),
      (config(sampleJInt, schemaLong, schemaIntRange0to100, fromInt(100)), valid(fromInt(100))),
      (config(sampleJInt, schemaInt, schemaLong), valid(sampleJInt)),
      (config(fromLong(Integer.MAX_VALUE), schemaInt, schemaInt), valid(fromInt(Integer.MAX_VALUE))),

      // Number conversion
      (config(sampleJInt, schemaInt, schemaLong), valid(sampleJLongFromInt)),
      (config(sampleJStr, schemaString, schemaLong, sampleInt), valid(sampleJLongFromInt)),
      (config(sampleJInt, schemaInt, schemaBigDecimal), valid(sampleJBigDecimalFromInt)),
      (config(sampleJStr, schemaString, schemaBigDecimal, sampleInt), valid(sampleJBigDecimalFromInt)),
      (config(sampleJInt, schemaLong, schemaBigDecimal), valid(sampleJBigDecimalFromInt)),
      (config(sampleJStr, schemaString, schemaBigDecimal, sampleLong), valid(sampleJBigDecimalFromLong)),
    )

    forAll(testData) { (config: ScenarioConfig, expected: Validated[_, RunResult]) =>
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

      // Additional fields turn on
      (config(sampleObjMapAny, schemaObjMapAny, schemaObjMapAny), valid(sampleObjMapAny)),
      (conf(schemaObjMapAny, sampleObjMapAnyOutput), valid(sampleObjMapAny)),
      (config(sampleObjMapPerson, schemaObjMapObjPerson, schemaObjMapAny), valid(sampleObjMapPerson)),
      (conf(schemaObjMapAny, sampleObjMapPersonOutput), valid(sampleObjMapPerson)),
      (config(sampleObjMapInt, schemaObjMapLong, schemaObjMapAny), valid(sampleObjMapInt)),
      (config(sampleObjMapInt, schemaObjMapLong, schemaObjMapLong), valid(sampleObjMapInt)),
      (conf(schemaObjMapLong, sampleObjMapIntOutput), valid(sampleObjMapInt)),
      (
        config(samplePerson, schemaPerson, schemaObjStr),
        invalid(Nil, List("field"), List("last", "first", "age"), Nil)
      ),
      (conf(schemaObjStr, samplePersonOutput), invalid(Nil, List("field"), List("first", "last", "age"), Nil)),
      (
        config(sampleObjMapAny, schemaObjMapAny, schemaObjMapLong),
        invalidTypes("path 'field.value' actual: 'Unknown' expected: 'Long'")
      ),
      (config(samplePerson, nameAndLastNameSchema, nameAndLastNameSchema), valid(samplePerson)),
      (config(samplePerson, schemaPerson, nameAndLastNameSchema), valid(samplePerson)),
      (config(samplePerson, schemaPerson, nameAndLastNameSchema(schemaLong)), valid(samplePerson)),
      (
        config(samplePerson, schemaPerson, nameAndLastNameSchema(schemaString)),
        invalidTypes("path 'age' actual: 'Long' expected: 'String'")
      ),
      (
        config(samplePerson, schemaPersonWithLimits, nameAndLastNameSchema(schemaString)),
        invalidTypes("path 'age' actual: 'Integer' expected: 'String'")
      ),
    )

    forAll(testData) { (config: ScenarioConfig, expected: Validated[_, RunResult]) =>
      val results = runWithValueResults(config)
      results shouldBe expected
    }
  }

  test("sink with schema with additionalProperties: true/{schema}") {
    //@formatter:off
    val testData = Table(
      ("input",           "sourceSchema",                "sinkSchema",                         "validationModes",  "result"),
      (sampleMapAny,      schemaMapAny,                  schemaMapAny,                         strictAndLax,       valid(sampleMapAny)),
      (sampleMapStr,      schemaMapStr,                  schemaMapAny,                         strictAndLax,       valid(sampleMapStr)),
      (sampleMapPerson,   schemaMapObjPerson,            schemaMapAny,                         strictAndLax,       valid(sampleMapPerson)),
      (sampleArrayInt,    schemaArrayLong,               schemaMapAny,                         strictAndLax,       invalidTypes("actual: 'List[Long]' expected: 'Map[String, Any]'")),
      (samplePerson,      schemaPerson,                  schemaMapAny,                         strictAndLax,       valid(samplePerson)),
      (sampleMapAny,      schemaMapAny,                  schemaMapStr,                         strict,             invalidTypes("path 'value' actual: 'Unknown' expected: 'String'")),
      (sampleMapStr,      schemaMapAny,                  schemaMapStr,                         lax,                valid(sampleMapStr)),
      (sampleMapStr,      schemaMapStr,                  schemaMapStr,                         strictAndLax,       valid(sampleMapStr)),
      (sampleMapStr,      schemaMapStringOrLong,         schemaMapStr,                         strict,             invalidTypes("path 'value' actual: 'Long | String' expected: 'String'")),
      (sampleMapStr,      schemaMapStringOrLong,         schemaMapStr,                         lax,                valid(sampleMapStr)),
      (sampleMapPerson,   schemaMapObjPerson,            schemaMapStr,                         strictAndLax,       invalidTypes("path 'value' actual: 'Record{age: Long, first: String, last: String}' expected: 'String'")),
      (sampleMapPerson,   schemaMapObjPersonWithLimits,  schemaMapStr,                         strictAndLax,       invalidTypes("path 'value' actual: 'Record{age: Integer, first: String, last: String}' expected: 'String'")),
      (sampleArrayInt,    schemaArrayLong,               schemaMapStr,                         strictAndLax,       invalidTypes("actual: 'List[Long]' expected: 'Map[String, String]'")),
      (samplePerson,      schemaPerson,                  schemaMapStr,                         strictAndLax,       invalidTypes("path 'age' actual: 'Long' expected: 'String'")),
      (samplePerson,      schemaPersonWithLimits,        schemaMapStr,                         strictAndLax,       invalidTypes("path 'age' actual: 'Integer' expected: 'String'")),
      (sampleMapAny,      schemaMapAny,                  schemaMapStringOrLong,                strict,             invalidTypes("path 'value' actual: 'Unknown' expected: 'Long | String'")),
      (sampleMapInt,      schemaMapAny,                  schemaMapStringOrLong,                lax,                valid(sampleMapInt)),
      (sampleMapStr,      schemaMapStr,                  schemaMapStringOrLong,                strictAndLax,       valid(sampleMapStr)),
      (sampleMapStr,      schemaMapStringOrLong,         schemaMapStringOrLong,                strictAndLax,       valid(sampleMapStr)),
      (sampleMapInt,      schemaMapStringOrLong,         schemaMapStringOrLong,                strictAndLax,       valid(sampleMapInt)),
      (samplePerson,      schemaMapObjPerson,            schemaMapStringOrLong,                strictAndLax,       invalidTypes("path 'value' actual: 'Record{age: Long, first: String, last: String}' expected: 'Long | String'")),
      (samplePerson,      schemaMapObjPersonWithLimits,  schemaMapStringOrLong,                strictAndLax,       invalidTypes("path 'value' actual: 'Record{age: Integer, first: String, last: String}' expected: 'Long | String'")),
      (sampleArrayInt,    schemaArrayLong,               schemaMapStringOrLong,                strictAndLax,       invalidTypes("actual: 'List[Long]' expected: 'Map[String, Long | String]'")),
      (samplePerson,      schemaPerson,                  schemaMapStringOrLong,                strictAndLax,       valid(samplePerson)),
      (samplePerson,      schemaPerson,                  nameAndLastNameSchema,                strictAndLax,       valid(samplePerson)),
      (samplePerson,      schemaPerson,                  nameAndLastNameSchema(schemaLong),    strictAndLax,       valid(samplePerson)),
      (samplePerson,      schemaPerson,                  nameAndLastNameSchema(schemaString),  strictAndLax,       invalidTypes("path 'age' actual: 'Long' expected: 'String'")),
      (samplePerson,      schemaPersonWithLimits,        nameAndLastNameSchema(schemaString),  strictAndLax,       invalidTypes("path 'age' actual: 'Integer' expected: 'String'")),
    )
    //@formatter:on

    forAll(testData) {
      (
          input: Json,
          sourceSchema: EveritSchema,
          sinkSchema: EveritSchema,
          validationModes: List[ValidationMode],
          expected: Validated[_, RunResult]
      ) =>
        validationModes.foreach { mode =>
          val cfg     = config(input, sourceSchema, sinkSchema, output = Input, Some(mode))
          val results = runWithValueResults(cfg)
          results shouldBe expected
        }
    }
  }

  test("sink with schema with range defined on age field") {
    //@formatter:off
    val testData = Table(
      ("input", "sourceSchema", "sinkSchema", "validationModes", "result"),
      (samplePerson, schemaPerson, schemaPersonWithLimits, strictAndLax, valid(samplePerson)),
      (sampleInvalidPerson, schemaPerson, schemaPersonWithLimits, strictAndLax, invalidRanges("path 'age' actual value: '300' should be between 10 and 200")),
      (sampleInvalidPerson, schemaPerson, schemaPersonWithUpperLimits, strictAndLax, invalidRanges("path 'age' actual value: '300' should be less than or equal to 200")),
      (sampleInvalidPerson, schemaPerson, schemaPersonWithLowerLimits, strictAndLax, invalidRanges("path 'age' actual value: '300' should be greater than or equal to 301")),
    )
    //@formatter:on

    forAll(testData) {
      (
          input: Json,
          sourceSchema: EveritSchema,
          sinkSchema: EveritSchema,
          validationModes: List[ValidationMode],
          expected: Validated[_, RunResult]
      ) =>
        validationModes.foreach { mode =>
          val cfg     = config(input, sourceSchema, sinkSchema, output = input, Some(mode))
          val results = runWithValueResults(cfg)
          results shouldBe expected
        }
    }
  }

  test("sink with enum schema") {
    val A   = Json.fromString("A")
    val one = Json.fromInt(1)
    val two = Json.fromInt(2)
    val obj = Json.obj(("x", A), ("y", Json.arr(one, two)))
    //@formatter:off
    val testData = Table(
      ("input",   "sourceSchema",           "sinkSchema",          "validationModes",  "result"),
      (A,         schemaEnumABC,            schemaEnumABC,         strictAndLax,       valid(A)),
      (A,         schemaEnumABC,            schemaEnumABC,         strictAndLax,       valid(A)),
      (A,         schemaEnumAB,             schemaEnumABC,         strictAndLax,       valid(A)),
      (A,         schemaEnumABC,            schemaEnumAB,          lax,                valid(A)),
      (A,         schemaEnumABC,            schemaEnumAB,          strict,             invalidTypes("actual: 'String(A) | String(B) | String(C)' expected: 'String(A) | String(B)'")),
      (A,         schemaString,             schemaEnumABC,         strictAndLax,       valid(A)),
      (A,         schemaEnumABC,            schemaString,          strictAndLax,       valid(A)),
      (A,         schemaEnumABC,            schemaEnumAB1,         lax,                valid(A)),
      (A,         schemaEnumABC,            schemaEnumAB1,         strict,             invalidTypes("actual: 'String(A) | String(B) | String(C)' expected: 'Integer(1) | String(A) | String(B)'")),
      (A,         schemaEnumAB1,            schemaEnumAB,          lax,                valid(A)),
      (A,         schemaEnumAB1,            schemaEnumAB,          strict,             invalidTypes("actual: 'Integer(1) | String(A) | String(B)' expected: 'String(A) | String(B)'")),
      (A,         schemaEnumAB1,            schemaEnumAB1,         strictAndLax,       valid(A)),
      (one,       schemaEnumAB1,            schemaEnumAB1,         strictAndLax,       valid(one)),
      (obj,       schemaEnumStrOrObj,       schemaEnumStrOrObj,    lax,                valid(obj)),
      (A,         schemaEnumStrOrObj,       schemaEnumStrOrObj,    lax,                valid(A)),
    )
    //@formatter:on

    forAll(testData) {
      (
          input: Json,
          sourceSchema: EveritSchema,
          sinkSchema: EveritSchema,
          validationModes: List[ValidationMode],
          expected: Validated[_, RunResult]
      ) =>
        validationModes.foreach { mode =>
          val cfg     = config(input, sourceSchema, sinkSchema, output = Input, Some(mode))
          val results = runWithValueResults(cfg)
          results shouldBe expected
        }
    }
  }

  test("sink with enum list schema") {
    val cfg =
      config(Json.obj(), schemaObjStr, schemaEnumStrOrList, output = SpecialSpELElement("{1,2}"), lax.headOption)
    val results = runWithValueResults(cfg)

    results.isValid shouldBe false
    val runtimeError = results.invalidValue.head
    runtimeError.nodeIds shouldBe Set("my-sink")
    runtimeError.asInstanceOf[CustomNodeError].message shouldBe
      """Provided value does not match scenario output - errors:
        |Incorrect type: actual: 'List[Integer]({1, 2})' expected: 'List[Integer]({1, 2, 3}) | String(A)'.""".stripMargin
  }

  test("patternProperties handling") {
    val objWithIntPatternPropsAndOpenAdditionalSchema =
      createObjectSchemaWithPatternProperties(Map("foo_int" -> schemaLong))
    val objWithPatternPropsAndStringAdditionalSchema =
      createObjectSchemaWithPatternProperties(Map("foo_int" -> schemaLong), Some(schemaString))
    val objWithDefinedPropsPatternPropsAndAdditionalSchema = createObjectSchemaWithPatternProperties(
      Map("foo_int" -> schemaLong),
      Some(schemaString),
      Map("definedProp" -> schemaString)
    )

    val inputObjectIntPropValue     = fromInt(1)
    val inputObjectDefinedPropValue = fromString("someString")
    val inputObject                 = obj("foo_int" -> inputObjectIntPropValue)
    val inputObjectWithDefinedProp  = obj("definedProp" -> inputObjectDefinedPropValue)

    //@formatter:off
    val testData = Table(
      ("input",                      "sourceSchema",                                       "sinkSchema",                                   "sinkExpression",                             "validationModes",    "result"),
      (inputObject,                  schemaMapAny,                                         objWithIntPatternPropsAndOpenAdditionalSchema,  Input,                                        lax,                  valid(inputObject)),
      (inputObject,                  schemaMapAny,                                         objWithIntPatternPropsAndOpenAdditionalSchema,  Input,                                        strict,               invalidTypes("actual: 'Map[String,Unknown]' expected: 'Map[String, Any]'")),
      (inputObject,                  schemaMapAny,                                         objWithPatternPropsAndStringAdditionalSchema,   Input,                                        lax,                  valid(inputObject)),
      (inputObject,                  schemaMapAny,                                         objWithPatternPropsAndStringAdditionalSchema,   Input,                                        strict,               invalidTypes("actual: 'Map[String,Unknown]' expected: 'Map[String, String]'")),
      (inputObject,                  objWithIntPatternPropsAndOpenAdditionalSchema,        objWithIntPatternPropsAndOpenAdditionalSchema,  Input,                                        lax,                  valid(inputObject)),
      (inputObject,                  objWithIntPatternPropsAndOpenAdditionalSchema,        objWithIntPatternPropsAndOpenAdditionalSchema,  Input,                                        strict,               invalidTypes("actual: 'Map[String,Unknown]' expected: 'Map[String, Any]'")),
      (inputObject,                  objWithPatternPropsAndStringAdditionalSchema,         objWithIntPatternPropsAndOpenAdditionalSchema,  Input,                                        lax,                  valid(inputObject)),
      (inputObject,                  objWithPatternPropsAndStringAdditionalSchema,         objWithPatternPropsAndStringAdditionalSchema,   Input,                                        lax,                  valid(inputObject)),
      (inputObject,                  objWithPatternPropsAndStringAdditionalSchema,         objWithPatternPropsAndStringAdditionalSchema,   Input,                                        strict,               invalidTypes("actual: 'Map[String,Long | String]' expected: 'Map[String, String]'")),
      (inputObject,                  objWithPatternPropsAndStringAdditionalSchema,         schemaLong,                                     SpecialSpELElement("#input['foo_int']"),      lax,                  valid(inputObjectIntPropValue)),
      (inputObject,                  objWithPatternPropsAndStringAdditionalSchema,         schemaLong,                                     SpecialSpELElement("#input['foo_int']"),      strict,               invalidTypes("actual: 'Long | String' expected: 'Long'")),
      (inputObject,                  objWithDefinedPropsPatternPropsAndAdditionalSchema,   schemaLong,                                     SpecialSpELElement("#input['foo_int']"),      lax,                  invalidNel(ExpressionParserCompilationError("There is no property 'foo_int' in type: Record{definedProp: String}", "my-sink", Some(ParameterName("Value")), "#input['foo_int']", None))),
      (inputObjectWithDefinedProp,   objWithDefinedPropsPatternPropsAndAdditionalSchema,   schemaString,                                   SpecialSpELElement("#input.definedProp"),     strict,               valid(inputObjectDefinedPropValue)),
    )
    //@formatter:on

    forAll(testData) {
      (
          input: Json,
          sourceSchema: EveritSchema,
          sinkSchema: EveritSchema,
          sinkExpression: SpecialSpELElement,
          validationModes: List[ValidationMode],
          expected: Validated[_, RunResult]
      ) =>
        validationModes.foreach { mode =>
          val cfg     = config(input, sourceSchema, sinkSchema, output = sinkExpression, Some(mode))
          val results = runWithValueResults(cfg)
          results shouldBe expected
        }
    }
  }

  test("pattern properties validations should work in editor mode") {
    def invalidTypeInEditorMode(fieldName: String, error: String): Invalid[NonEmptyList[CustomNodeError]] = {
      val finalMessage = OutputValidatorErrorsMessageFormatter.makeMessage(List(error), Nil, Nil, Nil)
      Invalid(NonEmptyList.one(CustomNodeError(sinkName, finalMessage, Some(ParameterName(fieldName)))))
    }

    val objWithNestedPatternPropertiesMapSchema =
      createObjSchema(true, false, createObjectSchemaWithPatternProperties(Map("_int$" -> schemaLong)))
    val objectWithNettedPatternPropertiesMapAsRefSchema = JsonSchemaBuilder.parseSchema("""{
        |  "type": "object",
        |  "properties": {
        |    "field": {
        |      "$ref": "#/defs/RefSchema"
        |    }
        |  },
        |  "defs": {
        |    "RefSchema": {
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
        objWithNestedPatternPropertiesMapSchema,
        Map("field" -> "{'foo_int': 1}"),
        valid(obj("field" -> obj("foo_int" -> fromInt(1))))
      ),
      (
        objWithNestedPatternPropertiesMapSchema,
        Map("field" -> "{'foo_int': '1'}"),
        invalidTypeInEditorMode("field", "actual: 'String(1)' expected: 'Long'")
      ),
      (
        objectWithNettedPatternPropertiesMapAsRefSchema,
        Map("field" -> "{'foo_int': 1}"),
        valid(obj("field" -> obj("foo_int" -> fromInt(1))))
      ),
      (
        objectWithNettedPatternPropertiesMapAsRefSchema,
        Map("field" -> "{'foo_int': '1'}"),
        invalidTypeInEditorMode("field", "actual: 'String(1)' expected: 'Long'")
      ),
    )

    forAll(testData) { (sinkSchema: EveritSchema, sinkFields: Map[String, String], expected: Validated[_, RunResult]) =>
      val dummyInputObject = obj()
      val cfg              = config(dummyInputObject, schemaMapAny, sinkSchema)
      val jsonScenario     = createEditorModeScenario(cfg, sinkFields)
      runner.registerJsonSchema(cfg.sourceTopic.toUnspecialized, cfg.sourceSchema)
      runner.registerJsonSchema(cfg.sinkTopic.toUnspecialized, cfg.sinkSchema)

      val input = KafkaConsumerRecord[String, String](cfg.sourceTopic, cfg.inputData.toString())
      val results = runner
        .runWithStringData(jsonScenario, List(input))
        .map(_.mapSuccesses(r => CirceUtil.decodeJsonUnsafe[Json](r.value(), "invalid json string")))
      results shouldBe expected
    }
  }

  test("various combinations of optional-like fields with sink in editor mode") {
    def expectValidObject(expectedObject: Map[String, Json])(
        result: ValidatedNel[ProcessCompilationError, Map[String, Json]]
    ): Assertion =
      result shouldBe Valid(expectedObject)

    def expectedMissingValue(result: ValidatedNel[ProcessCompilationError, Map[String, Json]]): Assertion =
      result.invalidValue should matchPattern {
        case NonEmptyList(EmptyMandatoryParameter(_, _, ParameterName(`ObjectFieldName`), `sinkName`), Nil) =>
      }

    forAll(
      Table[EveritSchema, String, ValidatedNel[ProcessCompilationError, Map[String, Json]] => Assertion](
        ("outputSchema", "fieldExpression", "expectationCheckingFun"),
        (createObjSchema(required = false, schemaString), "", expectValidObject(Map.empty)),
        (createObjSchema(required = true, schemaString, schemaNull), "", expectedMissingValue),
        (
          createObjSchema(required = true, schemaString, schemaNull),
          "null",
          expectValidObject(Map(ObjectFieldName -> Json.Null))
        ),
        (
          createObjSchema(required = false, schemaString, schemaNull),
          "",
          // We don't want user to decide if he/she want a null or to remove field, so we make a choice for him/her
          expectValidObject(Map(ObjectFieldName -> Json.Null))
        ),
        (
          createObjSchema(required = false, schemaString, schemaNull),
          // This case is treated the same by Nussknacker - empty expression is just an expression that always returns null
          "null",
          expectValidObject(Map(ObjectFieldName -> Json.Null))
        )
      )
    ) { (outputSchema, fieldExpression, expectationCheckingFun) =>
      val dummyInputObject = obj()
      val cfg              = config(dummyInputObject, schemaMapAny, outputSchema)
      val jsonScenario     = createEditorModeScenario(cfg, Map(ObjectFieldName -> fieldExpression))
      runner.registerJsonSchema(cfg.sourceTopic.toUnspecialized, cfg.sourceSchema)
      runner.registerJsonSchema(cfg.sinkTopic.toUnspecialized, cfg.sinkSchema)
      val input = KafkaConsumerRecord[String, String](cfg.sourceTopic, cfg.inputData.toString())

      val validatedResults = runner.runWithStringData(jsonScenario, List(input))

      val validatedDecodedResult = validatedResults.map { result =>
        result.errors shouldBe empty
        result.successes should have size 1
        CirceUtil.decodeJsonUnsafe[Map[String, Json]](result.successes.head.value())
      }
      expectationCheckingFun(validatedDecodedResult)
    }
  }

  test("schema evolution on output") {
    forAll(
      Table(
        ("inputSchema", "outputSchema", "inputRecord", "expectedOutputRecord"),
        (
          createObjSchema(required = true, schemaString, schemaNull),
          createObjSchema(required = false, schemaString),
          fromFields(List(ObjectFieldName -> Json.Null)),
          fromFields(List.empty)
        ),
        (
          createObjSchema(required = true, schemaString, schemaNull),
          createObjSchema(required = false, schemaString, schemaNull),
          fromFields(List(ObjectFieldName -> Json.Null)),
          fromFields(List(ObjectFieldName -> Json.Null))
        ),
        (
          createObjSchema(required = false, schemaString),
          createObjSchema(required = true, schemaString, schemaNull),
          fromFields(List.empty),
          fromFields(List(ObjectFieldName -> Json.Null))
        ),
        // schema evolution on input - it is done by external mechanism
        (
          createObjSchema(required = false, StringSchema.builder().defaultValue("foo").build()),
          createObjSchema(required = true, schemaString),
          fromFields(List.empty),
          fromFields(List(ObjectFieldName -> fromString("foo")))
        ),
        // schema evolution on output - it is done by Nussknacker's code - see ToJsonSchemaBasedEncoder
        (
          createObjSchema(required = true, schemaString, schemaNull),
          createObjSchema(required = true, StringSchema.builder().defaultValue("foo").build()),
          fromFields(List(ObjectFieldName -> Json.Null)),
          fromFields(List(ObjectFieldName -> fromString("foo")))
        ),
        (
          createObjSchema(required = false, schemaString),
          createObjSchema(required = true, StringSchema.builder().defaultValue("foo").build()),
          fromFields(List.empty),
          fromFields(List(ObjectFieldName -> fromString("foo")))
        )
      )
    ) { (inputSchema, outputSchema, inputRecord, expectedOutputRecord) =>
      val result = runWithValueResults(config(inputRecord, inputSchema, outputSchema)).validValue
      result.errors shouldBe empty
      result.successes should have size 1
      result.successes.head shouldBe expectedOutputRecord
    }
  }

  test("schema evolution on output - redundant fields") {
    val secondsField = ObjectFieldName + "2"
    val twoFieldsSchema = ObjectSchema.builder
      .addPropertySchema(ObjectFieldName, schemaString)
      .addPropertySchema(secondsField, schemaString)
      .additionalProperties(false)
      .build()

    val strictConfig = config(
      inputData = fromFields(List(ObjectFieldName -> fromString("foo"), secondsField -> fromString("bar"))),
      sourceSchema = twoFieldsSchema,
      sinkSchema = createObjSchema(schemaString),
      validationMode = Some(ValidationMode.strict)
    )
    val strictValidationErrors = runWithValueResults(strictConfig).invalidValue
    strictValidationErrors should matchPattern {
      case NonEmptyList(CustomNodeError(`sinkName`, message, _), Nil)
          if message.contains(s"Redundant fields: $secondsField") =>
    }

    val laxConfig = strictConfig.copy(validationMode = Some(ValidationMode.lax))
    val laxResult = runWithValueResults(laxConfig).validValue
    laxResult.errors shouldBe empty
    laxResult.successes should have size 1
    laxResult.successes.head shouldBe fromFields(List(ObjectFieldName -> fromString("foo")))
  }

  private def createEditorModeScenario(
      config: ScenarioConfig,
      fieldsExpressions: Map[String, String]
  ): CanonicalProcess = {
    val sinkParams = (Map(
      topicParamName.value         -> s"'${config.sinkTopic.name}'",
      schemaVersionParamName.value -> s"'${SchemaVersionOption.LatestOptionName}'",
      sinkKeyParamName.value       -> "",
      sinkRawEditorParamName.value -> "false",
    ) ++ fieldsExpressions).mapValuesNow(Expression.spel)

    ScenarioBuilder
      .streamingLite("check json validation")
      .source(
        sourceName,
        KafkaUniversalName,
        topicParamName.value         -> s"'${config.sourceTopic.name}'".spel,
        schemaVersionParamName.value -> s"'${SchemaVersionOption.LatestOptionName}'".spel
      )
      .emptySink(sinkName, KafkaUniversalName, sinkParams.toList: _*)
  }

  test("should catch runtime errors at deserialization - source") {
    val testData = Table(
      ("input", "sourceSchema", "expected"),
      (sampleObjStr, schemaObjLong, s"#/$ObjectFieldName: expected type: Integer, found: String"),
      (JsonObj(Null), schemaObjLong, s"#/$ObjectFieldName: expected type: Integer, found: Null"),
      (
        JsonObj(obj("t1" -> fromString("1"))),
        schemaObjMapLong,
        s"#/$ObjectFieldName/t1: expected type: Integer, found: String"
      ),
      (
        obj("first" -> sampleJStr),
        createObjSchema(true, true, schemaLong),
        s"#: required key [$ObjectFieldName] not found"
      ),
      (
        obj("t1" -> fromString("1"), ObjectFieldName -> fromString("1")),
        schemaObjStr,
        "#: extraneous key [t1] is not permitted"
      ),
      (Json.fromString("X"), schemaEnumAB, "#: X is not a valid enum value"),
      (
        obj("foo_int" -> fromString("foo")),
        createObjectSchemaWithPatternProperties(Map("foo_int" -> schemaLong)),
        "#/foo_int: expected type: Integer, found: String"
      )
    )

    forAll(testData) { (input: Json, sourceSchema: EveritSchema, expected: String) =>
      // here we're testing only source runtime validation so to prevent typing issues we pass literal as sink expression
      val cfg     = config(input, sourceSchema, schemaString, output = "someString")
      val results = runWithValueResults(cfg)
      val message = results.validValue.errors.head.throwable.asInstanceOf[RuntimeException].getMessage

      message shouldBe expected
    }
  }

  test("should catch runtime errors at encoding - sink") {
    val testData = Table(
      ("config", "expected"),
      (
        config(
          obj("foo_int" -> fromString("foo")),
          schemaMapAny,
          createObjectSchemaWithPatternProperties(Map("foo_int" -> schemaLong)),
          validationMode = Some(ValidationMode.lax)
        ),
        s"Not expected type: String for field: 'foo_int' with schema: $schemaLong."
      ),
    )

    forAll(testData) { (cfg: ScenarioConfig, expected: String) =>
      val results = runWithValueResults(cfg)
      val message = results.validValue.errors.head.throwable.asInstanceOf[RuntimeException].getMessage

      message shouldBe expected
    }
  }

  test("should pass everything under 'any' schema") {
    val testData = Table(
      ("config", "result"),
      (sampleJInt, valid(sampleJInt)),
      (fromLong(Integer.MAX_VALUE), valid(fromInt(Integer.MAX_VALUE))),
      (samplePerson, valid(samplePerson))
    )

    forAll(testData) { (input: Json, expected: Validated[_, RunResult]) =>
      List(schemaTrue, schemaEmpty).foreach { schema =>
        val results = runWithValueResults(config(input, schema, schema))
        results shouldBe expected
      }
    }
  }

  private def runWithValueResults(config: ScenarioConfig) =
    runWithResults(config).map(_.mapSuccesses(r => CirceUtil.decodeJsonUnsafe[Json](r.value(), "invalid json string")))

  private def runWithResults(config: ScenarioConfig): RunnerListResult[ProducerRecord[String, String]] = {
    val jsonScenario: CanonicalProcess = createScenario(config)
    runner.registerJsonSchema(config.sourceTopic.toUnspecialized, config.sourceSchema)
    runner.registerJsonSchema(config.sinkTopic.toUnspecialized, config.sinkSchema)

    val input  = KafkaConsumerRecord[String, String](config.sourceTopic, config.inputData.toString())
    val result = runner.runWithStringData(jsonScenario, List(input))
    result
  }

  private def createScenario(config: ScenarioConfig): CanonicalProcess =
    ScenarioBuilder
      .streamingLite("check json validation")
      .source(
        sourceName,
        KafkaUniversalName,
        topicParamName.value         -> s"'${config.sourceTopic.name}'".spel,
        schemaVersionParamName.value -> s"'${SchemaVersionOption.LatestOptionName}'".spel
      )
      .emptySink(
        sinkName,
        KafkaUniversalName,
        topicParamName.value              -> s"'${config.sinkTopic.name}'".spel,
        schemaVersionParamName.value      -> s"'${SchemaVersionOption.LatestOptionName}'".spel,
        sinkKeyParamName.value            -> "".spel,
        sinkValueParamName.value          -> s"${config.sinkDefinition}".spel,
        sinkRawEditorParamName.value      -> "true".spel,
        sinkValidationModeParamName.value -> s"'${config.validationModeName}'".spel
      )

  case class ScenarioConfig(
      topic: String,
      inputData: Json,
      sourceSchema: EveritSchema,
      sinkSchema: EveritSchema,
      sinkDefinition: String,
      validationMode: Option[ValidationMode]
  ) {
    lazy val validationModeName: String = validationMode.map(_.name).getOrElse(ValidationMode.strict.name)
    lazy val sourceTopic                = TopicName.ForSource(s"$topic-input")
    lazy val sinkTopic                  = TopicName.ForSink(s"$topic-output")
  }

  private def conf(
      outputSchema: EveritSchema,
      output: Any = Input,
      validationMode: Option[ValidationMode] = None
  ): ScenarioConfig =
    config(Null, schemaNull, outputSchema, output, validationMode)

  private def config(
      inputData: Json,
      sourceSchema: EveritSchema,
      sinkSchema: EveritSchema,
      output: Any = Input,
      validationMode: Option[ValidationMode] = None
  ): ScenarioConfig =
    ScenarioConfig(randomTopic, inputData, sourceSchema, sinkSchema, output.toSpELLiteral, validationMode)

}
