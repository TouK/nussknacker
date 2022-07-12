package pl.touk.nussknacker.engine.lite.components

import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, Validated}
import com.typesafe.config.ConfigValueFactory
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import org.apache.avro.{AvroRuntimeException, Schema}
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.common.errors.SerializationException
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError
import pl.touk.nussknacker.engine.api.process.{OutputValidatorErrorsMessageFormatter, ProcessObjectDependencies}
import pl.touk.nussknacker.engine.avro.encode.ValidationMode
import pl.touk.nussknacker.engine.avro.schemaregistry.SchemaVersionOption
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.{MockConfluentSchemaRegistryClientFactory, MockSchemaRegistryClient}
import pl.touk.nussknacker.engine.avro.{AvroUtils, KafkaAvroBaseComponentTransformer}
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.lite.components.AvroTestData.{recordStringSchema, _}
import pl.touk.nussknacker.engine.lite.util.test.{KafkaAvroConsumerRecord, LiteKafkaTestScenarioRunner}
import pl.touk.nussknacker.engine.util.namespaces.DefaultNamespacedObjectNaming
import pl.touk.nussknacker.engine.util.test.RunResult
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage

import java.nio.ByteBuffer
import java.util.UUID

class LiteKafkaAvroFunctionalTest extends FunSuite with Matchers with TableDrivenPropertyChecks with ValidatedValuesDetailedMessage {

  import Empty._
  import LiteKafkaComponentProvider._
  import LiteKafkaTestScenarioRunner._
  import ValidationMode._
  import pl.touk.nussknacker.engine.avro.KafkaAvroBaseComponentTransformer._
  import pl.touk.nussknacker.engine.spel.Implicits._

  private val input = "#input"
  private val sourceName = "my-source"
  private val sinkName = "my-sink"

  private val runtime: LiteKafkaTestScenarioRunner = {
    val config = DefaultKafkaConfig
      // we disable default kafka components to replace them by mocked
      .withValue("components.kafka.disabled", ConfigValueFactory.fromAnyRef(true))
      .withValue("kafka.kafkaProperties.\"schema.registry.url\"", fromAnyRef("schema-registry:666"))

    val mockSchemaRegistryClient = new MockSchemaRegistryClient
    val mockedKafkaComponents = new LiteKafkaComponentProvider(new MockConfluentSchemaRegistryClientFactory(mockSchemaRegistryClient))
    val processObjectDependencies = ProcessObjectDependencies(config, DefaultNamespacedObjectNaming)
    val mockedComponents = mockedKafkaComponents.create(config, processObjectDependencies)

    new LiteKafkaTestScenarioRunner(mockSchemaRegistryClient, mockedComponents, config)
  }

  test("should test end to end kafka avro record data at sink / source") {
    val testData = Table(
      ("config", "result"),
      //Primitive null validations
      (sConfig(null, nullSchema, input), valid(null)),
      (sConfig(null, nullSchema, null), valid(null)),

      //Primitive string validations
      (sConfig(sampleString, stringSchema, input), valid(sampleString)),
      (sConfig(sampleString, stringSchema, "str2"), valid("str2")),

      //Primitive boolean validations
      (sConfig(sampleBoolean, booleanSchema, input), valid(sampleBoolean)),
      (sConfig(sampleString, stringSchema, booleanSchema, sampleBoolean), valid(sampleBoolean)),

      //Primitive bytes validations
      (sConfig(sampleBytes, bytesSchema, input), valid(ByteBuffer.wrap(sampleBytes))),
      //FIXME: ByteBuffer is not allowed from SpEL.. (sConfig(sampleString, stringSchema, bytesSchema, """T(java.nio.ByteBuffer).wrap(#input.getBytes("UTF-8"))"""), valid(ByteBuffer.wrap(sampleBytes))),

      //Primitive integer validations
      (sConfig(sampleInteger, integerSchema, input), valid(sampleInteger)),
      (sConfig(sampleBoolean, booleanSchema, integerSchema, sampleInteger), valid(sampleInteger)),

      (sConfig(sampleInteger, longSchema, integerSchema, input), valid(sampleInteger)), //Long -> Int?
      (sConfig(sampleBoolean, booleanSchema, integerSchema, sampleInteger), valid(sampleInteger)), //Long -> Int?

      (sConfig(null, nullSchema, integerSchema, input), invalidTypes("path 'Data' actual: 'null' expected: 'Integer'")),
      (sConfig(sampleBoolean, booleanSchema, integerSchema, input), invalidTypes("path 'Data' actual: 'Boolean' expected: 'Integer'")),
      (sConfig(sampleString, stringSchema, integerSchema, input), invalidTypes("path 'Data' actual: 'String' expected: 'Integer'")),
      (sConfig(sampleFloat, floatSchema, integerSchema, input), invalidTypes("path 'Data' actual: 'Float' expected: 'Integer'")),
      (sConfig(sampleDouble, doubleSchema, integerSchema, input), invalidTypes("path 'Data' actual: 'Double' expected: 'Integer'")),
      (sConfig(sampleBytes, bytesSchema, integerSchema, input), invalidTypes("path 'Data' actual: 'ByteBuffer' expected: 'Integer'")),

      //FIXME: null validation (sConfig(sampleInteger, integerSchema, null), invalidTypes("path 'Data' actual: 'null' expected: 'Integer'")),
      (sConfig(sampleInteger, integerSchema, sampleString), invalidTypes("path 'Data' actual: 'String' expected: 'Integer'")),
      (sConfig(sampleInteger, integerSchema, sampleBoolean), invalidTypes("path 'Data' actual: 'Boolean' expected: 'Integer'")),
      (sConfig(sampleInteger, integerSchema, sampleFloat), invalidTypes("path 'Data' actual: 'Float' expected: 'Integer'")),
      (sConfig(sampleInteger, integerSchema, s"T(java.lang.Double).valueOf(1)"), invalidTypes("path 'Data' actual: 'Double' expected: 'Integer'")),

      //Primitive long validations
      (sConfig(sampleLong, longSchema, longSchema, input), valid(sampleLong)),
      (sConfig(sampleBoolean, booleanSchema, longSchema, sampleLong), valid(sampleLong)),
      (sConfig(sampleInteger, integerSchema, longSchema, input), valid(sampleInteger)), //Int -> Long?
      (sConfig(sampleBoolean, booleanSchema, longSchema, sampleInteger), valid(sampleInteger)), //Int -> Long?

      (sConfig(null, nullSchema, longSchema, input), invalidTypes("path 'Data' actual: 'null' expected: 'Long'")),
      (sConfig(sampleBoolean, booleanSchema, longSchema, input), invalidTypes("path 'Data' actual: 'Boolean' expected: 'Long'")),
      (sConfig(sampleString, stringSchema, longSchema, input), invalidTypes("path 'Data' actual: 'String' expected: 'Long'")),
      (sConfig(sampleFloat, floatSchema, longSchema, input), invalidTypes("path 'Data' actual: 'Float' expected: 'Long'")),
      (sConfig(sampleDouble, doubleSchema, longSchema, input), invalidTypes("path 'Data' actual: 'Double' expected: 'Long'")),
      (sConfig(sampleBytes, bytesSchema, longSchema, input), invalidTypes("path 'Data' actual: 'ByteBuffer' expected: 'Long'")),

      //FIXME: null validation (sConfig(sampleLong, longSchema, null), invalidTypes("path 'Data' actual: 'null' expected: 'Long'")),
      (sConfig(sampleLong, longSchema, "2"), invalidTypes("path 'Data' actual: 'String' expected: 'Long'")),
      (sConfig(sampleLong, longSchema, true), invalidTypes("path 'Data' actual: 'Boolean' expected: 'Long'")),
      (sConfig(sampleLong, longSchema, 1.0), invalidTypes("path 'Data' actual: 'Float' expected: 'Long'")),
      (sConfig(sampleLong, longSchema, "T(java.lang.Double).valueOf(1)"), invalidTypes("path 'Data' actual: 'Double' expected: 'Long'")),

      //Primitive float validations
      (sConfig(sampleFloat, floatSchema, floatSchema, input), valid(sampleFloat)),
      (sConfig(sampleBoolean, booleanSchema, floatSchema, sampleFloat), valid(sampleFloat)),
      (sConfig(sampleDouble, doubleSchema, floatSchema, input), valid(sampleDouble)), //Double -> Float?
      (sConfig(sampleBoolean, booleanSchema, floatSchema, sampleDouble), valid(sampleDouble)), //Double -> Float?
      (sConfig(sampleInteger, integerSchema, floatSchema, input), valid(sampleInteger)),
      (sConfig(sampleBoolean, booleanSchema, floatSchema, sampleInteger), valid(sampleInteger)),
      (sConfig(sampleLong, longSchema, floatSchema, input), valid(sampleLong)), //Long -> Float?
      (sConfig(sampleBoolean, booleanSchema, floatSchema, sampleLong), valid(sampleLong)), //Long -> Float?

      (sConfig(null, nullSchema, floatSchema, input), invalidTypes("path 'Data' actual: 'null' expected: 'Float'")),
      (sConfig(sampleBoolean, booleanSchema, floatSchema, input), invalidTypes("path 'Data' actual: 'Boolean' expected: 'Float'")),
      (sConfig(sampleString, stringSchema, floatSchema, input), invalidTypes("path 'Data' actual: 'String' expected: 'Float'")),
      (sConfig(sampleBytes, bytesSchema, floatSchema, input), invalidTypes("path 'Data' actual: 'ByteBuffer' expected: 'Float'")),

      //FIXME: null validation (sConfig(sampleFloat, floatSchema, null), invalidTypes("path 'Data' actual: 'null' expected: 'Float'")),
      (sConfig(sampleFloat, floatSchema, "2"), invalidTypes("path 'Data' actual: 'String' expected: 'Float'")),
      (sConfig(sampleFloat, floatSchema, true), invalidTypes("path 'Data' actual: 'Boolean' expected: 'Float'")),

      //Primitive Double validations
      (sConfig(sampleDouble, doubleSchema, input), valid(sampleDouble)),
      (sConfig(sampleBoolean, booleanSchema, doubleSchema, s"T(java.lang.Double).valueOf($sampleDouble)"), valid(sampleDouble)),
      (sConfig(sampleFloat, floatSchema, doubleSchema, input), valid(sampleFloat)),
      (sConfig(sampleBoolean, booleanSchema, doubleSchema, sampleFloat), valid(java.lang.Double.valueOf(sampleFloat.toString))),
      (sConfig(sampleInteger, integerSchema, doubleSchema, input), valid(sampleInteger)),
      (sConfig(sampleBoolean, booleanSchema, doubleSchema, sampleInteger), valid(sampleInteger)),
      (sConfig(sampleLong, longSchema, doubleSchema, input), valid(sampleLong)),
      (sConfig(sampleLong, longSchema, doubleSchema, sampleLong), valid(sampleLong)),

      (sConfig(null, nullSchema, doubleSchema, input), invalidTypes("path 'Data' actual: 'null' expected: 'Double'")),
      (sConfig(sampleBoolean, booleanSchema, doubleSchema, input), invalidTypes("path 'Data' actual: 'Boolean' expected: 'Double'")),
      (sConfig(sampleString, stringSchema, doubleSchema, input), invalidTypes("path 'Data' actual: 'String' expected: 'Double'")),
      (sConfig(sampleBytes, bytesSchema, doubleSchema, input), invalidTypes("path 'Data' actual: 'ByteBuffer' expected: 'Double'")),

      //FIXME: null validation (sConfig(sampleDouble, doubleSchema, null), invalidTypes("path 'Data' actual: 'null' expected: 'Double'")),
      (sConfig(sampleDouble, doubleSchema, "2"), invalidTypes("path 'Data' actual: 'String' expected: 'Double'")),
      (sConfig(sampleDouble, doubleSchema, true), invalidTypes("path 'Data' actual: 'Boolean' expected: 'Double'")),

      //Record with union field validations
      (rConfig(sampleInteger, recordUnionOfStringInteger, input), rValid(sampleInteger, recordUnionOfStringInteger)),
      (rConfig(sampleBoolean, recordBoolean, recordUnionOfStringInteger, sampleInteger), rValid(sampleInteger, recordUnionOfStringInteger)),
      (rConfig(sampleString, recordUnionOfStringInteger, input), rValid(sampleString, recordUnionOfStringInteger)),
      (rConfig(sampleBoolean, recordBoolean, recordUnionOfStringInteger, sampleString), rValid(sampleString, recordUnionOfStringInteger)),
      (ScenarioConfig(sampleString, stringSchema, recordUnionOfStringInteger, input, None), invalidTypes("path 'Data' actual: 'String' expected: '{field: String | Integer}'")),
      (rConfig(sampleBoolean, recordMaybeBoolean, recordUnionOfStringInteger, input), invalidTypes("path 'field' actual: 'Boolean' expected: 'String | Integer'")),

      //Array validations
      (rConfig(sampleIntegerArray, recordWithArrayOfNumbers, input), rValid(sampleIntegerArray, recordWithArrayOfNumbers)),
      (rConfig(List("12"), recordWithArrayOfStrings, recordWithArrayOfNumbers, input), invalidTypes("path 'field[]' actual: 'String' expected: 'Integer | Double'")),
      (rConfig(sampleInteger, recordInteger, recordWithArrayOfNumbers, "{1, 2}"), rValid(sampleIntegerArray, recordWithArrayOfNumbers)),
      (rConfig(sampleInteger, recordInteger, recordWithArrayOfNumbers, "{}"), rValid(List(), recordWithArrayOfNumbers)),
      (rConfig(sampleInteger, recordInteger, recordWithArrayOfNumbers, "{1.0, 2.5}"), rValid(List(1, 2), recordWithArrayOfNumbers)), //bug with serialization / deserialization union?? There should be List(1.0, 2.5) - casting to first schema: there first is int
      (rConfig(sampleInteger, recordInteger, recordWithArrayOfNumbers, """{true, "2"}"""), invalidTypes("path 'field[]' actual: 'Boolean | String' expected: 'Integer | Double'")),
      (rConfig(sampleInteger, recordInteger, recordWithArrayOfNumbers, sampleInteger), invalidTypes("path 'field' actual: 'Integer' expected: 'List[Integer | Double]'")),
      //FIXME: null validation (rConfig(sampleInteger, recordInteger, recordWithArrayOfNumbers, null), invalidTypes("path 'field' actual: 'null' expected: 'List[Integer | Double]'")),
      (rConfig(sampleInteger, recordInteger, recordWithArrayOfNumbers, EmptyRecord), invalid(Nil, List("field"), Nil)),

      (rConfig(sampleIntegerArray, recordWithMaybeArrayOfNumbers, input), rValid(sampleIntegerArray, recordWithMaybeArrayOfNumbers)),
      (rConfig(List("12"), recordWithArrayOfStrings, recordWithMaybeArrayOfNumbers, input), invalidTypes("path 'field[]' actual: 'String' expected: 'Integer | Double'")),
      (rConfig(sampleInteger, recordInteger, recordWithMaybeArrayOfNumbers, "{1, 2}"), rValid(sampleIntegerArray, recordWithMaybeArrayOfNumbers)),
      (rConfig(sampleInteger, recordInteger, recordWithMaybeArrayOfNumbers, "{}"), rValid(List(), recordWithMaybeArrayOfNumbers)),
      (rConfig(sampleInteger, recordInteger, recordWithMaybeArrayOfNumbers, """{1.0, 2.5}"""), rValid(List(1, 2), recordWithMaybeArrayOfNumbers)), //bug with serialization / deserialization union?? There should be List(1.0, 2.5) - casting to first schema: there first is int
      (rConfig(sampleIntegerArray, recordWithMaybeArrayOfNumbers, """{true, "2"}"""), invalidTypes("path 'field[]' actual: 'Boolean | String' expected: 'Integer | Double'")),
      (rConfig(sampleIntegerArray, recordWithMaybeArrayOfNumbers, sampleInteger), invalidTypes("path 'field' actual: 'Integer' expected: 'null | List[Integer | Double]'")),
      (rConfig(sampleIntegerArray, recordWithMaybeArrayOfNumbers, null), rValid(null, recordWithMaybeArrayOfNumbers)),
      (rConfig(sampleIntegerArray, recordWithMaybeArrayOfNumbers, EmptyRecord), invalid(Nil, List("field"), Nil)),

      (rConfig(sampleIntegerArray, recordWithOptionalArrayOfNumbers, input), rValid(sampleIntegerArray, recordWithOptionalArrayOfNumbers)),
      (rConfig(List("12"), recordWithArrayOfStrings, recordWithOptionalArrayOfNumbers, input), invalidTypes("path 'field[]' actual: 'String' expected: 'Integer | Double'")),
      (rConfig(sampleInteger, recordInteger, recordWithOptionalArrayOfNumbers, "{1, 2}"), rValid(sampleIntegerArray, recordWithOptionalArrayOfNumbers)),
      (rConfig(sampleInteger, recordInteger, recordWithOptionalArrayOfNumbers, "{}"), rValid(List(), recordWithOptionalArrayOfNumbers)),
      (rConfig(sampleInteger, recordInteger, recordWithOptionalArrayOfNumbers, "{1.0, 2.5}"), rValid(List(1, 2), recordWithOptionalArrayOfNumbers)), //bug with serialization / deserialization union?? There should be List(1.0, 2.5) - casting to first schema: there first is int
      (rConfig(sampleInteger, recordInteger, recordWithOptionalArrayOfNumbers, """{true, "2"}"""), invalidTypes("path 'field[]' actual: 'Boolean | String' expected: 'Integer | Double'")),
      (rConfig(sampleInteger, recordInteger, recordWithOptionalArrayOfNumbers, sampleInteger), invalidTypes("path 'field' actual: 'Integer' expected: 'null | List[Integer | Double]'")),
      (rConfig(sampleInteger, recordInteger, recordWithOptionalArrayOfNumbers, null), rValid(null, recordWithOptionalArrayOfNumbers)),
      (rConfig(sampleInteger, recordInteger, recordWithOptionalArrayOfNumbers, EmptyRecord), rValid(null, recordWithOptionalArrayOfNumbers)),

      (rConfig(sampleArrayInArray, recordOptionalArrayOfArraysNumbers, input), rValid(sampleArrayInArray, recordOptionalArrayOfArraysNumbers)),
      (rConfig(List(List("12")), recordOptionalArrayOfArraysStrings, recordOptionalArrayOfArraysNumbers, input), invalidTypes("path 'field[][]' actual: 'String' expected: 'Integer | Double'")),
      (rConfig(sampleInteger, recordInteger, recordOptionalArrayOfArraysNumbers, "{{1, 2}}"), rValid(sampleArrayInArray, recordOptionalArrayOfArraysNumbers)),
      (rConfig(sampleInteger, recordInteger, recordOptionalArrayOfArraysNumbers, "{}"), rValid(List(), recordOptionalArrayOfArraysNumbers)),
      (rConfig(sampleInteger, recordInteger, recordOptionalArrayOfArraysNumbers, "{{}}"), rValid(List(List()), recordOptionalArrayOfArraysNumbers)),
      (rConfig(sampleInteger, recordInteger, recordOptionalArrayOfArraysNumbers, "{{1.0, 2.5}}"), rValid(List(List(1, 2)), recordOptionalArrayOfArraysNumbers)), //bug with serialization / deserialization union?? There should be List(1.0, 2.5) - casting to first schema: there first is int
      (rConfig(sampleInteger, recordInteger, recordOptionalArrayOfArraysNumbers, """{{true, "2"}}"""), invalidTypes("path 'field[][]' actual: 'Boolean | String' expected: 'Integer | Double'")),
      (rConfig(sampleInteger, recordInteger, recordOptionalArrayOfArraysNumbers, s"{$sampleInteger}"), invalidTypes("path 'field[]' actual: 'Integer' expected: 'null | List[Integer | Double]'")),
      (rConfig(sampleInteger, recordInteger, recordOptionalArrayOfArraysNumbers, sampleInteger), invalidTypes("path 'field' actual: 'Integer' expected: 'null | List[null | List[Integer | Double]]'")),
      (rConfig(sampleInteger, recordInteger, recordOptionalArrayOfArraysNumbers, null), rValid(null, recordOptionalArrayOfArraysNumbers)),
      (rConfig(sampleInteger, recordInteger, recordOptionalArrayOfArraysNumbers, "{null}"), rValid(List(null), recordOptionalArrayOfArraysNumbers)),
      (rConfig(sampleInteger, recordInteger, recordOptionalArrayOfArraysNumbers, EmptyRecord), rValid(null, recordOptionalArrayOfArraysNumbers)),

      (rConfig(sampleArrayWithRecord, recordOptionalArrayOfRecords, input), rValid(sampleArrayWithRecord, recordOptionalArrayOfRecords)),
      (rConfig(sampleInteger, recordInteger, recordOptionalArrayOfRecords, s"{{price: $sampleDouble}}"), rValid(sampleArrayWithRecord, recordOptionalArrayOfRecords)),
      (rConfig(sampleInteger, recordInteger, recordOptionalArrayOfRecords, "{{price: null}}"), rValid(List(Map("price" -> null)), recordOptionalArrayOfRecords)),
      (rConfig(sampleInteger, recordInteger, recordOptionalArrayOfRecords, """{{"price1": "15.5"}}"""), invalid(Nil, List("field[].price"), List("field[].price1"))),
      (rConfig(sampleInteger, recordInteger, recordOptionalArrayOfRecords, """{{"price1": "15.5"}}""", Some(allowRedundantAndOptional)), invalid(Nil, List("field[].price"), Nil)),
      (rConfig(sampleInteger, recordInteger, recordOptionalArrayOfRecords, """{{"price": "15.5"}}"""), invalidTypes("path 'field[].price' actual: 'String' expected: 'null | Double'")),
      (rConfig(sampleInteger, recordInteger, recordOptionalArrayOfRecords, sampleInteger), invalidTypes("""path 'field' actual: 'Integer' expected: 'null | List[null | {price: null | Double}]'""")),
      (rConfig(sampleInteger, recordInteger, recordOptionalArrayOfRecords, s"{$sampleInteger}"), invalidTypes("""path 'field[]' actual: 'Integer' expected: 'null | {price: null | Double}'""")),
      (rConfig(sampleInteger, recordInteger, recordOptionalArrayOfRecords, null), rValid(null, recordOptionalArrayOfRecords)),
      (rConfig(sampleInteger, recordInteger, recordOptionalArrayOfRecords, "{null}"), rValid(List(null), recordOptionalArrayOfRecords)),
      (rConfig(sampleInteger, recordInteger, recordOptionalArrayOfRecords, EmptyRecord), rValid(null, recordOptionalArrayOfRecords)),

      //Map validations
      (rConfig(sampleMapInts, recordMapOfInts, input), rValid(sampleMapInts, recordMapOfInts)),
      (rConfig(Map("tax" -> "7"), recordMapOfStrings, recordMapOfInts, input), invalidTypes("path 'field' actual: 'Map[String,String]' expected: 'Map[String, null | Integer]'")),
      (rConfig(sampleInteger, recordInteger, recordMapOfInts, "{tax: 7}"), rValid(sampleMapInts, recordMapOfInts)),
      (rConfig(sampleInteger, recordInteger, recordMapOfInts, EmptyField), rValid(Map(), recordMapOfInts)),
      (rConfig(sampleInteger, recordInteger, recordMapOfInts, """{"tax": 7, "vat": "23"}"""), invalidTypes("path 'field.vat' actual: 'String' expected: 'null | Integer'")),
      (rConfig(sampleInteger, recordInteger, recordMapOfInts, sampleInteger), invalidTypes("path 'field' actual: 'Integer' expected: 'Map[String, null | Integer]'")),
      (rConfig(sampleInteger, recordInteger, recordMapOfInts, "{}"), invalidTypes("path 'field' actual: 'List[Unknown]' expected: 'Map[String, null | Integer]'")),
      //FIXME: null validation (rConfig(sampleInteger, recordInteger, recordMapOfInts, null), invalidTypes("path 'field' actual: 'null' expected: 'Map[String, null | Integer]'")),
      (rConfig(sampleInteger, recordInteger, recordMapOfInts, EmptyRecord), invalid(Nil, List("field"), Nil)),

      (rConfig(sampleMapInts, recordMaybeMapOfInts, input), rValid(sampleMapInts, recordMaybeMapOfInts)),
      (rConfig(Map("tax" -> "7"), recordMapOfStrings, recordMaybeMapOfInts, input), invalidTypes("path 'field' actual: 'Map[String,String]' expected: 'null | Map[String, null | Integer]'")),
      (rConfig(sampleInteger, recordInteger, recordMaybeMapOfInts, "{tax: 7}"), rValid(sampleMapInts, recordMaybeMapOfInts)),
      (rConfig(sampleInteger, recordInteger, recordMaybeMapOfInts, EmptyField), rValid(Map(), recordMaybeMapOfInts)),
      (rConfig(sampleInteger, recordInteger, recordMaybeMapOfInts, """{"tax": 7, "vat": "23"}"""), invalidTypes("path 'field.vat' actual: 'String' expected: 'null | Integer'")),
      (rConfig(sampleInteger, recordInteger, recordMaybeMapOfInts, sampleInteger), invalidTypes("path 'field' actual: 'Integer' expected: 'null | Map[String, null | Integer]'")),
      (rConfig(sampleInteger, recordInteger, recordMaybeMapOfInts, "{}"), invalidTypes("path 'field' actual: 'List[Unknown]' expected: 'null | Map[String, null | Integer]'")),
      (rConfig(sampleInteger, recordInteger, recordMaybeMapOfInts, null), rValid(null, recordMaybeMapOfInts)),
      (rConfig(sampleInteger, recordInteger, recordMaybeMapOfInts, EmptyRecord), invalid(Nil, List("field"), Nil)),

      (rConfig(sampleMapOfMapsInts, recordOptionalMapOfMapsInts, input), rValid(sampleMapOfMapsInts, recordOptionalMapOfMapsInts)),
      (rConfig(Map("first" -> Map("tax" -> "7")), recordMapOfMapsStrings, recordOptionalMapOfMapsInts, input), invalidTypes("path 'field' actual: 'Map[String,Map[String,String]]' expected: 'null | Map[String, null | Map[String, null | Integer]]'")),
      (rConfig(sampleInteger, recordInteger, recordOptionalMapOfMapsInts, "{first: {tax: 7}}"), rValid(sampleMapOfMapsInts, recordOptionalMapOfMapsInts)),
      (rConfig(sampleInteger, recordInteger, recordOptionalMapOfMapsInts, EmptyField), rValid(Map(), recordOptionalMapOfMapsInts)),
      (rConfig(sampleInteger, recordInteger, recordOptionalMapOfMapsInts, """{first: {tax: 7, vat: "23"}}"""), invalidTypes("path 'field.first.vat' actual: 'String' expected: 'null | Integer'")),
      (rConfig(sampleInteger, recordInteger, recordOptionalMapOfMapsInts, sampleInteger), invalidTypes("path 'field' actual: 'Integer' expected: 'null | Map[String, null | Map[String, null | Integer]]'")),
      (rConfig(sampleInteger, recordInteger, recordOptionalMapOfMapsInts, s"{first: $sampleInteger}"), invalidTypes("path 'field.first' actual: 'Integer' expected: 'null | Map[String, null | Integer]'")),
      (rConfig(sampleInteger, recordInteger, recordOptionalMapOfMapsInts, "{}"), invalidTypes("path 'field' actual: 'List[Unknown]' expected: 'null | Map[String, null | Map[String, null | Integer]]'")),
      (rConfig(sampleInteger, recordInteger, recordOptionalMapOfMapsInts, null), rValid(null, recordOptionalMapOfMapsInts)),
      (rConfig(sampleInteger, recordInteger, recordOptionalMapOfMapsInts, EmptyRecord), rValid(null, recordOptionalMapOfMapsInts)),

      (rConfig(sampleMapOfRecords, recordOptionalMapOfRecords, input), rValid(sampleMapOfRecords, recordOptionalMapOfRecords)),
      (rConfig(Map("first" -> Map("price" -> "15.5")), recordOptionalMapOfStringRecords, recordOptionalMapOfRecords, input), invalidTypes("path 'field' actual: 'Map[String,{price: String}]' expected: 'null | Map[String, null | {price: null | Double}]'")),
      (rConfig(sampleInteger, recordInteger, recordOptionalMapOfRecords, s"{first: {price: $sampleDouble}}"), rValid(sampleMapOfRecords, recordOptionalMapOfRecords)),
      (rConfig(sampleInteger, recordInteger, recordOptionalMapOfRecords, EmptyField), rValid(Map(), recordOptionalMapOfRecords)),
      (rConfig(sampleInteger, recordInteger, recordOptionalMapOfRecords, """{"first": {"price": "15.5"}}"""), invalidTypes("path 'field.first.price' actual: 'String' expected: 'null | Double'")),
      (rConfig(sampleInteger, recordInteger, recordOptionalMapOfRecords, sampleInteger), invalidTypes("path 'field' actual: 'Integer' expected: 'null | Map[String, null | {price: null | Double}]'")),
      (rConfig(sampleInteger, recordInteger, recordOptionalMapOfRecords, s"{first: $sampleInteger}"), invalidTypes("path 'field.first' actual: 'Integer' expected: 'null | {price: null | Double}'")),
      (rConfig(sampleInteger, recordInteger, recordOptionalMapOfRecords, "{}"), invalidTypes("path 'field' actual: 'List[Unknown]' expected: 'null | Map[String, null | {price: null | Double}]'")),
      (rConfig(sampleInteger, recordInteger, recordOptionalMapOfRecords, null), rValid(null, recordOptionalMapOfRecords)),
      (rConfig(sampleMapOfRecords, recordOptionalMapOfRecords, EmptyRecord), rValid(null, recordOptionalMapOfRecords)),

      //Record validations
      (rConfig(sampleNestedRecord, nestedRecordSchema, nestedRecordSchema, input), rValid(sampleNestedRecord, nestedRecordSchema)),
      (rConfig(sampleInteger, recordInteger, nestedRecordSchema, s"{sub: {price: $sampleDouble}}"), rValid(sampleNestedRecord, nestedRecordSchema)),
      (rConfig(Map("sub" -> Map("price" -> "15.5")), nestedRecordWithStringPriceSchema, nestedRecordSchema, input), invalidTypes("path 'field.sub.price' actual: 'String' expected: 'null | Double'")),
      (rConfig(sampleInteger, recordInteger, nestedRecordSchema, s"{sub: {price2: $sampleDouble}}"), invalid(Nil, List("field.sub.price"), List("field.sub.price2"))),
      (rConfig(sampleInteger, recordInteger, nestedRecordSchema, s"{sub: {price2: $sampleDouble}}", Some(allowRedundantAndOptional)), invalid(Nil, List("field.sub.price"), Nil)),
      (rConfig(sampleInteger, recordInteger, nestedRecordSchema, "{:}"), rValid(Map(), nestedRecordSchema)),
      (rConfig(sampleInteger, recordInteger, nestedRecordSchema, s"""{sub: {price: "$sampleDouble"}}"""), invalidTypes("path 'field.sub.price' actual: 'String' expected: 'null | Double'")),
      (rConfig(sampleInteger, recordInteger, nestedRecordSchema, input), invalidTypes("path 'field' actual: 'Integer' expected: 'null | {sub: null | {price: null | Double}}'")),
      (rConfig(sampleNestedRecord, nestedRecordSchema, nestedRecordSchema, sampleInteger), invalidTypes("path 'field' actual: 'Integer' expected: 'null | {sub: null | {price: null | Double}}'")),
      (rConfig(sampleInteger, recordInteger, nestedRecordSchema, s"{sub: $sampleInteger}"), invalidTypes("path 'field.sub' actual: 'Integer' expected: 'null | {price: null | Double}'")),
      (rConfig(sampleInteger, recordInteger, nestedRecordSchema, "{}"), invalidTypes("path 'field' actual: 'List[Unknown]' expected: 'null | {sub: null | {price: null | Double}}'")),
      (rConfig(sampleInteger, recordInteger, nestedRecordSchema, null), rValid(null, nestedRecordSchema)),
      (rConfig(sampleInteger, recordInteger, nestedRecordSchema, "{sub: null}"), rValid(Map("sub" -> null), nestedRecordSchema)),
      (rConfig(sampleInteger, recordInteger, nestedRecordSchema, "{sub: {price: null}}"), rValid(Map("sub" -> Map("price" -> null)), nestedRecordSchema)),
      (rConfig(sampleNestedRecordV2, nestedRecordSchemaV2, nestedRecordSchema, input), invalid(Nil, Nil, List("field.sub.currency", "field.str"))),
      (rConfig(sampleNestedRecordV2, nestedRecordSchemaV2, nestedRecordSchema, input, Some(allowRedundantAndOptional)), rValid(sampleNestedRecord, nestedRecordSchema)),
      (rConfig(sampleNestedRecord, nestedRecordSchema, nestedRecordSchemaV2, input), invalid(Nil, List("field.str"), Nil)),

      //Enum validations
      (rConfig(sampleEnum, recordEnumSchema, recordEnumSchema, input), rValid(sampleEnum, recordEnumSchema)),
      (rConfig(sampleEnum, recordEnumSchemaV2, recordEnumSchema, input), rValid(sampleEnum, recordEnumSchema)),
      (rConfig(sampleInteger, recordInteger, recordEnumSchema, sampleEnum), rValid(sampleEnum, recordEnumSchema)),
      (rConfig(sampleInteger, recordInteger, recordEnumSchema, input), invalidTypes("path 'field' actual: 'Integer' expected: 'EnumSymbol[SPADES | HEARTS | DIAMONDS | CLUBS] | String'")),
      (rConfig(sampleInteger, recordInteger, recordEnumSchema, sampleInteger), invalidTypes("path 'field' actual: 'Integer' expected: 'EnumSymbol[SPADES | HEARTS | DIAMONDS | CLUBS] | String'")),

      //Fixed validations
      (rConfig(sampleFixed, recordFixedSchema, recordFixedSchema, input), rValid(sampleFixed, recordFixedSchema)),
      (rConfig(sampleFixed, recordFixedSchema, recordFixedSchema, sampleFixed), rValid(sampleFixed, recordFixedSchema)),
      (rConfig(sampleInteger, recordInteger, recordFixedSchema, input), invalidTypes("path 'field' actual: 'Integer' expected: 'Fixed[32] | ByteBuffer | String'")),
      (rConfig(sampleInteger, recordInteger, recordFixedSchema, sampleInteger), invalidTypes("path 'field' actual: 'Integer' expected: 'Fixed[32] | ByteBuffer | String'")),

      //Logical: UUID validations
      (rConfig(sampleUUID, recordUUIDSchema, recordUUIDSchema, input), rValid(sampleUUID, recordUUIDSchema)),
      (rConfig(sampleUUID.toString, recordStringSchema, recordUUIDSchema, input), rValid(sampleUUID, recordUUIDSchema)),
      (rConfig(sampleInteger, recordInteger, recordUUIDSchema, s"""T(java.util.UUID).fromString("${sampleUUID.toString}")"""), rValid(sampleUUID, recordUUIDSchema)),
      (rConfig(sampleInteger, recordInteger, recordUUIDSchema, sampleUUID.toString), rValid(sampleUUID, recordUUIDSchema)),
      (rConfig(sampleInteger, recordInteger, recordUUIDSchema, input), invalidTypes("path 'field' actual: 'Integer' expected: 'UUID | String'")),
      (rConfig(sampleInteger, recordInteger, recordUUIDSchema, sampleInteger), invalidTypes("path 'field' actual: 'Integer' expected: 'UUID | String'")),

      //Logical: BigDecimal validations
      (rConfig(sampleDecimal, recordDecimalSchema, recordDecimalSchema, input), rValid(sampleDecimal, recordDecimalSchema)),
      (rConfig(sampleString, recordStringSchema, recordDecimalSchema, "T(java.math.BigDecimal).valueOf(1l).setScale(2)"), rValid(sampleDecimal, recordDecimalSchema)),
      (rConfig(sampleString, recordStringSchema, recordDecimalSchema, sampleDecimal), invalidTypes("path 'field' actual: 'Float' expected: 'BigDecimal | ByteBuffer'")),
      (rConfig(sampleInteger, recordInteger, recordDecimalSchema, input), invalidTypes("path 'field' actual: 'Integer' expected: 'BigDecimal | ByteBuffer'")),
      (rConfig(sampleInteger, recordInteger, recordDecimalSchema, sampleInteger), invalidTypes("path 'field' actual: 'Integer' expected: 'BigDecimal | ByteBuffer'")),

      //Logical: Date validations -> LocalDate
      (rConfig(sampleDate, recordDateSchema, recordDateSchema, input), rValid(sampleDate, recordDateSchema)),
      (rConfig(sampleString, recordStringSchema, recordDateSchema, s"T(java.time.LocalDate).ofEpochDay(${sampleDate.toEpochDay})"), rValid(sampleDate, recordDateSchema)),
      (rConfig(sampleDate.toEpochDay, recordInteger, recordDateSchema, input), rValid(sampleDate, recordDateSchema)),
      (rConfig(sampleString, recordStringSchema, recordDateSchema, sampleDate.toEpochDay.toInt), rValid(sampleDate, recordDateSchema)),
      (rConfig(sampleString, recordStringSchema, recordDateSchema, input), invalidTypes("path 'field' actual: 'String' expected: 'LocalDate | Integer'")),
      (rConfig(sampleDate, recordDateSchema, recordDateSchema, sampleString), invalidTypes("path 'field' actual: 'String' expected: 'LocalDate | Integer'")),

      //Logical: Time Millis -> LocalTime
      (rConfig(sampleMillisLocalTime, recordTimeMillisSchema, recordTimeMillisSchema, input), rValid(sampleMillisLocalTime, recordTimeMillisSchema)),
      (rConfig(sampleString, recordStringSchema, recordTimeMillisSchema, s"T(java.time.LocalTime).ofNanoOfDay(${sampleMillisLocalTime.toNanoOfDay}l)"), rValid(sampleMillisLocalTime, recordTimeMillisSchema)),
      (rConfig(sampleMillisLocalTime.toMillis, recordInteger, recordTimeMillisSchema, input), rValid(sampleMillisLocalTime, recordTimeMillisSchema)),
      (rConfig(sampleString, recordStringSchema, recordTimeMillisSchema, sampleMillisLocalTime.toMillis), rValid(sampleMillisLocalTime, recordTimeMillisSchema)),
      (rConfig(sampleString, recordStringSchema, recordTimeMillisSchema, input), invalidTypes("path 'field' actual: 'String' expected: 'LocalTime | Integer'")),
      (rConfig(sampleString, recordStringSchema, recordTimeMillisSchema, sampleString), invalidTypes("path 'field' actual: 'String' expected: 'LocalTime | Integer'")),

      //Logical: Time Micros -> LocalTime
      (rConfig(sampleMicrosLocalTime, recordTimeMicrosSchema, recordTimeMicrosSchema, input), rValid(sampleMicrosLocalTime, recordTimeMicrosSchema)),
      (rConfig(sampleString, recordStringSchema, recordTimeMicrosSchema, s"T(java.time.LocalTime).ofNanoOfDay(${sampleMicrosLocalTime.toNanoOfDay}l)"), rValid(sampleMicrosLocalTime, recordTimeMicrosSchema)),
      (rConfig(sampleMicrosLocalTime.toMicros, recordLong, recordTimeMicrosSchema, input), rValid(sampleMicrosLocalTime, recordTimeMicrosSchema)),
      (rConfig(sampleString, recordStringSchema, recordTimeMicrosSchema, sampleMicrosLocalTime.toMicros), rValid(sampleMicrosLocalTime, recordTimeMicrosSchema)),
      (rConfig(sampleInteger, recordInteger, recordTimeMicrosSchema, input), invalidTypes("path 'field' actual: 'Integer' expected: 'LocalTime | Long'")),
      (rConfig(sampleInteger, recordInteger, recordTimeMicrosSchema, sampleInteger), invalidTypes("path 'field' actual: 'Integer' expected: 'LocalTime | Long'")),

      //Logical: Timestamp Millis -> Instant
      (rConfig(sampleMillisInstant, recordTimestampMillisSchema, recordTimestampMillisSchema, input), rValid(sampleMillisInstant, recordTimestampMillisSchema)),
      (rConfig(sampleString, recordStringSchema, recordTimestampMillisSchema, s"T(java.time.Instant).ofEpochMilli(${sampleMillisInstant.toEpochMilli}l)"), rValid(sampleMillisInstant, recordTimestampMillisSchema)),
      (rConfig(sampleMillisInstant.toEpochMilli, recordLong, recordTimestampMillisSchema, input), rValid(sampleMillisInstant, recordTimestampMillisSchema)),
      (rConfig(sampleString, recordStringSchema, recordTimestampMillisSchema, sampleMillisInstant.toEpochMilli), rValid(sampleMillisInstant, recordTimestampMillisSchema)),
      (rConfig(sampleInteger, recordInteger, recordTimestampMillisSchema, input), invalidTypes("path 'field' actual: 'Integer' expected: 'Instant | Long'")),
      (rConfig(sampleInteger, recordInteger, recordTimestampMillisSchema, sampleInteger), invalidTypes("path 'field' actual: 'Integer' expected: 'Instant | Long'")),

      //Logical: Timestamp Micros -> Instant
      (rConfig(sampleMicrosInstant, recordTimestampMicrosSchema, recordTimestampMicrosSchema, input), rValid(sampleMicrosInstant, recordTimestampMicrosSchema)),
      (rConfig(sampleString, recordStringSchema, recordTimestampMicrosSchema, s"T(java.time.Instant).ofEpochSecond(${sampleMicrosInstant.toMicrosFromEpoch}l, ${sampleMicrosInstant.toNanoAdjustment}l)"), rValid(sampleMicrosInstant, recordTimestampMicrosSchema)),
      (rConfig(sampleMicrosInstant.toMicros, recordLong, recordTimestampMicrosSchema, input), rValid(sampleMicrosInstant, recordTimestampMicrosSchema)),
      (rConfig(sampleString, recordStringSchema, recordTimestampMicrosSchema, sampleMicrosInstant.toMicros), rValid(sampleMicrosInstant, recordTimestampMicrosSchema)),
      (rConfig(sampleInteger, recordInteger, recordTimestampMicrosSchema, input), invalidTypes("path 'field' actual: 'Integer' expected: 'Instant | Long'")),
      (rConfig(sampleInteger, recordInteger, recordTimestampMicrosSchema, sampleInteger), invalidTypes("path 'field' actual: 'Integer' expected: 'Instant | Long'")),
    )

    forAll(testData) { (config: ScenarioConfig, expected: Validated[_, RunResult[_]]) =>
      //Given
      val avroScenario: EspProcess = createScenario(config)
      val sourceSchemaId = runtime.registerAvroSchema(config.sourceTopic, config.sourceSchema)
      runtime.registerAvroSchema(config.sinkTopic, config.sinkSchema)

      //When
      val input = KafkaAvroConsumerRecord(config.sourceTopic, config.input, sourceSchemaId)
      val result = runtime.runWithAvroData(avroScenario, List(input))

      //Then
      val resultsWithValue = result.map(runResult => {
        runResult.copy(successes = runResult.successes.map(_.value()))
      })

      resultsWithValue shouldBe expected
    }

  }

  test("should catch runtime errors") {
    val testData = Table(
      ("config", "expectedMessage"),
      //Comparing String -> Enum returns true, but in runtime BestEffortAvroEncoder tries to encode String (that doesn't meet the requirements) to Enum
      (rConfig(sampleEnumV2, recordStringSchema, recordEnumSchema, input), badContainerMessage(recordStringSchema, recordEnumSchema)),
      (rConfig(sampleBoolean, recordBoolean, recordEnumSchema, sampleEnumV2), s"Not expected symbol: $sampleEnumV2 for field: Some(field) with schema: $baseEnum"),

      //Comparing EumV2 -> Enum returns true, but in runtime BestEffortAvroEncoder tries to encode value EnumV2 to Enum
      (rConfig(sampleEnumV2, recordEnumSchemaV2, recordEnumSchema, input), badContainerMessage(recordEnumSchemaV2, recordEnumSchema)),

      //Comparing String -> Fixed returns true, but in runtime BestEffortAvroEncoder tries to encode String (that doesn't meet the requirements) to Fixed
      (rConfig(sampleString, recordStringSchema, recordFixedSchema, input), badContainerMessage (recordStringSchema, recordFixedSchema)),
      (rConfig(sampleBoolean, recordBoolean, recordFixedSchema, sampleString), s"Fixed size not matches: ${sampleString.size} != ${baseFixed.getFixedSize} for schema: $baseFixed"),

      //Comparing FixedV2 -> Fixed returns true, but in runtime BestEffortAvroEncoder tries to encode value FixedV2 to Fixed
      (rConfig(sampleFixedV2, recordFixedSchemaV2, recordFixedSchema, input), badContainerMessage(recordFixedSchemaV2, recordFixedSchema)),

      //Situation when we put String -> UUID, where String isn't valid UUID type...
      (rConfig(sampleBoolean, recordBoolean, recordUUIDSchema, sampleString), s"Value '$sampleString' is not a UUID."),
      (rConfig(sampleString, recordStringSchema, recordUUIDSchema, input), badContainerMessage(recordStringSchema, recordUUIDSchema)),
    )

    forAll(testData) { (config: ScenarioConfig, expectedMessage: String) =>
      //Given
      val avroScenario: EspProcess = createScenario(config)
      val sourceSchemaId = runtime.registerAvroSchema(config.sourceTopic, config.sourceSchema)
      runtime.registerAvroSchema(config.sinkTopic, config.sinkSchema)

      val input = KafkaAvroConsumerRecord(config.sourceTopic, config.input, sourceSchemaId)
      val result = runtime.runWithAvroData(avroScenario, List(input))


      val message = result.validValue.errors.head.throwable.asInstanceOf[AvroRuntimeException].getMessage
      message shouldBe expectedMessage
    }

  }

  //Error / bug on field schema evolution... SubV1 -> SubV2 ( currency with default value - optional field )
  test("should catch runtime errors on field schema evolution") {
    val config = rConfig(sampleNestedRecord, nestedRecordSchema, nestedRecordSchemaV2, """{"sub": #input.field.sub, "str": "sample"}""")
    val avroScenario: EspProcess = createScenario(config)
    val sourceSchemaId = runtime.registerAvroSchema(config.sourceTopic, config.sourceSchema)
    runtime.registerAvroSchema(config.sinkTopic, config.sinkSchema)

    val input = KafkaAvroConsumerRecord(config.sourceTopic, config.input, sourceSchemaId)
    val result = runtime.runWithAvroData(avroScenario, List(input))

    val error = result.validValue.errors.head.throwable.asInstanceOf[SerializationException]
    error.getMessage shouldBe "Error serializing Avro message"
    error.getCause.getMessage shouldBe s"""Not in union $subV2RecordSchema: {"price": $sampleDouble}"""
  }

  private def badContainerMessage(actualSchema: Schema, expected: Schema) = s"Not expected container: $actualSchema for schema: $expected"

  private def createScenario(config: ScenarioConfig) =
    ScenarioBuilder
      .streamingLite("check avro validation")
      .source(sourceName, KafkaAvroName,
        TopicParamName -> s"'${config.sourceTopic}'",
        SchemaVersionParamName -> s"'${SchemaVersionOption.LatestOptionName}'"
      )
      .emptySink(sinkName, KafkaSinkRawAvroName,
        TopicParamName -> s"'${config.sinkTopic}'",
        SchemaVersionParamName -> s"'${SchemaVersionOption.LatestOptionName}'",
        SinkKeyParamName -> "",
        SinkValueParamName -> s"${config.output}",
        SinkValidationModeParameterName -> s"'${config.validationModeName}'"
      )

  object ScenarioConfig {

    private def randomTopic = UUID.randomUUID().toString

    def apply(inputData: Any, schema: Schema, sinkDefinition: String, validationMode: Option[ValidationMode]): ScenarioConfig =
      new ScenarioConfig(randomTopic, inputData, schema, schema, sinkDefinition, validationMode)

    def apply(inputData: Any, sourceSchema: Schema, sinkSchema: Schema, sinkDefinition: String, validationMode: Option[ValidationMode]): ScenarioConfig =
      new ScenarioConfig(randomTopic, inputData, sourceSchema, sinkSchema, sinkDefinition, validationMode)

  }

  case class ScenarioConfig(topic: String, data: Any, sourceSchema: Schema, sinkSchema: Schema, output: String, validationMode: Option[ValidationMode]) {
    lazy val validationModeName: String = validationMode.map(_.name).getOrElse(ValidationMode.allowOptional.name)
    lazy val sourceTopic = s"$topic-input"
    lazy val sinkTopic = s"$topic-output"

    lazy val input: Any = data match {
      case map: Map[String@unchecked, _] =>
        AvroUtils.createRecord(sourceSchema, map)
      case d => d
    }
  }

  private def valid[T](data: T): Valid[RunResult[T]] =
    Valid(RunResult.success(data))

  //RecordValid -> valid success record with field
  private def rValid(data: Any, schema: Schema): Valid[RunResult[GenericRecord]] =
    Valid(rSuccess(data, schema))

  //RunResult -> success record with field
  private def rSuccess(data: Any, schema: Schema): RunResult[GenericRecord] =
    RunResult.success(AvroUtils.createRecord(schema, Map(RecordFieldName -> data)))

  private def invalidTypes(typeErrors: String*): Invalid[NonEmptyList[CustomNodeError]] = invalid(
    typeErrors.toList, Nil, Nil
  )

  private def invalid(typeFieldErrors: List[String], missingFieldsError: List[String], redundantFieldsError: List[String]): Invalid[NonEmptyList[CustomNodeError]] = {
    val finalMessage = OutputValidatorErrorsMessageFormatter.makeMessage(typeFieldErrors, missingFieldsError, redundantFieldsError)
    Invalid(NonEmptyList.one(CustomNodeError(sinkName, finalMessage, Some(KafkaAvroBaseComponentTransformer.SinkValueParamName))))
  }

  private def rConfig(inputData: Any, schema: Schema, output: Any): ScenarioConfig =
    rConfig(inputData, schema, schema, output, None)

  //RecordConfig -> config record with field
  private def rConfig(inputData: Any, sourceSchema: Schema, sinkSchema: Schema, output: Any, validationMode: Option[ValidationMode] = None): ScenarioConfig = {
    def record(value: Any) = s"""{"$RecordFieldName": $value}"""

    val sinkDefinition = output match {
      case str: String if str == input => str
      case spel: String if spel.startsWith("T(") => record(spel)
      case spel: String if spel.startsWith("{") => record(spel)
      case str: String => record(s""""$str"""")
      case long: Long => record(s"${long}l")
      case empty if empty == EmptyField => record("{:}")
      case empty if empty == EmptyRecord => "{:}"
      case any => record(any)
    }

    ScenarioConfig(Map("field" -> inputData), sourceSchema, sinkSchema, sinkDefinition, validationMode)
  }

  //StandardConfig -> simple avro type
  private def sConfig(inputData: Any, schema: Schema, output: Any): ScenarioConfig =
    sConfig(inputData, schema, schema, output, None)

  private def sConfig(inputData: Any, sourceSchema: Schema, sinkSchema: Schema, output: Any, validationMode: Option[ValidationMode] = None): ScenarioConfig = {
    val sinkDefinition = output match {
      case str: String if str == input => str
      case spel: String if spel.startsWith("T(") => spel
      case long: Long => s"${long}l"
      case str: String =>s"'$str'"
      case null => "null"
      case any => any.toString
    }

    ScenarioConfig(inputData, sourceSchema, sinkSchema, sinkDefinition, validationMode)
  }

}

private object Empty extends Enumeration {
  val EmptyRecord: Value = Value("empty-record")
  val EmptyField: Value = Value("empty-base")
}
