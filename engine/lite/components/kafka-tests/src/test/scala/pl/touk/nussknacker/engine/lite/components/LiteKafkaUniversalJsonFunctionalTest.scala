package pl.touk.nussknacker.engine.lite.components

import cats.data.Validated.Invalid
import cats.data.{NonEmptyList, Validated}
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
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{CustomNodeError, ExpressionParserCompilationError}
import pl.touk.nussknacker.engine.api.validation.ValidationMode
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.json.JsonSchemaBuilder
import pl.touk.nussknacker.engine.lite.util.test.KafkaConsumerRecord
import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer._
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.SchemaVersionOption
import pl.touk.nussknacker.engine.util.output.OutputValidatorErrorsMessageFormatter
import pl.touk.nussknacker.engine.util.test.RunResult
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner.RunnerListResult
import pl.touk.nussknacker.test.{SpecialSpELElement, ValidatedValuesDetailedMessage}

class LiteKafkaUniversalJsonFunctionalTest extends AnyFunSuite with Matchers with ScalaCheckDrivenPropertyChecks with Inside
  with TableDrivenPropertyChecks with ValidatedValuesDetailedMessage with FunctionalTestMixin {

  import LiteKafkaComponentProvider._
  import SpecialSpELElement._
  import pl.touk.nussknacker.engine.lite.components.utils.JsonTestData._
  import pl.touk.nussknacker.engine.spel.Implicits._
  import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
  import pl.touk.nussknacker.test.LiteralSpELImplicits._

  private val lax = List(ValidationMode.lax)
  private val strict = List(ValidationMode.strict)
  private val strictAndLax = ValidationMode.values

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
      (config(fromLong(Integer.MAX_VALUE.toLong + 1), schemaInteger, schemaIntegerRange), invalidTypes("actual: 'Long' expected: 'Integer'")),
      (config(sampleJInt, schemaInteger, schemaIntegerRange0to100, fromInt(200)), invalidRanges("actual value: '200' should be between 0 and 100")),
      (config(sampleJInt, schemaInteger, schemaIntegerRangeTo100, fromInt(200)), invalidRanges("actual value: '200' should be between -inf and 100")),
      (config(sampleJInt, schemaInteger, schemaIntegerRange0to100, fromInt(100)), valid(fromInt(100))),
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

      (config(samplePerson, schemaPerson, schemaObjStr), invalid(Nil, List("field"), List("age", "first", "last"), Nil)),
      (conf(schemaObjStr, samplePersonOutput), invalid(Nil, List("field"), List("first", "last", "age"), Nil)),

      (config(sampleObjMapAny, schemaObjMapAny, schemaObjMapInt), invalidTypes("path 'field.value' actual: 'Unknown' expected: 'Long'")),

      (config(samplePerson, nameAndLastNameSchema, nameAndLastNameSchema), valid(samplePerson)),
      (config(samplePerson, schemaPerson, nameAndLastNameSchema), valid(samplePerson)),
      (config(samplePerson, schemaPerson, nameAndLastNameSchema(schemaInteger)), valid(samplePerson)),
      (config(samplePerson, schemaPerson, nameAndLastNameSchema(schemaString)), invalidTypes("path 'age' actual: 'Long' expected: 'String'")),
      (config(samplePerson, schemaPersonWithLimits, nameAndLastNameSchema(schemaString)), invalidTypes("path 'age' actual: 'Integer' expected: 'String'")),
    )

    forAll(testData) { (config: ScenarioConfig, expected: Validated[_, RunResult[_]]) =>
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
      (sampleArrayInt,    schemaArrayInt,                schemaMapAny,                         strictAndLax,       invalidTypes("actual: 'List[Long]' expected: 'Map[String, Any]'")),
      (samplePerson,      schemaPerson,                  schemaMapAny,                         strictAndLax,       valid(samplePerson)),
      (sampleMapAny,      schemaMapAny,                  schemaMapStr,                         strict,             invalidTypes("path 'value' actual: 'Unknown' expected: 'String'")),
      (sampleMapStr,      schemaMapAny,                  schemaMapStr,                         lax,                valid(sampleMapStr)),
      (sampleMapStr,      schemaMapStr,                  schemaMapStr,                         strictAndLax,       valid(sampleMapStr)),
      (sampleMapStr,      schemaMapStringOrInt,          schemaMapStr,                         strict,             invalidTypes("path 'value' actual: 'String | Long' expected: 'String'")),
      (sampleMapStr,      schemaMapStringOrInt,          schemaMapStr,                         lax,                valid(sampleMapStr)),
      (sampleMapPerson,   schemaMapObjPerson,            schemaMapStr,                         strictAndLax,       invalidTypes("path 'value' actual: '{age: Long, first: String, last: String}' expected: 'String'")),
      (sampleMapPerson,   schemaMapObjPersonWithLimits,  schemaMapStr,                         strictAndLax,       invalidTypes("path 'value' actual: '{age: Integer, first: String, last: String}' expected: 'String'")),
      (sampleArrayInt,    schemaArrayInt,                schemaMapStr,                         strictAndLax,       invalidTypes("actual: 'List[Long]' expected: 'Map[String, String]'")),
      (samplePerson,      schemaPerson,                  schemaMapStr,                         strictAndLax,       invalidTypes("path 'age' actual: 'Long' expected: 'String'")),
      (samplePerson,      schemaPersonWithLimits,        schemaMapStr,                         strictAndLax,       invalidTypes("path 'age' actual: 'Integer' expected: 'String'")),
      (sampleMapAny,      schemaMapAny,                  schemaMapStringOrInt,                 strict,             invalidTypes("path 'value' actual: 'Unknown' expected: 'String | Long'")),
      (sampleMapInt,      schemaMapAny,                  schemaMapStringOrInt,                 lax,                valid(sampleMapInt)),
      (sampleMapStr,      schemaMapStr,                  schemaMapStringOrInt,                 strictAndLax,       valid(sampleMapStr)),
      (sampleMapStr,      schemaMapStringOrInt,          schemaMapStringOrInt,                 strictAndLax,       valid(sampleMapStr)),
      (sampleMapInt,      schemaMapStringOrInt,          schemaMapStringOrInt,                 strictAndLax,       valid(sampleMapInt)),
      (samplePerson,      schemaMapObjPerson,            schemaMapStringOrInt,                 strictAndLax,       invalidTypes("path 'value' actual: '{age: Long, first: String, last: String}' expected: 'String | Long'")),
      (samplePerson,      schemaMapObjPersonWithLimits,  schemaMapStringOrInt,                 strictAndLax,       invalidTypes("path 'value' actual: '{age: Integer, first: String, last: String}' expected: 'String | Long'")),
      (sampleArrayInt,    schemaArrayInt,                schemaMapStringOrInt,                 strictAndLax,       invalidTypes("actual: 'List[Long]' expected: 'Map[String, String | Long]'")),
      (samplePerson,      schemaPerson,                  schemaMapStringOrInt,                 strictAndLax,       valid(samplePerson)),
      (samplePerson,      schemaPerson,                  nameAndLastNameSchema,                strictAndLax,       valid(samplePerson)),
      (samplePerson,      schemaPerson,                  nameAndLastNameSchema(schemaInteger), strictAndLax,       valid(samplePerson)),
      (samplePerson,      schemaPerson,                  nameAndLastNameSchema(schemaString),  strictAndLax,       invalidTypes("path 'age' actual: 'Long' expected: 'String'")),
      (samplePerson,      schemaPersonWithLimits,        nameAndLastNameSchema(schemaString),  strictAndLax,       invalidTypes("path 'age' actual: 'Integer' expected: 'String'")),
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

  test("sink with schema with range defined on age field") {
    //@formatter:off
    val testData = Table(
      ("input", "sourceSchema", "sinkSchema", "validationModes", "result"),
      (samplePerson, schemaPerson, schemaPersonWithLimits, strictAndLax, valid(samplePerson)),
      (sampleInvalidPerson, schemaPerson, schemaPersonWithLimits, strictAndLax, invalidRanges("path 'age' actual value: '300' should be between 10 and 200")),
      (sampleInvalidPerson, schemaPerson, schemaPersonWithUpperLimits, strictAndLax, invalidRanges("path 'age' actual value: '300' should be between -inf and 200")),
      (sampleInvalidPerson, schemaPerson, schemaPersonWithLowerLimits, strictAndLax, invalidRanges("path 'age' actual value: '300' should be between 301 and +inf")),
    )
    //@formatter:on

    forAll(testData) {
      (input: Json, sourceSchema: EveritSchema, sinkSchema: EveritSchema, validationModes: List[ValidationMode], expected: Validated[_, RunResult[_]]) =>
        validationModes.foreach { mode =>
          val cfg = config(input, sourceSchema, sinkSchema, output = input, Some(mode))
          val results = runWithValueResults(cfg)
          results shouldBe expected
        }
    }
  }

  test("sink with enum schema") {
    val A = Json.fromString("A")
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
      (A,         schemaEnumABC,            schemaEnumAB,          strict,             invalidTypes("actual: 'String{A} | String{B} | String{C}' expected: 'String{A} | String{B}'")),
      (A,         schemaString,             schemaEnumABC,         strictAndLax,       valid(A)),
      (A,         schemaEnumABC,            schemaString,          strictAndLax,       valid(A)),
      (A,         schemaEnumABC,            schemaEnumAB1,         lax,                valid(A)),
      (A,         schemaEnumABC,            schemaEnumAB1,         strict,             invalidTypes("actual: 'String{A} | String{B} | String{C}' expected: 'String{A} | Integer{1} | String{B}'")),
      (A,         schemaEnumAB1,            schemaEnumAB,          lax,                valid(A)),
      (A,         schemaEnumAB1,            schemaEnumAB,          strict,             invalidTypes("actual: 'String{A} | Integer{1} | String{B}' expected: 'String{A} | String{B}'")),
      (A,         schemaEnumAB1,            schemaEnumAB1,         strictAndLax,       valid(A)),
      (one,       schemaEnumAB1,            schemaEnumAB1,         strictAndLax,       valid(one)),
      (obj,       schemaEnumStrOrObj,       schemaEnumStrOrObj,    lax,                valid(obj)),
      (A,         schemaEnumStrOrObj,       schemaEnumStrOrObj,    lax,                valid(A)),
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

  test("sink with enum list schema") {
    val cfg = config(Json.obj(), schemaObjStr, schemaEnumStrOrList, output = SpecialSpELElement("{1,2}"), lax.headOption)
    val results = runWithValueResults(cfg)

    results.isValid shouldBe true // it should be invalid, but it's so edge case that we decided to live with it
    val runtimeError = results.validValue.errors.head
    runtimeError.nodeComponentInfo.get.nodeId shouldBe "my-sink"
    runtimeError.throwable.asInstanceOf[RuntimeException].getMessage shouldBe "#: [1,2] is not a valid enum value"
  }

  test("patternProperties handling") {
    val objWithIntPatternPropsAndOpenAdditionalSchema = createObjectSchemaWithPatternProperties(Map("foo_int" -> schemaInteger))
    val objWithPatternPropsAndStringAdditionalSchema = createObjectSchemaWithPatternProperties(Map("foo_int" -> schemaInteger), Some(schemaString))
    val objWithDefinedPropsPatternPropsAndAdditionalSchema = createObjectSchemaWithPatternProperties(Map("foo_int" -> schemaInteger), Some(schemaString), Map("definedProp" -> schemaString))

    val inputObjectIntPropValue = fromInt(1)
    val inputObjectDefinedPropValue = fromString("someString")
    val inputObject = obj("foo_int" -> inputObjectIntPropValue)
    val inputObjectWithDefinedProp = obj("definedProp" -> inputObjectDefinedPropValue)

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
      (inputObject,                  objWithPatternPropsAndStringAdditionalSchema,         schemaInteger,                                  SpecialSpELElement("#input['foo_int']"),      lax,                  valid(inputObjectIntPropValue)),
      (inputObject,                  objWithPatternPropsAndStringAdditionalSchema,         schemaInteger,                                  SpecialSpELElement("#input['foo_int']"),      strict,               invalidTypes("actual: 'Long | String' expected: 'Long'")),
      (inputObject,                  objWithDefinedPropsPatternPropsAndAdditionalSchema,   schemaInteger,                                  SpecialSpELElement("#input['foo_int']"),      lax,                  Invalid(NonEmptyList(ExpressionParserCompilationError("Dynamic property access is not allowed", "my-sink", Some("Value"), "#input['foo_int']"), Nil))),
      (inputObjectWithDefinedProp,   objWithDefinedPropsPatternPropsAndAdditionalSchema,   schemaString,                                   SpecialSpELElement("#input.definedProp"),     strict,               valid(inputObjectDefinedPropValue)),
    )
    //@formatter:on

    forAll(testData) {
      (input: Json, sourceSchema: EveritSchema, sinkSchema: EveritSchema, sinkExpression: SpecialSpELElement, validationModes: List[ValidationMode], expected: Validated[_, RunResult[_]]) =>
        validationModes.foreach { mode =>
          val cfg = config(input, sourceSchema, sinkSchema, output = sinkExpression, Some(mode))
          val results = runWithValueResults(cfg)
          results shouldBe expected
        }
    }
  }

  test("pattern properties validations should work in editor mode") {
    def scenario(config: ScenarioConfig, fieldsExpressions: Map[String, String]): CanonicalProcess = {
      val sinkParams = (Map(
        TopicParamName -> s"'${config.sinkTopic}'",
        SchemaVersionParamName -> s"'${SchemaVersionOption.LatestOptionName}'",
        SinkKeyParamName -> "",
        SinkRawEditorParamName -> "false",
      ) ++ fieldsExpressions).mapValuesNow(Expression("spel", _))

      ScenarioBuilder
        .streamingLite("check json validation")
        .source(sourceName, KafkaUniversalName,
          TopicParamName -> s"'${config.sourceTopic}'",
          SchemaVersionParamName -> s"'${SchemaVersionOption.LatestOptionName}'"
        )
        .emptySink(sinkName, KafkaUniversalName, sinkParams.toList: _*)
    }

    def invalidTypeInEditorMode(fieldName: String, error: String): Invalid[NonEmptyList[CustomNodeError]] = {
      val finalMessage = OutputValidatorErrorsMessageFormatter.makeMessage(List(error), Nil, Nil, Nil)
      Invalid(NonEmptyList.one(CustomNodeError(sinkName, finalMessage, Some(fieldName))))
    }

    val objWithNestedPatternPropertiesMapSchema = createObjSchema(true, false, createObjectSchemaWithPatternProperties(Map("_int$" -> schemaInteger)))
    val objectWithNettedPatternPropertiesMapAsRefSchema = JsonSchemaBuilder.parseSchema(
      """{
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
      (objWithNestedPatternPropertiesMapSchema, Map("field" -> "{'foo_int': 1}"), valid(obj("field" -> obj("foo_int" -> fromInt(1))))),
      (objWithNestedPatternPropertiesMapSchema, Map("field" -> "{'foo_int': '1'}"), invalidTypeInEditorMode("field", "actual: 'String{1}' expected: 'Long'")),
      (objectWithNettedPatternPropertiesMapAsRefSchema, Map("field" -> "{'foo_int': 1}"), valid(obj("field" -> obj("foo_int" -> fromInt(1))))),
      (objectWithNettedPatternPropertiesMapAsRefSchema, Map("field" -> "{'foo_int': '1'}"), invalidTypeInEditorMode("field", "actual: 'String{1}' expected: 'Long'")),
    )

    forAll(testData) {
      (sinkSchema: EveritSchema, sinkFields: Map[String, String], expected: Validated[_, RunResult[_]]) =>
        val dummyInputObject = obj()
        val cfg = config(dummyInputObject, schemaMapAny, sinkSchema)
        val jsonScenario: CanonicalProcess = scenario(cfg, sinkFields)
        runner.registerJsonSchema(cfg.sourceTopic, cfg.sourceSchema)
        runner.registerJsonSchema(cfg.sinkTopic, cfg.sinkSchema)

        val input = KafkaConsumerRecord[String, String](cfg.sourceTopic, cfg.inputData.toString())
        val results = runner.runWithStringData(jsonScenario, List(input)).map(_.mapSuccesses(r => CirceUtil.decodeJsonUnsafe[Json](r.value(), "invalid json string")))
        results shouldBe expected
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
      (Json.fromString("X"), schemaEnumAB, "#: X is not a valid enum value"),
      (obj("foo_int" -> fromString("foo")), createObjectSchemaWithPatternProperties(Map("foo_int" -> schemaInteger)), "#/foo_int: expected type: Integer, found: String")
    )

    forAll(testData) { (input: Json, sourceSchema: EveritSchema, expected: String) =>
      // here we're testing only source runtime validation so to prevent typing issues we pass literal as sink expression
      val cfg = config(input, sourceSchema, schemaString, output = "someString")
      val results = runWithValueResults(cfg)
      val message = results.validValue.errors.head.throwable.asInstanceOf[RuntimeException].getMessage

      message shouldBe expected
    }
  }

  test("should catch runtime errors at encoding - sink") {
    val testData = Table(
      ("config", "expected"),
      (config(
        obj(),
        schemaObjStr,
        schemaObjStr,
        objOutputAsInputField),
        s"Not expected type: Null for field: 'field' with schema: $schemaString."),
      (config(
        obj("foo_int" -> fromString("foo")),
        schemaMapAny,
        createObjectSchemaWithPatternProperties(Map("foo_int" -> schemaInteger)),
        validationMode = Some(ValidationMode.lax)
      ), s"Not expected type: String for field: 'foo_int' with schema: $schemaInteger."),
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

    forAll(testData) { (input: Json, expected: Validated[_, RunResult[_]]) =>
      List(trueSchema, emptySchema).foreach { schema =>
        val results = runWithValueResults(config(input, schema, schema))
        results shouldBe expected
      }
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
