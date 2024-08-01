package pl.touk.nussknacker.engine.lite.components

import cats.data.Validated.Valid
import cats.data.{Validated, ValidatedNel}
import org.apache.avro.Schema.Type
import org.apache.avro.generic.GenericRecord
import org.apache.avro.{AvroRuntimeException, Schema}
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.errors.SerializationException
import org.scalatest.Inside
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.{TableDrivenPropertyChecks, TableFor2}
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.validation.ValidationMode
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.lite.components.utils.AvroGen.genValueForSchema
import pl.touk.nussknacker.engine.lite.components.utils.AvroTestData._
import pl.touk.nussknacker.engine.lite.components.utils.{AvroGen, ExcludedConfig}
import pl.touk.nussknacker.engine.lite.util.test.KafkaAvroConsumerRecord
import pl.touk.nussknacker.engine.schemedkafka.AvroUtils
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.SchemaVersionOption
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner.RunnerListResult
import pl.touk.nussknacker.engine.util.test.{RunListResult, RunResult}
import pl.touk.nussknacker.test.{SpecialSpELElement, ValidatedValuesDetailedMessage}

import java.nio.ByteBuffer
import scala.jdk.CollectionConverters._

class LiteKafkaUniversalAvroSchemaFunctionalTest
    extends AnyFunSuite
    with Matchers
    with ScalaCheckDrivenPropertyChecks
    with Inside
    with TableDrivenPropertyChecks
    with ValidatedValuesDetailedMessage
    with FunctionalTestMixin {

  import LiteKafkaComponentProvider._
  import SpecialSpELElement._
  import ValidationMode._
  import pl.touk.nussknacker.engine.lite.components.utils.LiteralSpELWithAvroImplicits._
  import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer._
  import pl.touk.nussknacker.engine.spel.Implicits._

  implicit override val generatorDrivenConfig: PropertyCheckConfiguration =
    PropertyCheckConfiguration(minSuccessful = 1000, minSize = 0, workers = 5)

  test("simple 1:1 #input as sink output") {
    val genScenarioConfig = for {
      schema <- AvroGen.genSchema(ExcludedConfig.Base)
      value  <- AvroGen.genValueForSchema(schema)
    } yield sConfig(value, schema, Input)

    forAll(genScenarioConfig) { config =>
      val resultsInput = runWithValueResults(config)
      val expected     = valid(config.inputData)

      resultsInput shouldBe expected
    }
  }

  test("simple 1:1 SpEL as sink output") {
    // We can't in easy way put bytes information, because ByteArray | ByteBuffer is not available at SpEL context
    val config = ExcludedConfig.Base.withGlobal(Type.BYTES, Type.FIXED)
    val genScenarioConfig = for {
      schema <- AvroGen.genSchema(config)
      value  <- genValueForSchema(schema)
    } yield (value, ScenarioConfig(randomTopic, sampleBytes, bytesSchema, schema, value.toSpELLiteral, None))

    forAll(genScenarioConfig) { case (value, config) =>
      val resultsInput = runWithValueResults(config)
      val expected     = valid(value)

      resultsInput shouldBe expected
    }
  }

  // FIXME: java.nio.ByteBuffer is not available from SpEL (sConfig(sampleString, stringSchema, bytesSchema, """T(java.nio.ByteBuffer).wrap(#input.getBytes("UTF-8"))"""), valid(ByteBuffer.wrap(sampleBytes))),

  test("should test end to end kafka avro record data at sink / source with primitive integers") {
    testEnd2End(
      Table(
        ("config", "result"),
        (sConfig(sampleInteger, longSchema, integerSchema, Input), invalidTypes("actual: 'Long' expected: 'Integer'")),
        (
          sConfig(sampleBoolean, booleanSchema, integerSchema, sampleLong),
          invalidTypes(s"actual: '${typedLong.display}' expected: 'Integer'")
        ),
        (
          sConfig(null, nullSchema, integerSchema, Input),
          invalidTypes("actual: 'Null' expected: 'Integer'")
        ),
        (
          sConfig(sampleBoolean, booleanSchema, integerSchema, Input),
          invalidTypes("actual: 'Boolean' expected: 'Integer'")
        ),
        (
          sConfig(sampleString, stringSchema, integerSchema, Input),
          invalidTypes("actual: 'String' expected: 'Integer'")
        ),
        (sConfig(sampleFloat, floatSchema, integerSchema, Input), invalidTypes("actual: 'Float' expected: 'Integer'")),
        (
          sConfig(sampleDouble, doubleSchema, integerSchema, Input),
          invalidTypes("actual: 'Double' expected: 'Integer'")
        ),
        (
          sConfig(sampleBytes, bytesSchema, integerSchema, Input),
          invalidTypes("actual: 'ByteBuffer' expected: 'Integer'")
        ),
        (
          sConfig(sampleInteger, integerSchema, null),
          invalidTypes("actual: 'Null' expected: 'Integer'")
        ),
        (
          sConfig(sampleInteger, integerSchema, sampleString),
          invalidTypes(s"actual: '${typedStr.display}' expected: 'Integer'")
        ),
        (
          sConfig(sampleInteger, integerSchema, sampleBoolean),
          invalidTypes(s"actual: '${typedBool.display}' expected: 'Integer'")
        ),
        (
          sConfig(sampleInteger, integerSchema, sampleFloat),
          invalidTypes(s"actual: '${typedFloat.display}' expected: 'Integer'")
        ),
        (
          sConfig(sampleInteger, integerSchema, sampleDouble),
          invalidTypes(s"actual: '${typedDouble.display}' expected: 'Integer'")
        ),
      )
    )
  }

  test("should test end to end kafka avro record data at sink with Unknown type") {
    testEnd2End(
      Table(
        ("config", "result"),
        (
          sConfig(null, nullSchema, integerSchema, SpecialSpELElement(s"""{$sampleInteger, "$sampleString"}[0]""")),
          invalidTypes(s"actual: 'Unknown' expected: 'Integer'")
        ),
        (
          sConfig(
            null,
            nullSchema,
            integerSchema,
            SpecialSpELElement(s"""{$sampleInteger, "$sampleString"}[0]"""),
            Some(ValidationMode.lax)
          ),
          valid(sampleInteger)
        ),
      )
    )
  }

  test("should test end to end kafka avro record data at sink / source with primitive longs") {
    testEnd2End(
      Table(
        ("config", "result"),
        (sConfig(sampleInteger, integerSchema, longSchema, Input), valid(sampleInteger.toLong)),
        (sConfig(sampleBoolean, booleanSchema, longSchema, sampleInteger), valid(sampleInteger.toLong)),
        (sConfig(null, nullSchema, longSchema, Input), invalidTypes("actual: 'Null' expected: 'Long'")),
        (sConfig(sampleBoolean, booleanSchema, longSchema, Input), invalidTypes("actual: 'Boolean' expected: 'Long'")),
        (sConfig(sampleString, stringSchema, longSchema, Input), invalidTypes("actual: 'String' expected: 'Long'")),
        (sConfig(sampleFloat, floatSchema, longSchema, Input), invalidTypes("actual: 'Float' expected: 'Long'")),
        (sConfig(sampleDouble, doubleSchema, longSchema, Input), invalidTypes("actual: 'Double' expected: 'Long'")),
        (sConfig(sampleBytes, bytesSchema, longSchema, Input), invalidTypes("actual: 'ByteBuffer' expected: 'Long'")),
        (sConfig(sampleLong, longSchema, null), invalidTypes("actual: 'Null' expected: 'Long'")),
        (
          sConfig(sampleLong, longSchema, sampleString),
          invalidTypes(s"actual: '${typedStr.display}' expected: 'Long'")
        ),
        (
          sConfig(sampleLong, longSchema, sampleBoolean),
          invalidTypes(s"actual: '${typedBool.display}' expected: 'Long'")
        ),
        (
          sConfig(sampleLong, longSchema, sampleFloat),
          invalidTypes(s"actual: '${typedFloat.display}' expected: 'Long'")
        ),
        (
          sConfig(sampleLong, longSchema, sampleDouble),
          invalidTypes(s"actual: '${typedDouble.display}' expected: 'Long'")
        ),
      )
    )
  }

  test("should test end to end kafka avro record data at sink / source with primitive float") {
    testEnd2End(
      Table(
        ("config", "result"),
        (sConfig(sampleDouble, doubleSchema, floatSchema, Input), invalidTypes("actual: 'Double' expected: 'Float'")),
        (
          sConfig(sampleBoolean, booleanSchema, floatSchema, sampleDouble),
          invalidTypes(s"actual: '${typedDouble.display}' expected: 'Float'")
        ),
        (sConfig(sampleInteger, integerSchema, floatSchema, Input), valid(sampleInteger.toFloat)),
        (sConfig(sampleBoolean, booleanSchema, floatSchema, sampleInteger), valid(sampleInteger.toFloat)),
        (sConfig(sampleLong, longSchema, floatSchema, Input), valid(sampleLong.toFloat)),
        (sConfig(sampleBoolean, booleanSchema, floatSchema, sampleLong), valid(sampleLong.toFloat)),
        (sConfig(null, nullSchema, floatSchema, Input), invalidTypes("actual: 'Null' expected: 'Float'")),
        (
          sConfig(sampleBoolean, booleanSchema, floatSchema, Input),
          invalidTypes("actual: 'Boolean' expected: 'Float'")
        ),
        (sConfig(sampleString, stringSchema, floatSchema, Input), invalidTypes("actual: 'String' expected: 'Float'")),
        (sConfig(sampleBytes, bytesSchema, floatSchema, Input), invalidTypes("actual: 'ByteBuffer' expected: 'Float'")),
        (sConfig(sampleFloat, floatSchema, null), invalidTypes("actual: 'Null' expected: 'Float'")),
        (
          sConfig(sampleFloat, floatSchema, sampleString),
          invalidTypes(s"actual: '${typedStr.display}' expected: 'Float'")
        ),
        (
          sConfig(sampleFloat, floatSchema, sampleBoolean),
          invalidTypes(s"actual: '${typedBool.display}' expected: 'Float'")
        ),
      )
    )
  }

  test("should test end to end kafka avro record data at sink / source with primitive double") {
    testEnd2End(
      Table(
        ("config", "result"),
        (sConfig(sampleFloat, floatSchema, doubleSchema, Input), valid(sampleFloat.toDouble)),
        (sConfig(sampleBoolean, booleanSchema, doubleSchema, sampleFloat), valid(sampleFloat.toDouble)),
        (sConfig(sampleInteger, integerSchema, doubleSchema, Input), valid(sampleInteger.toDouble)),
        (sConfig(sampleBoolean, booleanSchema, doubleSchema, sampleInteger), valid(sampleInteger.toDouble)),
        (sConfig(sampleLong, longSchema, doubleSchema, Input), valid(sampleLong.toDouble)),
        (sConfig(sampleLong, longSchema, doubleSchema, sampleLong), valid(sampleLong.toDouble)),
        (sConfig(null, nullSchema, doubleSchema, Input), invalidTypes("actual: 'Null' expected: 'Double'")),
        (
          sConfig(sampleBoolean, booleanSchema, doubleSchema, Input),
          invalidTypes("actual: 'Boolean' expected: 'Double'")
        ),
        (sConfig(sampleString, stringSchema, doubleSchema, Input), invalidTypes("actual: 'String' expected: 'Double'")),
        (
          sConfig(sampleBytes, bytesSchema, doubleSchema, Input),
          invalidTypes("actual: 'ByteBuffer' expected: 'Double'")
        ),
        (sConfig(sampleDouble, doubleSchema, null), invalidTypes("actual: 'Null' expected: 'Double'")),
        (
          sConfig(sampleDouble, doubleSchema, sampleString),
          invalidTypes(s"actual: '${typedStr.display}' expected: 'Double'")
        ),
        (
          sConfig(sampleDouble, doubleSchema, sampleBoolean),
          invalidTypes(s"actual: '${typedBool.display}' expected: 'Double'")
        ),
      )
    )
  }

  test("should test end to end kafka avro record data at sink / source with union field validations") {
    testEnd2End(
      Table(
        ("config", "result"),
        (
          rConfig(sampleInteger, recordUnionStringAndIntegerSchema, recordUnionStringAndIntegerSchema, Input),
          rValid(sampleInteger, recordUnionStringAndIntegerSchema)
        ),
        (
          rConfig(sampleInteger, recordIntegerSchema, recordUnionStringAndIntegerSchema, Input),
          rValid(sampleInteger, recordUnionStringAndIntegerSchema)
        ),
        (
          rConfig(sampleBoolean, recordBooleanSchema, recordUnionStringAndIntegerSchema, sampleInteger),
          rValid(sampleInteger, recordUnionStringAndIntegerSchema)
        ),
        (
          rConfig(sampleString, recordUnionStringAndIntegerSchema, recordUnionStringAndIntegerSchema, Input),
          rValid(sampleString, recordUnionStringAndIntegerSchema)
        ),
        (
          rConfig(sampleString, recordStringSchema, recordUnionStringAndIntegerSchema, Input),
          rValid(sampleString, recordUnionStringAndIntegerSchema)
        ),
        (
          rConfig(sampleBoolean, recordBooleanSchema, recordUnionStringAndIntegerSchema, sampleString),
          rValid(sampleString, recordUnionStringAndIntegerSchema)
        ),
        (
          rConfig(
            sampleUnionStringAndRecordInt,
            recordUnionStringAndRecordIntSchema,
            recordUnionRecordIntAndStringSchema,
            Input
          ),
          valid(sampleUnionRecordIntAndString)
        ),
        (
          rConfig(
            sampleUnionRecordIntAndString,
            recordUnionRecordIntAndStringSchema,
            recordUnionStringAndRecordIntSchema,
            Input
          ),
          valid(sampleUnionStringAndRecordInt)
        ),
        (
          rConfig(
            sampleUnionRecordLongAndString,
            recordUnionRecordLongAndStringSchema,
            recordUnionRecordIntAndStringSchema,
            Input
          ),
          invalidTypes(
            "path 'field' actual: 'Record{field: Long} | String' expected: 'Record{field: Integer} | String'"
          )
        ),
        (
          rConfig(
            sampleUnionRecordIntAndString,
            recordUnionRecordIntAndStringSchema,
            recordUnionRecordLongAndStringSchema,
            Input
          ),
          valid(sampleUnionRecordLongAndString)
        ),
        (
          rConfig(
            sampleUnionMapOfLongsAndLong,
            recordUnionMapOfLongsAndLongSchema,
            recordUnionMapOfIntsAndIntSchema,
            Input
          ),
          invalidTypes(
            "path 'field' actual: 'Long | Map[String,Long]' expected: 'Map[String,Null | Integer] | Integer'"
          )
        ),
        (
          rConfig(
            sampleUnionMapOfIntsAndInt,
            recordUnionMapOfIntsAndIntSchema,
            recordUnionMapOfLongsAndLongSchema,
            Input
          ),
          valid(sampleUnionMapOfLongsAndLong)
        ),
        (
          rConfig(sampleUnionMapOfIntsAndInt, recordUnionMapOfIntsAndIntSchema, recordMapOfIntsSchema, Input),
          invalidTypes("path 'field' actual: 'Integer | Map[String,Integer]' expected: 'Map[String,Null | Integer]'")
        ),
        (
          rConfig(
            sampleUnionMapOfIntsAndInt,
            recordUnionMapOfIntsAndIntSchema,
            recordMapOfIntsSchema,
            Input,
            Some(ValidationMode.lax)
          ),
          valid(sampleMapOfIntsAndInt)
        ),
        (
          rConfig(sampleString, recordUnionStringAndRecordIntSchema, recordUnionRecordIntAndStringSchema, Input),
          rValid(sampleString, recordUnionRecordIntAndStringSchema)
        ),
        (
          rConfig(sampleString, recordUnionRecordIntAndStringSchema, recordUnionRecordIntAndStringSchema, Input),
          rValid(sampleString, recordUnionRecordIntAndStringSchema)
        ),
        (
          sConfig(sampleString, stringSchema, recordUnionStringAndIntegerSchema, Input),
          invalidTypes("actual: 'String' expected: 'Record{field: String | Integer}'")
        ),
        (
          rConfig(sampleBoolean, recordUnionStringAndBooleanSchema, recordUnionStringAndIntegerSchema, Input),
          invalidTypes("path 'field' actual: 'Boolean | String' expected: 'String | Integer'")
        ),
        (
          rConfig(sampleBoolean, recordMaybeBooleanSchema, recordUnionStringAndIntegerSchema, Input),
          invalidTypes("path 'field' actual: 'Boolean' expected: 'String | Integer'")
        ),
      )
    )
  }

  test(
    "should test end to end kafka avro record data at sink / source with input as output with conversion - schema evolution"
  ) {
    testEnd2End(
      Table(
        ("config", "result"),
        (
          rConfig(sampleLong, recordLongSchema, recordIntegerSchema, Input),
          invalidTypes(s"path 'field' actual: 'Long' expected: 'Integer'")
        ),
        (
          rConfig(sampleFloat, recordFloatSchema, recordIntegerSchema, Input),
          invalidTypes(s"path 'field' actual: 'Float' expected: 'Integer'")
        ),
        (
          rConfig(sampleDouble, recordDoubleSchema, recordIntegerSchema, Input),
          invalidTypes(s"path 'field' actual: 'Double' expected: 'Integer'")
        ),
        (
          rConfig(sampleInteger, recordIntegerSchema, recordLongSchema, Input),
          rValid(sampleInteger.toLong, recordLongSchema)
        ), // avro allows to pass Integer as Long
        (
          rConfig(sampleFloat, recordFloatSchema, recordLongSchema, Input),
          invalidTypes(s"path 'field' actual: 'Float' expected: 'Long'")
        ),
        (
          rConfig(sampleDouble, recordDoubleSchema, recordLongSchema, Input),
          invalidTypes(s"path 'field' actual: 'Double' expected: 'Long'")
        ),
        (
          rConfig(sampleInteger, recordIntegerSchema, recordFloatSchema, Input),
          rValid(sampleInteger.toFloat, recordFloatSchema)
        ), // avro allows to pass Integer as Float
        (
          rConfig(sampleLong, recordLongSchema, recordFloatSchema, Input),
          rValid(sampleLong.toFloat, recordFloatSchema)
        ), // avro allows to pass Long as Float
        (
          rConfig(sampleDouble, recordDoubleSchema, recordFloatSchema, Input),
          invalidTypes(s"path 'field' actual: 'Double' expected: 'Float'")
        ),
        (
          rConfig(sampleInteger, recordIntegerSchema, recordDoubleSchema, Input),
          rValid(sampleInteger.toDouble, recordDoubleSchema)
        ), // avro allows to pass Integer as Double
        (
          rConfig(sampleLong, recordLongSchema, recordDoubleSchema, Input),
          rValid(sampleLong.toDouble, recordDoubleSchema)
        ), // avro allows to pass Long as Double
        (
          rConfig(sampleFloat, recordFloatSchema, recordDoubleSchema, Input),
          rValid(sampleFloat.toDouble, recordDoubleSchema)
        ), // avro allows to pass Float as Double
      )
    )
  }

  test("should test end to end kafka avro record data at sink / source with input as output with arrays") {
    testEnd2End(
      Table(
        ("config", "result"),
        (sConfig(List("12").asJava, arrayOfStringsSchema, arrayOfStringsSchema, Input), valid(List("12").asJava)),
        (
          rConfig(List("12"), recordArrayOfStringsSchema, recordArrayOfNumbersSchema, Input),
          invalidTypes("path 'field[]' actual: 'String' expected: 'Integer | Double'")
        ),
        (
          rConfig(sampleInteger, recordIntegerSchema, recordArrayOfNumbersSchema, List(1.0, 2.5)),
          rValid(List(1.0, 2.5), recordArrayOfNumbersSchema)
        ),
        (
          rConfig(sampleInteger, recordIntegerSchema, recordArrayOfNumbersSchema, List(sampleString)),
          invalidTypes(s"path 'field[]' actual: '${typedStr.withoutValue.display}' expected: 'Integer | Double'")
        ),
        // FIXME: List[Unknown] (rConfig(sampleInteger, recordIntegerSchema, recordWithArrayOfNumbers, s"""{$sampleBoolean, "$sampleString"}"""), invalidTypes(s"path 'field[]' actual: '${typeBool.display} | ${typeStr.display}' expected: 'Integer | Double'")),
        (
          rConfig(sampleInteger, recordIntegerSchema, recordArrayOfNumbersSchema, sampleInteger),
          invalidTypes(s"path 'field' actual: '${typedInt.display}' expected: 'List[Integer | Double]'")
        ),
        (
          rConfig(sampleInteger, recordIntegerSchema, recordArrayOfNumbersSchema, null),
          invalidTypes("path 'field' actual: 'Null' expected: 'List[Integer | Double]'")
        ),
        (
          rConfig(List("12"), recordArrayOfStringsSchema, recordMaybeArrayOfNumbersSchema, Input),
          invalidTypes("path 'field[]' actual: 'String' expected: 'Integer | Double'")
        ),
        (
          rConfig(sampleInteger, recordIntegerSchema, recordMaybeArrayOfNumbersSchema, List(1.0, 2.5)),
          rValid(List(1.0, 2.5), recordMaybeArrayOfNumbersSchema)
        ),
        (
          rConfig(sampleInteger, recordIntegerSchema, recordMaybeArrayOfNumbersSchema, List(sampleString)),
          invalidTypes(s"path 'field[]' actual: '${typedStr.withoutValue.display}' expected: 'Integer | Double'")
        ),
        // FIXME: List[Unknown] (rConfig(sampleInteger, recordIntegerSchema, recordWithMaybeArrayOfNumbers, s"""{$sampleBoolean, "$sampleString"}"""), invalidTypes("path 'field[]' actual: '${typeBool.display} | ${typeStr.display}' expected: 'Integer | Double'")),
        (
          rConfig(sampleInteger, recordIntegerSchema, recordMaybeArrayOfNumbersSchema, sampleInteger),
          invalidTypes(s"path 'field' actual: '${typedInt.display}' expected: 'Null | List[Integer | Double]'")
        ),
        (
          rConfig(sampleInteger, recordIntegerSchema, recordMaybeArrayOfNumbersSchema, null),
          rValid(null, recordMaybeArrayOfNumbersSchema)
        ),
        (
          rConfig(List("12"), recordArrayOfStringsSchema, recordOptionalArrayOfNumbersSchema, Input),
          invalidTypes("path 'field[]' actual: 'String' expected: 'Integer | Double'")
        ),
        (
          rConfig(sampleInteger, recordIntegerSchema, recordOptionalArrayOfNumbersSchema, List(1.0, 2.5)),
          rValid(List(1.0, 2.5), recordOptionalArrayOfNumbersSchema)
        ),
        // FIXME: List[Unknown]  (rConfig(sampleInteger, recordIntegerSchema, recordWithMaybeArrayOfNumbers, s"""{$sampleBoolean, "$sampleString"}"""), invalidTypes("path 'field[]' actual: '${typeBool.display} | ${typeStr.display}' expected: 'Integer | Double'")),
        (
          rConfig(sampleInteger, recordIntegerSchema, recordMaybeArrayOfNumbersSchema, List(sampleString)),
          invalidTypes(s"path 'field[]' actual: '${typedStr.withoutValue.display}' expected: 'Integer | Double'")
        ),
        (
          rConfig(sampleInteger, recordIntegerSchema, recordOptionalArrayOfNumbersSchema, sampleInteger),
          invalidTypes(s"path 'field' actual: '${typedInt.display}' expected: 'Null | List[Integer | Double]'")
        ),
        (
          rConfig(sampleInteger, recordIntegerSchema, recordOptionalArrayOfNumbersSchema, null),
          rValid(null, recordOptionalArrayOfNumbersSchema)
        ),
        (
          rConfig(sampleInteger, recordIntegerSchema, recordOptionalArrayOfNumbersSchema, EmptyMap),
          invalid(Nil, List("field"), Nil)
        ),
        (
          rConfig(sampleInteger, recordIntegerSchema, recordOptionalArrayOfNumbersSchema, EmptyMap, Some(lax)),
          rValid(null, recordOptionalArrayOfNumbersSchema)
        ),
        (
          rConfig(
            List(List("12")),
            recordOptionalArrayOfArraysStringsSchema,
            recordOptionalArrayOfArraysNumbersSchema,
            Input
          ),
          invalidTypes("path 'field[][]' actual: 'String' expected: 'Integer | Double'")
        ),
        (
          rConfig(sampleInteger, recordIntegerSchema, recordOptionalArrayOfArraysNumbersSchema, List(List(1.0, 2.5))),
          rValid(List(List(1.0, 2.5)), recordOptionalArrayOfArraysNumbersSchema)
        ),
        // FIXME: List[Unknown]  (rConfig(sampleInteger, recordIntegerSchema, recordOptionalArrayOfArraysNumbers, s"""{{$sampleBoolean, "$sampleString"}}"""), invalidTypes("path 'field[][]' actual: 'Boolean | String' expected: 'Integer | Double'")),
        (
          rConfig(
            sampleInteger,
            recordIntegerSchema,
            recordOptionalArrayOfArraysNumbersSchema,
            List(List(sampleString))
          ),
          invalidTypes(s"path 'field[][]' actual: '${typedStr.withoutValue.display}' expected: 'Integer | Double'")
        ),
        (
          rConfig(sampleInteger, recordIntegerSchema, recordOptionalArrayOfArraysNumbersSchema, List(sampleInteger)),
          invalidTypes(
            s"path 'field[]' actual: '${typedInt.withoutValue.display}' expected: 'Null | List[Integer | Double]'"
          )
        ),
        (
          rConfig(sampleInteger, recordIntegerSchema, recordOptionalArrayOfArraysNumbersSchema, sampleInteger),
          invalidTypes(
            s"path 'field' actual: '${typedInt.display}' expected: 'Null | List[Null | List[Integer | Double]]'"
          )
        ),
        (
          rConfig(
            sampleInteger,
            recordIntegerSchema,
            recordOptionalArrayOfRecordsSchema,
            List(Map("price1" -> "15.5"))
          ),
          invalid(Nil, List("field[].price"), List("field[].price1"))
        ),
        (
          rConfig(
            sampleInteger,
            recordIntegerSchema,
            recordOptionalArrayOfRecordsSchema,
            List(Map("price1" -> "15.5")),
            Some(lax)
          ),
          invalid(Nil, List("field[].price"), Nil)
        ),
        (
          rConfig(
            sampleInteger,
            recordIntegerSchema,
            recordOptionalArrayOfRecordsSchema,
            List(Map("price" -> sampleString))
          ),
          invalidTypes(s"path 'field[].price' actual: '${typedStr.withoutValue.display}' expected: 'Null | Double'")
        ),
        (
          rConfig(sampleInteger, recordIntegerSchema, recordOptionalArrayOfRecordsSchema, sampleInteger),
          invalidTypes(
            s"""path 'field' actual: '${typedInt.display}' expected: 'Null | List[Null | Record{price: Null | Double}]'"""
          )
        ),
        (
          rConfig(sampleInteger, recordIntegerSchema, recordOptionalArrayOfRecordsSchema, List(sampleInteger)),
          invalidTypes(
            s"""path 'field[]' actual: '${typedInt.withoutValue.display}' expected: 'Null | Record{price: Null | Double}'"""
          )
        ),
      )
    )
  }

  test("should test end to end kafka avro record data at sink / source with input as output with maps") {
    testEnd2End(
      Table(
        ("config", "result"),
        (
          rConfig(Map("tax" -> "7"), recordMapOfStringsSchema, recordMapOfIntsSchema, Input),
          invalidTypes("path 'field[*]' actual: 'String' expected: 'Null | Integer'")
        ),
        (
          rConfig(Map("price" -> sampleDouble.toString), fakeMapRecordWithStringPrice, recordMapOfStringsSchema, Input),
          invalidTypes("path 'field' actual: 'Record{price: String}' expected: 'Map[String,Null | String]'")
        ),
        (
          rConfig(sampleInteger, recordIntegerSchema, recordMapOfIntsSchema, Map("tax" -> 7, "vat" -> "23")),
          invalidTypes("path 'field.vat' actual: 'String(23)' expected: 'Null | Integer'")
        ),
        (
          rConfig(sampleInteger, recordIntegerSchema, recordMapOfIntsSchema, sampleInteger),
          invalidTypes(s"path 'field' actual: '${typedInt.display}' expected: 'Map[String,Null | Integer]'")
        ),
        (
          rConfig(sampleInteger, recordIntegerSchema, recordMapOfIntsSchema, Nil),
          invalidTypes("path 'field' actual: 'List[Unknown]({})' expected: 'Map[String,Null | Integer]'")
        ),
        (
          rConfig(sampleInteger, recordIntegerSchema, recordMapOfIntsSchema, null),
          invalidTypes("path 'field' actual: 'Null' expected: 'Map[String,Null | Integer]'")
        ),
        (
          rConfig(Map("tax" -> "7"), recordMapOfStringsSchema, recordMaybeMapOfIntsSchema, Input),
          invalidTypes("path 'field[*]' actual: 'String' expected: 'Null | Integer'")
        ),
        (
          rConfig(sampleInteger, recordIntegerSchema, recordMaybeMapOfIntsSchema, Map("tax" -> 7, "vat" -> "23")),
          invalidTypes("path 'field.vat' actual: 'String(23)' expected: 'Null | Integer'")
        ),
        (
          rConfig(sampleInteger, recordIntegerSchema, recordMaybeMapOfIntsSchema, sampleInteger),
          invalidTypes(s"path 'field' actual: '${typedInt.display}' expected: 'Null | Map[String,Null | Integer]'")
        ),
        (
          rConfig(sampleInteger, recordIntegerSchema, recordMaybeMapOfIntsSchema, Nil),
          invalidTypes("path 'field' actual: 'List[Unknown]({})' expected: 'Null | Map[String,Null | Integer]'")
        ),
        (
          rConfig(
            Map("first" -> Map("tax" -> "7")),
            recordMapOfMapsStringsSchema,
            recordOptionalMapOfMapsIntsSchema,
            Input
          ),
          invalidTypes("path 'field[*][*]' actual: 'String' expected: 'Null | Integer'")
        ),
        (
          rConfig(
            sampleInteger,
            recordIntegerSchema,
            recordOptionalMapOfMapsIntsSchema,
            Map("first" -> Map("tax" -> 7, "vat" -> "23"))
          ),
          invalidTypes("path 'field.first.vat' actual: 'String(23)' expected: 'Null | Integer'")
        ),
        (
          rConfig(sampleInteger, recordIntegerSchema, recordOptionalMapOfMapsIntsSchema, sampleInteger),
          invalidTypes(
            s"path 'field' actual: '${typedInt.display}' expected: 'Null | Map[String,Null | Map[String,Null | Integer]]'"
          )
        ),
        (
          rConfig(sampleInteger, recordIntegerSchema, recordOptionalMapOfMapsIntsSchema, Map("first" -> sampleInteger)),
          invalidTypes(
            s"path 'field.first' actual: '${typedInt.display}' expected: 'Null | Map[String,Null | Integer]'"
          )
        ),
        (
          rConfig(sampleInteger, recordIntegerSchema, recordOptionalMapOfMapsIntsSchema, Nil),
          invalidTypes(
            "path 'field' actual: 'List[Unknown]({})' expected: 'Null | Map[String,Null | Map[String,Null | Integer]]'"
          )
        ),
        (
          rConfig(sampleInteger, recordIntegerSchema, recordOptionalMapOfMapsIntsSchema, null),
          rValid(null, recordOptionalMapOfMapsIntsSchema)
        ),
        (
          rConfig(sampleInteger, recordIntegerSchema, recordOptionalMapOfMapsIntsSchema, EmptyMap),
          invalid(Nil, List("field"), Nil)
        ),
        (
          rConfig(sampleInteger, recordIntegerSchema, recordOptionalMapOfMapsIntsSchema, EmptyMap, Some(lax)),
          rValid(null, recordOptionalMapOfMapsIntsSchema)
        ),
        (
          rConfig(
            Map("first" -> Map("price" -> "15.5")),
            recordOptionalMapOfStringRecordsSchema,
            recordOptionalMapOfRecordsSchema,
            Input
          ),
          invalidTypes("path 'field[*].price' actual: 'String' expected: 'Null | Double'")
        ),
        (
          rConfig(
            sampleInteger,
            recordIntegerSchema,
            recordOptionalMapOfRecordsSchema,
            Map("first" -> Map("price" -> sampleString))
          ),
          invalidTypes(s"path 'field.first.price' actual: '${typedStr.display}' expected: 'Null | Double'")
        ),
        (
          rConfig(sampleInteger, recordIntegerSchema, recordOptionalMapOfRecordsSchema, sampleInteger),
          invalidTypes(
            s"path 'field' actual: '${typedInt.display}' expected: 'Null | Map[String,Null | Record{price: Null | Double}]'"
          )
        ),
        (
          rConfig(sampleInteger, recordIntegerSchema, recordOptionalMapOfRecordsSchema, Map("first" -> sampleInteger)),
          invalidTypes(
            s"path 'field.first' actual: '${typedInt.display}' expected: 'Null | Record{price: Null | Double}'"
          )
        ),
        (
          rConfig(sampleInteger, recordIntegerSchema, recordOptionalMapOfRecordsSchema, Nil),
          invalidTypes(
            "path 'field' actual: 'List[Unknown]({})' expected: 'Null | Map[String,Null | Record{price: Null | Double}]'"
          )
        ),
        (
          rConfig(sampleInteger, recordIntegerSchema, recordOptionalMapOfRecordsSchema, null),
          rValid(null, recordOptionalMapOfRecordsSchema)
        ),
        (
          rConfig(sampleInteger, recordIntegerSchema, recordOptionalMapOfRecordsSchema, EmptyMap),
          invalid(Nil, List("field"), Nil)
        ),
        (
          rConfig(sampleInteger, recordIntegerSchema, recordOptionalMapOfRecordsSchema, EmptyMap, Some(lax)),
          rValid(null, recordOptionalMapOfRecordsSchema)
        ),
      )
    )
  }

  test("should test end to end kafka avro record data at sink / source with input as output with records") {
    testEnd2End(
      Table(
        ("config", "result"),
        (
          rConfig(Map("sub" -> Map("price" -> "15.5")), nestedRecordWithStringPriceSchema, nestedRecordSchema, Input),
          invalidTypes("path 'field.sub.price' actual: 'String' expected: 'Null | Double'")
        ),
        (
          rConfig(sampleInteger, recordIntegerSchema, nestedRecordSchema, Map("sub" -> Map("price2" -> sampleDouble))),
          invalid(Nil, List("field.sub.price"), List("field.sub.price2"))
        ),
        (
          rConfig(
            sampleInteger,
            recordIntegerSchema,
            nestedRecordSchema,
            Map("sub" -> Map("price2" -> sampleDouble)),
            Some(lax)
          ),
          invalid(Nil, List("field.sub.price"), Nil)
        ),
        (
          rConfig(sampleInteger, recordIntegerSchema, nestedRecordSchema, Map("sub" -> Map("price" -> sampleString))),
          invalidTypes(s"path 'field.sub.price' actual: '${typedStr.display}' expected: 'Null | Double'")
        ),
        (
          rConfig(sampleInteger, recordIntegerSchema, nestedRecordSchema, Input),
          invalidTypes(
            "path 'field' actual: 'Integer' expected: 'Null | Record{sub: Null | Record{price: Null | Double}}'"
          )
        ),
        (
          rConfig(sampleNestedRecord, nestedRecordSchema, nestedRecordSchema, sampleInteger),
          invalidTypes(
            s"path 'field' actual: '${typedInt.display}' expected: 'Null | Record{sub: Null | Record{price: Null | Double}}'"
          )
        ),
        (
          rConfig(sampleInteger, recordIntegerSchema, nestedRecordSchema, Map("sub" -> sampleInteger)),
          invalidTypes(
            s"path 'field.sub' actual: '${typedInt.display}' expected: 'Null | Record{price: Null | Double}'"
          )
        ),
        (
          rConfig(sampleInteger, recordIntegerSchema, nestedRecordSchema, Nil),
          invalidTypes(
            "path 'field' actual: 'List[Unknown]({})' expected: 'Null | Record{sub: Null | Record{price: Null | Double}}'"
          )
        ),
        (
          rConfig(sampleNestedRecordV2, nestedRecordSchemaV2, nestedRecordSchema, Input),
          invalid(Nil, Nil, List("field.sub.currency", "field.str"))
        ),
        (
          rConfig(sampleNestedRecordV2, nestedRecordSchemaV2, nestedRecordSchema, Input, Some(lax)),
          valid(sampleNestedRecord)
        ),
        (
          rConfig(sampleNestedRecord, nestedRecordSchema, nestedRecordSchemaV2, Input),
          invalid(Nil, List("field.str", "field.sub.currency"), Nil)
        ),
        (
          rConfig(sampleString, recordStringSchema, recordWithBigUnionSchema, Input),
          invalidTypes(
            "path 'field' actual: 'String' expected: 'Null | Boolean | Record{sub: Null | Record{price: Null | String}} | Record{sub: Null | Record{price: Null | Double}}'"
          )
        ),
      )
    )
  }

  test("should test end to end kafka avro record data at sink / source with input as output with enum") {
    testEnd2End(
      Table(
        ("config", "result"),
        (
          rConfig(sampleInteger, recordIntegerSchema, recordEnumSchema, sampleEnumString),
          rValid(sampleEnum, recordEnumSchema)
        ),
        (
          rConfig(sampleInteger, recordIntegerSchema, recordEnumSchema, sampleStrEnumV2),
          invalidTypes(
            s"path 'field' actual: '${typedStrEnumV2.display}' expected: 'EnumSymbol[SPADES | HEARTS | DIAMONDS | CLUBS] | String'"
          )
        ),
        (
          rConfig(sampleInteger, recordIntegerSchema, recordEnumSchema, Input),
          invalidTypes(
            "path 'field' actual: 'Integer' expected: 'EnumSymbol[SPADES | HEARTS | DIAMONDS | CLUBS] | String'"
          )
        ),
        (
          rConfig(sampleInteger, recordIntegerSchema, recordEnumSchema, sampleInteger),
          invalidTypes(
            s"path 'field' actual: '${typedInt.display}' expected: 'EnumSymbol[SPADES | HEARTS | DIAMONDS | CLUBS] | String'"
          )
        ),
      )
    )
  }

  test("should test end to end kafka avro record data at sink / source with input as output with fixed") {
    testEnd2End(
      Table(
        ("config", "result"),
        (rConfig(sampleFixed, recordFixedSchema, recordFixedSchema, Input), rValid(sampleFixed, recordFixedSchema)),
        (
          rConfig(sampleInteger, recordIntegerSchema, recordFixedSchema, sampleStrFixed),
          rValid(sampleFixed, recordFixedSchema)
        ),
        (
          rConfig(sampleInteger, recordIntegerSchema, recordFixedSchema, sampleStrFixedV),
          invalidTypes(s"path 'field' actual: '${typeStrFixedV2.display}' expected: 'Fixed[32] | ByteBuffer | String'")
        ),
        (
          rConfig(sampleInteger, recordIntegerSchema, recordFixedSchema, Input),
          invalidTypes("path 'field' actual: 'Integer' expected: 'Fixed[32] | ByteBuffer | String'")
        ),
        (
          rConfig(sampleInteger, recordIntegerSchema, recordFixedSchema, sampleInteger),
          invalidTypes(s"path 'field' actual: '${typedInt.display}' expected: 'Fixed[32] | ByteBuffer | String'")
        ),
      )
    )
  }

  test(
    "should test end to end kafka avro record data at sink / source with input as output with Logical: UUID validations"
  ) {
    testEnd2End(
      Table(
        ("config", "result"),
        (rConfig(sampleUUID, recordUUIDSchema, recordUUIDSchema, Input), rValid(sampleUUID, recordUUIDSchema)),
        (
          rConfig(sampleUUID.toString, recordStringSchema, recordUUIDSchema, Input),
          rValid(sampleUUID, recordUUIDSchema)
        ),
        (
          rConfig(sampleInteger, recordIntegerSchema, recordUUIDSchema, sampleUUID),
          rValid(sampleUUID, recordUUIDSchema)
        ),
        (
          rConfig(sampleInteger, recordIntegerSchema, recordUUIDSchema, sampleUUID.toString),
          rValid(sampleUUID, recordUUIDSchema)
        ),
        (
          rConfig(sampleInteger, recordIntegerSchema, recordUUIDSchema, Input),
          invalidTypes("path 'field' actual: 'Integer' expected: 'UUID | String'")
        ),
        (
          rConfig(sampleInteger, recordIntegerSchema, recordUUIDSchema, sampleInteger),
          invalidTypes(s"path 'field' actual: '${typedInt.display}' expected: 'UUID | String'")
        ),
        (
          rConfig(sampleInteger, recordIntegerSchema, recordUUIDSchema, sampleString),
          invalidTypes(s"path 'field' actual: '${typedStr.display}' expected: 'UUID | String'")
        ),
      )
    )
  }

  test(
    "should test end to end kafka avro record data at sink / source with input as output with Logical: BigDecimal validations"
  ) {
    testEnd2End(
      Table(
        ("config", "result"),
        (
          rConfig(sampleDecimal, recordDecimalSchema, recordDecimalSchema, Input),
          rValid(sampleDecimal, recordDecimalSchema)
        ),
        (
          rConfig(sampleString, recordStringSchema, recordDecimalSchema, sampleDecimal),
          rValid(sampleDecimal, recordDecimalSchema)
        ),
        (
          rConfig(sampleString, recordStringSchema, recordDecimalSchema, sampleDouble),
          invalidTypes(s"path 'field' actual: 'Double($sampleDouble)' expected: 'BigDecimal | ByteBuffer'")
        ),
        (
          rConfig(sampleInteger, recordIntegerSchema, recordDecimalSchema, Input),
          invalidTypes("path 'field' actual: 'Integer' expected: 'BigDecimal | ByteBuffer'")
        ),
        (
          rConfig(sampleInteger, recordIntegerSchema, recordDecimalSchema, sampleInteger),
          invalidTypes(s"path 'field' actual: '${typedInt.display}' expected: 'BigDecimal | ByteBuffer'")
        ),
      )
    )
  }

  test(
    "should test end to end kafka avro record data at sink / source with input as output with Logical: Date validations -> LocalDate"
  ) {
    testEnd2End(
      Table(
        ("config", "result"),
        (rConfig(sampleDate, recordDateSchema, recordDateSchema, Input), rValid(sampleDate, recordDateSchema)),
        (rConfig(sampleString, recordStringSchema, recordDateSchema, sampleDate), rValid(sampleDate, recordDateSchema)),
        (
          rConfig(sampleDate.toEpochDay, recordIntegerSchema, recordDateSchema, Input),
          rValid(sampleDate, recordDateSchema)
        ),
        (
          rConfig(sampleString, recordStringSchema, recordDateSchema, sampleDate.toEpochDay.toInt),
          rValid(sampleDate, recordDateSchema)
        ),
        (
          rConfig(sampleString, recordStringSchema, recordDateSchema, Input),
          invalidTypes("path 'field' actual: 'String' expected: 'LocalDate | Integer'")
        ),
        (
          rConfig(sampleDate, recordDateSchema, recordDateSchema, sampleString),
          invalidTypes(s"path 'field' actual: '${typedStr.display}' expected: 'LocalDate | Integer'")
        ),
      )
    )
  }

  test(
    "should test end to end kafka avro record data at sink / source with input as output with Logical: Time Millis -> LocalTime"
  ) {
    testEnd2End(
      Table(
        ("config", "result"),
        (
          rConfig(sampleMillisLocalTime, recordTimeMillisSchema, recordTimeMillisSchema, Input),
          rValid(sampleMillisLocalTime, recordTimeMillisSchema)
        ),
        (
          rConfig(sampleString, recordStringSchema, recordTimeMillisSchema, sampleMillisLocalTime),
          rValid(sampleMillisLocalTime, recordTimeMillisSchema)
        ),
        (
          rConfig(sampleMillisLocalTime.toMillis, recordIntegerSchema, recordTimeMillisSchema, Input),
          rValid(sampleMillisLocalTime, recordTimeMillisSchema)
        ),
        (
          rConfig(sampleString, recordStringSchema, recordTimeMillisSchema, sampleMillisLocalTime.toMillis),
          rValid(sampleMillisLocalTime, recordTimeMillisSchema)
        ),
        (
          rConfig(sampleString, recordStringSchema, recordTimeMillisSchema, Input),
          invalidTypes("path 'field' actual: 'String' expected: 'LocalTime | Integer'")
        ),
        (
          rConfig(sampleBoolean, recordBooleanSchema, recordTimeMillisSchema, sampleString),
          invalidTypes(s"path 'field' actual: '${typedStr.display}' expected: 'LocalTime | Integer'")
        ),
      )
    )
  }

  test(
    "should test end to end kafka avro record data at sink / source with input as output with Logical: Time Micros -> LocalTime"
  ) {
    testEnd2End(
      Table(
        ("config", "result"),
        (
          rConfig(sampleMicrosLocalTime, recordTimeMicrosSchema, recordTimeMicrosSchema, Input),
          rValid(sampleMicrosLocalTime, recordTimeMicrosSchema)
        ),
        (
          rConfig(sampleString, recordStringSchema, recordTimeMicrosSchema, sampleMicrosLocalTime),
          rValid(sampleMicrosLocalTime, recordTimeMicrosSchema)
        ),
        (
          rConfig(sampleMicrosLocalTime.toMicros, recordLongSchema, recordTimeMicrosSchema, Input),
          rValid(sampleMicrosLocalTime, recordTimeMicrosSchema)
        ),
        (
          rConfig(sampleString, recordStringSchema, recordTimeMicrosSchema, sampleMicrosLocalTime.toMicros),
          rValid(sampleMicrosLocalTime, recordTimeMicrosSchema)
        ),
        (
          rConfig(sampleString, recordStringSchema, recordTimeMicrosSchema, Input),
          invalidTypes("path 'field' actual: 'String' expected: 'LocalTime | Long'")
        ),
        (
          rConfig(sampleBoolean, recordBooleanSchema, recordTimeMicrosSchema, sampleString),
          invalidTypes(s"path 'field' actual: '${typedStr.display}' expected: 'LocalTime | Long'")
        ),
      )
    )
  }

  test(
    "should test end to end kafka avro record data at sink / source with input as output with Logical: Timestamp Millis -> Instant"
  ) {
    testEnd2End(
      Table(
        ("config", "result"),
        (
          rConfig(sampleMillisInstant, recordTimestampMillisSchema, recordTimestampMillisSchema, Input),
          rValid(sampleMillisInstant, recordTimestampMillisSchema)
        ),
        (
          rConfig(sampleString, recordStringSchema, recordTimestampMillisSchema, sampleMillisInstant),
          rValid(sampleMillisInstant, recordTimestampMillisSchema)
        ),
        (
          rConfig(sampleMillisInstant.toEpochMilli, recordLongSchema, recordTimestampMillisSchema, Input),
          rValid(sampleMillisInstant, recordTimestampMillisSchema)
        ),
        (
          rConfig(sampleString, recordStringSchema, recordTimestampMillisSchema, sampleMillisInstant.toEpochMilli),
          rValid(sampleMillisInstant, recordTimestampMillisSchema)
        ),
        (
          rConfig(sampleString, recordStringSchema, recordTimestampMillisSchema, Input),
          invalidTypes("path 'field' actual: 'String' expected: 'Instant | Long'")
        ),
        (
          rConfig(sampleBoolean, recordBooleanSchema, recordTimestampMillisSchema, sampleString),
          invalidTypes(s"path 'field' actual: '${typedStr.display}' expected: 'Instant | Long'")
        ),
      )
    )
  }

  test(
    "should test end to end kafka avro record data at sink / source with input as output with Logical: Timestamp Micros -> Instant"
  ) {
    testEnd2End(
      Table(
        ("config", "result"),
        (
          rConfig(sampleMicrosInstant, recordTimestampMicrosSchema, recordTimestampMicrosSchema, Input),
          rValid(sampleMicrosInstant, recordTimestampMicrosSchema)
        ),
        (
          rConfig(sampleString, recordStringSchema, recordTimestampMicrosSchema, sampleMicrosInstant),
          rValid(sampleMicrosInstant, recordTimestampMicrosSchema)
        ),
        (
          rConfig(sampleMicrosInstant.toMicros, recordLongSchema, recordTimestampMicrosSchema, Input),
          rValid(sampleMicrosInstant, recordTimestampMicrosSchema)
        ),
        (
          rConfig(sampleString, recordStringSchema, recordTimestampMicrosSchema, sampleMicrosInstant.toMicros),
          rValid(sampleMicrosInstant, recordTimestampMicrosSchema)
        ),
        (
          rConfig(sampleString, recordStringSchema, recordTimestampMicrosSchema, Input),
          invalidTypes("path 'field' actual: 'String' expected: 'Instant | Long'")
        ),
        (
          rConfig(sampleBoolean, recordBooleanSchema, recordTimestampMicrosSchema, sampleString),
          invalidTypes(s"path 'field' actual: '${typedStr.display}' expected: 'Instant | Long'")
        ),
      )
    )
  }

  private def testEnd2End(testData: TableFor2[ScenarioConfig, ValidatedNel[ProcessCompilationError, RunResult]]) = {
    forAll(testData) { (config: ScenarioConfig, expected: Validated[_, RunResult]) =>
      val results = runWithValueResults(config)
      results shouldBe expected
    }
  }

  test("should catch runtime errors") {
    val testData = Table(
      "config",
      // Comparing String -> Enum returns true, but in runner BestEffortAvroEncoder tries to encode String (that doesn't meet the requirements) to Enum
      rConfig(sampleStrFixedV, recordStringSchema, recordEnumSchema, Input),

      // FIXME: Comparing EnumV2 -> Enum returns true, but in runner BestEffortAvroEncoder tries to encode String (that doesn't meet the requirements) to Enum
      rConfig(sampleEnumV2, recordEnumSchemaV2, recordEnumSchema, Input),

      // Comparing String -> Fixed returns true, but in runner BestEffortAvroEncoder tries to encode String (that doesn't meet the requirements) to Fixed
      rConfig(sampleString, recordStringSchema, recordFixedSchema, Input),

      // FIXME: Comparing FixedV2 -> Fixed returns true, but in runner BestEffortAvroEncoder tries to encode value FixedV2 to Fixed
      rConfig(sampleFixedV2, recordFixedSchemaV2, recordFixedSchema, Input),

      // Situation when we put String -> UUID, where String isn't valid UUID type...
      rConfig(sampleString, recordStringSchema, recordUUIDSchema, Input),
    )

    forAll(testData) { config: ScenarioConfig =>
      val results = runWithValueResults(config)
      val message = results.validValue.errors.head.throwable.asInstanceOf[AvroRuntimeException].getMessage
      message shouldBe s"Not expected container: ${config.sourceSchema} for schema: ${config.sinkSchema}"
    }

  }

  // Error / bug on field schema evolution... SubV1 -> SubV2 ( currency with default value - optional field )
  test("should catch runner errors on field schema evolution") {
    val config = rConfig(
      sampleNestedRecord,
      nestedRecordSchema,
      nestedRecordSchemaV2,
      Map("sub" -> SpecialSpELElement("#input.field.sub"), "str" -> sampleString),
      Some(lax)
    )
    val results = runWithValueResults(config)

    val error = results.validValue.errors.head.throwable.asInstanceOf[SerializationException]
    error.getMessage shouldBe "Error serializing Avro message"

    error.getCause.getMessage shouldBe s"""Not in union ${nestedRecordV2FieldsSchema}: {"sub": {"price": $sampleDouble}, "str": "$sampleString"} (field=$RecordFieldName)"""
  }

  test("should allow to dynamically get record field by name") {
    val config = sConfig(
      AvroUtils.createRecord(recordIntegerSchema, Map(RecordFieldName -> sampleInteger)),
      recordIntegerSchema,
      integerSchema,
      SpecialSpELElement("#input.get('field')")
    )
    val results = runWithValueResults(config)

    results.validValue.successes shouldBe List(sampleInteger)
  }

  private def runWithValueResults(config: ScenarioConfig) =
    runWithResults(config).map(
      _.mapSuccesses(r =>
        r.value() match {
          case bytes: Array[Byte] =>
            ByteBuffer.wrap(bytes) // We convert bytes to byte buffer because comparing array[byte] compares reference
          case v => v
        }
      )
    )

  private def runWithResults(config: ScenarioConfig): RunnerListResult[ProducerRecord[String, Any]] = {
    val avroScenario   = createScenario(config)
    val sourceSchemaId = runner.registerAvroSchema(config.sourceTopic, config.sourceSchema)
    runner.registerAvroSchema(config.sinkTopic, config.sinkSchema)

    val input = KafkaAvroConsumerRecord(config.sourceTopic, config.inputData, sourceSchemaId)
    runner.runWithAvroData(avroScenario, List(input))
  }

  private def createScenario(config: ScenarioConfig) =
    ScenarioBuilder
      .streamingLite("check avro validation")
      .source(
        sourceName,
        KafkaUniversalName,
        topicParamName.value         -> s"'${config.sourceTopic}'",
        schemaVersionParamName.value -> s"'${SchemaVersionOption.LatestOptionName}'"
      )
      .emptySink(
        sinkName,
        KafkaUniversalName,
        topicParamName.value              -> s"'${config.sinkTopic}'",
        schemaVersionParamName.value      -> s"'${SchemaVersionOption.LatestOptionName}'",
        sinkKeyParamName.value            -> "",
        sinkValueParamName.value          -> s"${config.sinkDefinition}",
        sinkRawEditorParamName.value      -> "true",
        sinkValidationModeParamName.value -> s"'${config.validationModeName}'"
      )

  case class ScenarioConfig(
      topic: String,
      inputData: Any,
      sourceSchema: Schema,
      sinkSchema: Schema,
      sinkDefinition: String,
      validationMode: Option[ValidationMode]
  ) {
    lazy val validationModeName: String = validationMode.map(_.name).getOrElse(ValidationMode.strict.name)
    lazy val sourceTopic                = s"$topic-input"
    lazy val sinkTopic                  = s"$topic-output"
  }

  // RecordValid -> valid success record with base field
  private def rValid(data: Any, schema: Schema): Valid[RunListResult[GenericRecord]] = {
    valid(AvroUtils.createRecord(schema, Map(RecordFieldName -> data)))
  }

  // RecordConfig -> config with record as a input
  private def rConfig(
      inputData: Any,
      sourceSchema: Schema,
      sinkSchema: Schema,
      output: Any,
      validationMode: Option[ValidationMode] = None
  ): ScenarioConfig = {
    val sinkDefinition = output match {
      case element: SpecialSpELElement if List(EmptyMap, Input).contains(element) => element
      case any                                                                    => Map(RecordFieldName -> any)
    }

    val input = inputData match {
      case record: GenericRecord => record
      case any                   => AvroUtils.createRecord(sourceSchema, Map(RecordFieldName -> any))
    }

    ScenarioConfig(randomTopic, input, sourceSchema, sinkSchema, sinkDefinition.toSpELLiteral, validationMode)
  }

  // StandardConfig -> simple avro type as a input
  private def sConfig(inputData: Any, schema: Schema, output: Any): ScenarioConfig =
    sConfig(inputData, schema, schema, output, None)

  private def sConfig(
      inputData: Any,
      sourceSchema: Schema,
      sinkSchema: Schema,
      output: Any,
      validationMode: Option[ValidationMode] = None
  ): ScenarioConfig =
    ScenarioConfig(randomTopic, inputData, sourceSchema, sinkSchema, output.toSpELLiteral, validationMode)

}
