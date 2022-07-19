package pl.touk.nussknacker.engine.lite.components

import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, Validated}
import com.typesafe.config.ConfigValueFactory
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import org.apache.avro.Schema.Type
import org.apache.avro.generic.GenericRecord
import org.apache.avro.{AvroRuntimeException, Schema}
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.errors.SerializationException
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.{FunSuite, Inside, Matchers}
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.avro.encode.ValidationMode
import pl.touk.nussknacker.engine.avro.schemaregistry.SchemaVersionOption
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.{MockConfluentSchemaRegistryClientFactory, MockSchemaRegistryClient}
import pl.touk.nussknacker.engine.avro.{AvroSchemaCreator, AvroUtils, KafkaAvroBaseComponentTransformer}
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.lite.components.AvroTestData._
import pl.touk.nussknacker.engine.lite.util.test.{KafkaAvroConsumerRecord, LiteKafkaTestScenarioRunner}
import pl.touk.nussknacker.engine.util.namespaces.DefaultNamespacedObjectNaming
import pl.touk.nussknacker.engine.util.output.OutputValidatorErrorsMessageFormatter
import pl.touk.nussknacker.engine.util.test.RunResult
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner.RunnerResult
import pl.touk.nussknacker.test.{SinkOutputSpELConverter, ValidatedValuesDetailedMessage}

import java.nio.ByteBuffer
import java.util.UUID

class LiteKafkaAvroFunctionalTest extends FunSuite with Matchers with ScalaCheckDrivenPropertyChecks with Inside with TableDrivenPropertyChecks with ValidatedValuesDetailedMessage {

  import LiteKafkaComponentProvider._
  import LiteKafkaTestScenarioRunner._
  import SinkOutputSpELConverter._
  import ValidationMode._
  import pl.touk.nussknacker.engine.avro.KafkaAvroBaseComponentTransformer._
  import pl.touk.nussknacker.engine.spel.Implicits._

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

  implicit override val generatorDrivenConfig: PropertyCheckConfiguration = PropertyCheckConfiguration(minSuccessful = 5000, minSize = 0, workers = 15)

  test("simple 1:1 #input as sink output") {
    val genScenarioConfig = for {
      schema <- AvroGen.genSchema(ExcludedConfig.Base)
      value <- AvroGen.genValueForSchema(schema)
    } yield sConfig(value, schema, Input)

    forAll(genScenarioConfig) { config =>
      val resultsInput = runWithValueResults(config)
      val expected = valid(config.inputData)

      resultsInput shouldBe expected
    }
  }

  test("simple 1:1 SpEL as sink output") {
    //We can't in easy way put bytes information, because ByteArray is not available at SpEL context
    val config = ExcludedConfig.Base.withGlobal(Type.BYTES, Type.FIXED)
    val genScenarioConfig = for {
      schema <- AvroGen.genSchema(config)
      (value, spel) <- AvroGen.genValueWithSpELForSchema(schema)
    } yield ScenarioConfig(value, schema, schema, spel, None)

    forAll(genScenarioConfig) { config =>
      //We put at input fake data (bytes) to be sure that at the end of scenario we are putting data converted to SpEL
      val configWithFakeInput = config.copy(inputData = sampleBytes, sourceSchema = bytesSchema)
      val resultsInput = runWithValueResults(configWithFakeInput)
      val expected = valid(config.inputData)

      resultsInput shouldBe expected
    }
  }

  test("should test end to end kafka avro record data at sink / source") {
    val testData = Table(
      ("config", "result"),
      //FIXME: java.nio.ByteBuffer is not available from SpEL (sConfig(sampleString, stringSchema, bytesSchema, """T(java.nio.ByteBuffer).wrap(#input.getBytes("UTF-8"))"""), valid(ByteBuffer.wrap(sampleBytes))),

      //Primitive integer validations
      (sConfig(sampleInteger, longSchema, integerSchema, Input), valid(sampleInteger)), //Long -> Int?
      (sConfig(sampleBoolean, booleanSchema, integerSchema, sampleInteger), valid(sampleInteger)), //Long -> Int?

      (sConfig(null, nullSchema, integerSchema, Input), invalidTypes("path 'Data' actual: 'Null' expected: 'Integer'")),
      (sConfig(sampleBoolean, booleanSchema, integerSchema, Input), invalidTypes("path 'Data' actual: 'Boolean' expected: 'Integer'")),
      (sConfig(sampleString, stringSchema, integerSchema, Input), invalidTypes("path 'Data' actual: 'String' expected: 'Integer'")),
      (sConfig(sampleFloat, floatSchema, integerSchema, Input), invalidTypes("path 'Data' actual: 'Float' expected: 'Integer'")),
      (sConfig(sampleDouble, doubleSchema, integerSchema, Input), invalidTypes("path 'Data' actual: 'Double' expected: 'Integer'")),
      (sConfig(sampleBytes, bytesSchema, integerSchema, Input), invalidTypes("path 'Data' actual: 'ByteBuffer' expected: 'Integer'")),

        (sConfig(sampleInteger, integerSchema, null), invalidTypes("path 'Data' actual: 'Null' expected: 'Integer'")),
      (sConfig(sampleInteger, integerSchema, sampleString), invalidTypes(s"path 'Data' actual: '${typeStr.display}' expected: 'Integer'")),
      (sConfig(sampleInteger, integerSchema, sampleBoolean), invalidTypes(s"path 'Data' actual: '${typeBool.display}' expected: 'Integer'")),
      (sConfig(sampleInteger, integerSchema, sampleFloat), invalidTypes(s"path 'Data' actual: '${typeFloat.display}' expected: 'Integer'")),
      (sConfig(sampleInteger, integerSchema, s"T(java.lang.Double).valueOf(1)"), invalidTypes(s"path 'Data' actual: 'Double' expected: 'Integer'")),

      //Primitive long validations
      (sConfig(sampleInteger, integerSchema, longSchema, Input), valid(sampleInteger)), //Int -> Long?
      (sConfig(sampleBoolean, booleanSchema, longSchema, sampleInteger), valid(sampleInteger)), //Int -> Long?

      (sConfig(null, nullSchema, longSchema, Input), invalidTypes("path 'Data' actual: 'Null' expected: 'Long'")),
      (sConfig(sampleBoolean, booleanSchema, longSchema, Input), invalidTypes("path 'Data' actual: 'Boolean' expected: 'Long'")),
      (sConfig(sampleString, stringSchema, longSchema, Input), invalidTypes("path 'Data' actual: 'String' expected: 'Long'")),
      (sConfig(sampleFloat, floatSchema, longSchema, Input), invalidTypes("path 'Data' actual: 'Float' expected: 'Long'")),
      (sConfig(sampleDouble, doubleSchema, longSchema, Input), invalidTypes("path 'Data' actual: 'Double' expected: 'Long'")),
      (sConfig(sampleBytes, bytesSchema, longSchema, Input), invalidTypes("path 'Data' actual: 'ByteBuffer' expected: 'Long'")),

      (sConfig(sampleLong, longSchema, null), invalidTypes("path 'Data' actual: 'Null' expected: 'Long'")),
      (sConfig(sampleLong, longSchema, sampleString), invalidTypes(s"path 'Data' actual: '${typeStr.display}' expected: 'Long'")),
      (sConfig(sampleLong, longSchema, sampleBoolean), invalidTypes(s"path 'Data' actual: '${typeBool.display}' expected: 'Long'")),
      (sConfig(sampleLong, longSchema, sampleFloat), invalidTypes(s"path 'Data' actual: '${typeFloat.display}' expected: 'Long'")),
      (sConfig(sampleLong, longSchema, "T(java.lang.Double).valueOf(1)"), invalidTypes("path 'Data' actual: 'Double' expected: 'Long'")),

      //Primitive float validations
      (sConfig(sampleDouble, doubleSchema, floatSchema, Input), valid(sampleDouble)), //Double -> Float?
      (sConfig(sampleBoolean, booleanSchema, floatSchema, sampleDouble), valid(sampleDouble)), //Double -> Float?
      (sConfig(sampleInteger, integerSchema, floatSchema, Input), valid(sampleInteger)),
      (sConfig(sampleBoolean, booleanSchema, floatSchema, sampleInteger), valid(sampleInteger)),
      (sConfig(sampleLong, longSchema, floatSchema, Input), valid(sampleLong)), //Long -> Float?
      (sConfig(sampleBoolean, booleanSchema, floatSchema, sampleLong), valid(sampleLong)), //Long -> Float?

      (sConfig(null, nullSchema, floatSchema, Input), invalidTypes("path 'Data' actual: 'Null' expected: 'Float'")),
      (sConfig(sampleBoolean, booleanSchema, floatSchema, Input), invalidTypes("path 'Data' actual: 'Boolean' expected: 'Float'")),
      (sConfig(sampleString, stringSchema, floatSchema, Input), invalidTypes("path 'Data' actual: 'String' expected: 'Float'")),
      (sConfig(sampleBytes, bytesSchema, floatSchema, Input), invalidTypes("path 'Data' actual: 'ByteBuffer' expected: 'Float'")),

      (sConfig(sampleFloat, floatSchema, null), invalidTypes("path 'Data' actual: 'Null' expected: 'Float'")),
      (sConfig(sampleFloat, floatSchema, sampleString), invalidTypes(s"path 'Data' actual: '${typeStr.display}' expected: 'Float'")),
      (sConfig(sampleFloat, floatSchema, sampleBoolean), invalidTypes(s"path 'Data' actual: '${typeBool.display}' expected: 'Float'")),

      //Primitive Double validations
      (sConfig(sampleFloat, floatSchema, doubleSchema, Input), valid(sampleFloat)), // Float with Double Schema => Float ?
      (sConfig(sampleBoolean, booleanSchema, doubleSchema, sampleFloat), valid(sampleFloat)),
      (sConfig(sampleInteger, integerSchema, doubleSchema, Input), valid(sampleInteger)), // Int with Double Schema => Int ?
      (sConfig(sampleBoolean, booleanSchema, doubleSchema, sampleInteger), valid(sampleInteger)), // Int with Double Schema => Int ?
      (sConfig(sampleLong, longSchema, doubleSchema, Input), valid(sampleLong)), // Long with Double Schema => Long ?
      (sConfig(sampleLong, longSchema, doubleSchema, sampleLong), valid(sampleLong)), // Long with Double Schema => Long ?

      (sConfig(null, nullSchema, doubleSchema, Input), invalidTypes("path 'Data' actual: 'Null' expected: 'Double'")),
      (sConfig(sampleBoolean, booleanSchema, doubleSchema, Input), invalidTypes("path 'Data' actual: 'Boolean' expected: 'Double'")),
      (sConfig(sampleString, stringSchema, doubleSchema, Input), invalidTypes("path 'Data' actual: 'String' expected: 'Double'")),
      (sConfig(sampleBytes, bytesSchema, doubleSchema, Input), invalidTypes("path 'Data' actual: 'ByteBuffer' expected: 'Double'")),

      (sConfig(sampleDouble, doubleSchema, null), invalidTypes("path 'Data' actual: 'Null' expected: 'Double'")),
      (sConfig(sampleDouble, doubleSchema, sampleString), invalidTypes(s"path 'Data' actual: '${typeStr.display}' expected: 'Double'")),
      (sConfig(sampleDouble, doubleSchema, sampleBoolean), invalidTypes(s"path 'Data' actual: '${typeBool.display}' expected: 'Double'")),

      //Record with union field validations
      (rConfig(sampleBoolean, recordBooleanSchema, recordUnionOfStringInteger, sampleInteger, None), rValid(sampleInteger, recordUnionOfStringInteger)),
      (rConfig(sampleBoolean, recordBooleanSchema, recordUnionOfStringInteger, sampleString, None), rValid(sampleString, recordUnionOfStringInteger)),
      (ScenarioConfig(sampleString, stringSchema, recordUnionOfStringInteger, Input, None), invalidTypes("path 'Data' actual: 'String' expected: '{field: String | Integer}'")),
      (rConfig(sampleBoolean, recordMaybeBoolean, recordUnionOfStringInteger, Input), invalidTypes("path 'field' actual: 'Boolean' expected: 'String | Integer'")),

      //Array validations
      (rConfig(List("12"), recordWithArrayOfStrings, recordWithArrayOfNumbers, Input), invalidTypes("path 'field[]' actual: 'String' expected: 'Integer | Double'")),
      (rConfig(sampleInteger, recordIntegerSchema, recordWithArrayOfNumbers, "{1.0, 2.5}"), rValid(List(1, 2), recordWithArrayOfNumbers)), //bug with serialization / deserialization union?? There should be List(1.0, 2.5) - casting to first schema: there first is int
      (rConfig(sampleInteger, recordIntegerSchema, recordWithArrayOfNumbers, s"""{"$sampleString"}"""), invalidTypes(s"path 'field[]' actual: '${typeStr.display}' expected: 'Integer | Double'")),
      //FIXME: List[Unknown] (rConfig(sampleInteger, recordIntegerSchema, recordWithArrayOfNumbers, s"""{$sampleBoolean, "$sampleString"}"""), invalidTypes(s"path 'field[]' actual: '${typeBool.display} | ${typeStr.display}' expected: 'Integer | Double'")),
      (rConfig(sampleInteger, recordIntegerSchema, recordWithArrayOfNumbers, sampleInteger), invalidTypes(s"path 'field' actual: '${typeInt.display}' expected: 'List[Integer | Double]'")),
      (rConfig(sampleInteger, recordIntegerSchema, recordWithArrayOfNumbers, null), invalidTypes("path 'field' actual: 'Null' expected: 'List[Integer | Double]'")),
      (rConfig(sampleInteger, recordIntegerSchema, recordWithArrayOfNumbers, EmptyRoot), invalid(Nil, List("field"), Nil)),

      (rConfig(List("12"), recordWithArrayOfStrings, recordWithMaybeArrayOfNumbers, Input), invalidTypes("path 'field[]' actual: 'String' expected: 'Integer | Double'")),
      (rConfig(sampleInteger, recordIntegerSchema, recordWithMaybeArrayOfNumbers, "{1.0, 2.5}"), rValid(List(1, 2), recordWithMaybeArrayOfNumbers)), //bug with serialization / deserialization union?? There should be List(1.0, 2.5) - casting to first schema: there first is int
      (rConfig(sampleInteger, recordIntegerSchema, recordWithMaybeArrayOfNumbers, s"""{"$sampleString"}"""), invalidTypes(s"path 'field[]' actual: '${typeStr.display}' expected: 'Integer | Double'")),
      //FIXME: List[Unknown] (rConfig(sampleInteger, recordIntegerSchema, recordWithMaybeArrayOfNumbers, s"""{$sampleBoolean, "$sampleString"}"""), invalidTypes("path 'field[]' actual: '${typeBool.display} | ${typeStr.display}' expected: 'Integer | Double'")),
      (rConfig(sampleInteger, recordIntegerSchema, recordWithMaybeArrayOfNumbers, sampleInteger), invalidTypes(s"path 'field' actual: '${typeInt.display}' expected: 'Null | List[Integer | Double]'")),
      (rConfig(sampleInteger, recordIntegerSchema, recordWithMaybeArrayOfNumbers, EmptyRoot), invalid(Nil, List("field"), Nil)),

      (rConfig(List("12"), recordWithArrayOfStrings, recordWithOptionalArrayOfNumbers, Input), invalidTypes("path 'field[]' actual: 'String' expected: 'Integer | Double'")),
      (rConfig(sampleInteger, recordIntegerSchema, recordWithOptionalArrayOfNumbers, "{1.0, 2.5}"), rValid(List(1, 2), recordWithOptionalArrayOfNumbers)), //bug with serialization / deserialization union?? There should be List(1.0, 2.5) - casting to first schema: there first is int
      //FIXME: List[Unknown]  (rConfig(sampleInteger, recordIntegerSchema, recordWithMaybeArrayOfNumbers, s"""{$sampleBoolean, "$sampleString"}"""), invalidTypes("path 'field[]' actual: '${typeBool.display} | ${typeStr.display}' expected: 'Integer | Double'")),
      (rConfig(sampleInteger, recordIntegerSchema, recordWithMaybeArrayOfNumbers, s"""{"$sampleString"}"""), invalidTypes(s"path 'field[]' actual: '${typeStr.display}' expected: 'Integer | Double'")),
      (rConfig(sampleInteger, recordIntegerSchema, recordWithOptionalArrayOfNumbers, sampleInteger), invalidTypes(s"path 'field' actual: '${typeInt.display}' expected: 'Null | List[Integer | Double]'")),

      (rConfig(List(List("12")), recordOptionalArrayOfArraysStrings, recordOptionalArrayOfArraysNumbers, Input), invalidTypes("path 'field[][]' actual: 'String' expected: 'Integer | Double'")),
      (rConfig(sampleInteger, recordIntegerSchema, recordOptionalArrayOfArraysNumbers, "{{1.0, 2.5}}"), rValid(List(List(1, 2)), recordOptionalArrayOfArraysNumbers)), //bug with serialization / deserialization union?? There should be List(1.0, 2.5) - casting to first schema: there first is int
      //FIXME: List[Unknown]  (rConfig(sampleInteger, recordIntegerSchema, recordOptionalArrayOfArraysNumbers, s"""{{$sampleBoolean, "$sampleString"}}"""), invalidTypes("path 'field[][]' actual: 'Boolean | String' expected: 'Integer | Double'")),
      (rConfig(sampleInteger, recordIntegerSchema, recordOptionalArrayOfArraysNumbers, s"""{{"$sampleString"}}"""), invalidTypes(s"path 'field[][]' actual: '${typeStr.display}' expected: 'Integer | Double'")),
      (rConfig(sampleInteger, recordIntegerSchema, recordOptionalArrayOfArraysNumbers, s"{$sampleInteger}"), invalidTypes(s"path 'field[]' actual: '${typeInt.display}' expected: 'Null | List[Integer | Double]'")),
      (rConfig(sampleInteger, recordIntegerSchema, recordOptionalArrayOfArraysNumbers, sampleInteger), invalidTypes(s"path 'field' actual: '${typeInt.display}' expected: 'Null | List[Null | List[Integer | Double]]'")),

      (rConfig(sampleInteger, recordIntegerSchema, recordOptionalArrayOfRecords, """{{"price1": "15.5"}}"""), invalid(Nil, List("field[].price"), List("field[].price1"))),
      (rConfig(sampleInteger, recordIntegerSchema, recordOptionalArrayOfRecords, """{{"price1": "15.5"}}""", Some(allowRedundant)), invalid(Nil, List("field[].price"), Nil)),
      (rConfig(sampleInteger, recordIntegerSchema, recordOptionalArrayOfRecords, s"""{{"price": "$sampleString"}}"""), invalidTypes(s"path 'field[].price' actual: '${typeStr.display}' expected: 'Null | Double'")),
      (rConfig(sampleInteger, recordIntegerSchema, recordOptionalArrayOfRecords, sampleInteger), invalidTypes(s"""path 'field' actual: '${typeInt.display}' expected: 'Null | List[Null | {price: Null | Double}]'""")),
      (rConfig(sampleInteger, recordIntegerSchema, recordOptionalArrayOfRecords, s"{$sampleInteger}"), invalidTypes(s"""path 'field[]' actual: '${typeInt.display}' expected: 'Null | {price: Null | Double}'""")),
      (rConfig(sampleInteger, recordIntegerSchema, recordOptionalArrayOfRecords, EmptyRoot), rValid(null, recordOptionalArrayOfRecords)),

      //Map validations
      (rConfig(Map("tax" -> "7"), recordMapOfStrings, recordMapOfInts, Input), invalidTypes("path 'field[*]' actual: 'String' expected: 'Null | Integer'")),
      (rConfig(sampleInteger, recordIntegerSchema, recordMapOfInts, """{"tax": 7, "vat": "23"}"""), invalidTypes("path 'field.vat' actual: 'String{23}' expected: 'Null | Integer'")),
      (rConfig(sampleInteger, recordIntegerSchema, recordMapOfInts, sampleInteger), invalidTypes(s"path 'field' actual: '${typeInt.display}' expected: 'Map[String, Null | Integer]'")),
      (rConfig(sampleInteger, recordIntegerSchema, recordMapOfInts, EmptyList), invalidTypes("path 'field' actual: 'List[Unknown]' expected: 'Map[String, Null | Integer]'")),
      (rConfig(sampleInteger, recordIntegerSchema, recordMapOfInts, null), invalidTypes("path 'field' actual: 'Null' expected: 'Map[String, Null | Integer]'")),
      (rConfig(sampleInteger, recordIntegerSchema, recordMapOfInts, EmptyRoot), invalid(Nil, List("field"), Nil)),

      (rConfig(Map("tax" -> "7"), recordMapOfStrings, recordMaybeMapOfInts, Input), invalidTypes("path 'field[*]' actual: 'String' expected: 'Null | Integer'")),
      (rConfig(sampleInteger, recordIntegerSchema, recordMaybeMapOfInts, """{"tax": 7, "vat": "23"}"""), invalidTypes("path 'field.vat' actual: 'String{23}' expected: 'Null | Integer'")),
      (rConfig(sampleInteger, recordIntegerSchema, recordMaybeMapOfInts, sampleInteger), invalidTypes(s"path 'field' actual: '${typeInt.display}' expected: 'Null | Map[String, Null | Integer]'")),
      (rConfig(sampleInteger, recordIntegerSchema, recordMaybeMapOfInts, EmptyList), invalidTypes("path 'field' actual: 'List[Unknown]' expected: 'Null | Map[String, Null | Integer]'")),
      (rConfig(sampleInteger, recordIntegerSchema, recordMaybeMapOfInts, EmptyRoot), invalid(Nil, List("field"), Nil)),

      (rConfig(Map("first" -> Map("tax" -> "7")), recordMapOfMapsStrings, recordOptionalMapOfMapsInts, Input), invalidTypes("path 'field[*][*]' actual: 'String' expected: 'Null | Integer'")),
      (rConfig(sampleInteger, recordIntegerSchema, recordOptionalMapOfMapsInts, """{first: {tax: 7, vat: "23"}}"""), invalidTypes("path 'field.first.vat' actual: 'String{23}' expected: 'Null | Integer'")),
      (rConfig(sampleInteger, recordIntegerSchema, recordOptionalMapOfMapsInts, sampleInteger), invalidTypes(s"path 'field' actual: '${typeInt.display}' expected: 'Null | Map[String, Null | Map[String, Null | Integer]]'")),
      (rConfig(sampleInteger, recordIntegerSchema, recordOptionalMapOfMapsInts, s"{first: $sampleInteger}"), invalidTypes(s"path 'field.first' actual: '${typeInt.display}' expected: 'Null | Map[String, Null | Integer]'")),
      (rConfig(sampleInteger, recordIntegerSchema, recordOptionalMapOfMapsInts, EmptyList), invalidTypes("path 'field' actual: 'List[Unknown]' expected: 'Null | Map[String, Null | Map[String, Null | Integer]]'")),
      (rConfig(sampleInteger, recordIntegerSchema, recordOptionalMapOfMapsInts, EmptyRoot), rValid(null, recordOptionalMapOfMapsInts)),

      (rConfig(Map("first" -> Map("price" -> "15.5")), recordOptionalMapOfStringRecords, recordOptionalMapOfRecords, Input), invalidTypes("path 'field[*].price' actual: 'String' expected: 'Null | Double'")),
      (rConfig(sampleInteger, recordIntegerSchema, recordOptionalMapOfRecords, s"""{"first": {"price": "$sampleString"}}"""), invalidTypes(s"path 'field.first.price' actual: '${typeStr.display}' expected: 'Null | Double'")),
      (rConfig(sampleInteger, recordIntegerSchema, recordOptionalMapOfRecords, sampleInteger), invalidTypes(s"path 'field' actual: '${typeInt.display}' expected: 'Null | Map[String, Null | {price: Null | Double}]'")),
      (rConfig(sampleInteger, recordIntegerSchema, recordOptionalMapOfRecords, s"{first: $sampleInteger}"), invalidTypes(s"path 'field.first' actual: '${typeInt.display}' expected: 'Null | {price: Null | Double}'")),
      (rConfig(sampleInteger, recordIntegerSchema, recordOptionalMapOfRecords, EmptyList), invalidTypes("path 'field' actual: 'List[Unknown]' expected: 'Null | Map[String, Null | {price: Null | Double}]'")),
      (rConfig(sampleMapOfRecords, recordOptionalMapOfRecords, recordOptionalMapOfRecords, EmptyRoot), rValid(null, recordOptionalMapOfRecords)),

      //Record validations
      (rConfig(Map("sub" -> Map("price" -> "15.5")), nestedRecordWithStringPriceSchema, nestedRecordSchema, Input), invalidTypes("path 'field.sub.price' actual: 'String' expected: 'Null | Double'")),
      (rConfig(sampleInteger, recordIntegerSchema, nestedRecordSchema, s"{sub: {price2: $sampleDouble}}"), invalid(Nil, List("field.sub.price"), List("field.sub.price2"))),
      (rConfig(sampleInteger, recordIntegerSchema, nestedRecordSchema, s"{sub: {price2: $sampleDouble}}", Some(allowRedundant)), invalid(Nil, List("field.sub.price"), Nil)),
      (rConfig(sampleInteger, recordIntegerSchema, nestedRecordSchema, s"""{sub: {price: "$sampleString"}}"""), invalidTypes(s"path 'field.sub.price' actual: '${typeStr.display}' expected: 'Null | Double'")),
      (rConfig(sampleInteger, recordIntegerSchema, nestedRecordSchema, Input), invalidTypes("path 'field' actual: 'Integer' expected: 'Null | {sub: Null | {price: Null | Double}}'")),
      (rConfig(sampleNestedRecord, nestedRecordSchema, nestedRecordSchema, sampleInteger), invalidTypes(s"path 'field' actual: '${typeInt.display}' expected: 'Null | {sub: Null | {price: Null | Double}}'")),
      (rConfig(sampleInteger, recordIntegerSchema, nestedRecordSchema, s"{sub: $sampleInteger}"), invalidTypes(s"path 'field.sub' actual: '${typeInt.display}' expected: 'Null | {price: Null | Double}'")),
      (rConfig(sampleInteger, recordIntegerSchema, nestedRecordSchema, EmptyList), invalidTypes("path 'field' actual: 'List[Unknown]' expected: 'Null | {sub: Null | {price: Null | Double}}'")),
      (rConfig(sampleNestedRecordV2, nestedRecordSchemaV2, nestedRecordSchema, Input), invalid(Nil, Nil, List("field.sub.currency", "field.str"))),
      (rConfig(sampleNestedRecordV2, nestedRecordSchemaV2, nestedRecordSchema, Input, Some(allowRedundant)), valid(sampleNestedRecord)),
      (rConfig(sampleNestedRecord, nestedRecordSchema, nestedRecordSchemaV2, Input), invalid(Nil, List("field.str"), Nil)),

      //Enum validations
      (rConfig(sampleInteger, recordIntegerSchema, recordEnumSchema, sampleEnumString), rValid(sampleEnum, recordEnumSchema)),
      (rConfig(sampleInteger, recordIntegerSchema, recordEnumSchema, sampleEnumV2String), invalidTypes(s"path 'field' actual: '${typeEnumV2Str.display}' expected: 'EnumSymbol[SPADES | HEARTS | DIAMONDS | CLUBS] | String'")),
      (rConfig(sampleInteger, recordIntegerSchema, recordEnumSchema, Input), invalidTypes("path 'field' actual: 'Integer' expected: 'EnumSymbol[SPADES | HEARTS | DIAMONDS | CLUBS] | String'")),
      (rConfig(sampleInteger, recordIntegerSchema, recordEnumSchema, sampleInteger), invalidTypes(s"path 'field' actual: '${typeInt.display}' expected: 'EnumSymbol[SPADES | HEARTS | DIAMONDS | CLUBS] | String'")),

      //Fixed validations
      (rConfig(sampleFixed, recordFixedSchema, recordFixedSchema, Input), rValid(sampleFixed, recordFixedSchema)),
      (rConfig(sampleInteger, recordIntegerSchema, recordFixedSchema, sampleFixedString), rValid(sampleFixed, recordFixedSchema)),
      (rConfig(sampleInteger, recordIntegerSchema, recordFixedSchema, sampleFixedV2String), invalidTypes(s"path 'field' actual: '${typeFixedV2str.display}' expected: 'Fixed[32] | ByteBuffer | String'")),
      (rConfig(sampleInteger, recordIntegerSchema, recordFixedSchema, Input), invalidTypes("path 'field' actual: 'Integer' expected: 'Fixed[32] | ByteBuffer | String'")),
      (rConfig(sampleInteger, recordIntegerSchema, recordFixedSchema, sampleInteger), invalidTypes(s"path 'field' actual: '${typeInt.display}' expected: 'Fixed[32] | ByteBuffer | String'")),

      //Logical: UUID validations
      (rConfig(sampleUUID, recordUUIDSchema, recordUUIDSchema, Input), rValid(sampleUUID, recordUUIDSchema)),
      (rConfig(sampleUUID.toString, recordStringSchema, recordUUIDSchema, Input), rValid(sampleUUID, recordUUIDSchema)),
      (rConfig(sampleInteger, recordIntegerSchema, recordUUIDSchema, s"""T(java.util.UUID).fromString("${sampleUUID.toString}")"""), rValid(sampleUUID, recordUUIDSchema)),
      (rConfig(sampleInteger, recordIntegerSchema, recordUUIDSchema, sampleUUID.toString), rValid(sampleUUID, recordUUIDSchema)),
      (rConfig(sampleInteger, recordIntegerSchema, recordUUIDSchema, Input), invalidTypes("path 'field' actual: 'Integer' expected: 'UUID | String'")),
      (rConfig(sampleInteger, recordIntegerSchema, recordUUIDSchema, sampleInteger), invalidTypes(s"path 'field' actual: '${typeInt.display}' expected: 'UUID | String'")),
      (rConfig(sampleInteger, recordIntegerSchema, recordUUIDSchema, sampleString), invalidTypes(s"path 'field' actual: '${typeStr.display}' expected: 'UUID | String'")),

      //Logical: BigDecimal validations
      (rConfig(sampleDecimal, recordDecimalSchema, recordDecimalSchema, Input), rValid(sampleDecimal, recordDecimalSchema)),
      (rConfig(sampleString, recordStringSchema, recordDecimalSchema, "T(java.math.BigDecimal).valueOf(1l).setScale(2)"), rValid(sampleDecimal, recordDecimalSchema)),
      (rConfig(sampleString, recordStringSchema, recordDecimalSchema, sampleDecimal), invalidTypes(s"path 'field' actual: 'Double{1.0}' expected: 'BigDecimal | ByteBuffer'")),
      (rConfig(sampleInteger, recordIntegerSchema, recordDecimalSchema, Input), invalidTypes("path 'field' actual: 'Integer' expected: 'BigDecimal | ByteBuffer'")),
      (rConfig(sampleInteger, recordIntegerSchema, recordDecimalSchema, sampleInteger), invalidTypes(s"path 'field' actual: '${typeInt.display}' expected: 'BigDecimal | ByteBuffer'")),

      //Logical: Date validations -> LocalDate
      (rConfig(sampleDate, recordDateSchema, recordDateSchema, Input), rValid(sampleDate, recordDateSchema)),
      (rConfig(sampleString, recordStringSchema, recordDateSchema, s"T(java.time.LocalDate).ofEpochDay(${sampleDate.toEpochDay})"), rValid(sampleDate, recordDateSchema)),
      (rConfig(sampleDate.toEpochDay, recordIntegerSchema, recordDateSchema, Input), rValid(sampleDate, recordDateSchema)),
      (rConfig(sampleString, recordStringSchema, recordDateSchema, sampleDate.toEpochDay.toInt), rValid(sampleDate, recordDateSchema)),
      (rConfig(sampleString, recordStringSchema, recordDateSchema, Input), invalidTypes("path 'field' actual: 'String' expected: 'LocalDate | Integer'")),
      (rConfig(sampleDate, recordDateSchema, recordDateSchema, sampleString), invalidTypes(s"path 'field' actual: '${typeStr.display}' expected: 'LocalDate | Integer'")),

      //Logical: Time Millis -> LocalTime
      (rConfig(sampleMillisLocalTime, recordTimeMillisSchema, recordTimeMillisSchema, Input), rValid(sampleMillisLocalTime, recordTimeMillisSchema)),
      (rConfig(sampleString, recordStringSchema, recordTimeMillisSchema, s"T(java.time.LocalTime).ofNanoOfDay(${sampleMillisLocalTime.toNanoOfDay}l)"), rValid(sampleMillisLocalTime, recordTimeMillisSchema)),
      (rConfig(sampleMillisLocalTime.toMillis, recordIntegerSchema, recordTimeMillisSchema, Input), rValid(sampleMillisLocalTime, recordTimeMillisSchema)),
      (rConfig(sampleString, recordStringSchema, recordTimeMillisSchema, sampleMillisLocalTime.toMillis), rValid(sampleMillisLocalTime, recordTimeMillisSchema)),
      (rConfig(sampleString, recordStringSchema, recordTimeMillisSchema, Input), invalidTypes("path 'field' actual: 'String' expected: 'LocalTime | Integer'")),
      (rConfig(sampleString, recordStringSchema, recordTimeMillisSchema, sampleString), invalidTypes(s"path 'field' actual: '${typeStr.display}' expected: 'LocalTime | Integer'")),

      //Logical: Time Micros -> LocalTime
      (rConfig(sampleMicrosLocalTime, recordTimeMicrosSchema, recordTimeMicrosSchema, Input), rValid(sampleMicrosLocalTime, recordTimeMicrosSchema)),
      (rConfig(sampleString, recordStringSchema, recordTimeMicrosSchema, s"T(java.time.LocalTime).ofNanoOfDay(${sampleMicrosLocalTime.toNanoOfDay}l)"), rValid(sampleMicrosLocalTime, recordTimeMicrosSchema)),
      (rConfig(sampleMicrosLocalTime.toMicros, recordLongSchema, recordTimeMicrosSchema, Input), rValid(sampleMicrosLocalTime, recordTimeMicrosSchema)),
      (rConfig(sampleString, recordStringSchema, recordTimeMicrosSchema, sampleMicrosLocalTime.toMicros), rValid(sampleMicrosLocalTime, recordTimeMicrosSchema)),
      (rConfig(sampleInteger, recordIntegerSchema, recordTimeMicrosSchema, Input), invalidTypes("path 'field' actual: 'Integer' expected: 'LocalTime | Long'")),
      (rConfig(sampleInteger, recordIntegerSchema, recordTimeMicrosSchema, sampleInteger), invalidTypes(s"path 'field' actual: '${typeInt.display}' expected: 'LocalTime | Long'")),

      //Logical: Timestamp Millis -> Instant
      (rConfig(sampleMillisInstant, recordTimestampMillisSchema, recordTimestampMillisSchema, Input), rValid(sampleMillisInstant, recordTimestampMillisSchema)),
      (rConfig(sampleString, recordStringSchema, recordTimestampMillisSchema, s"T(java.time.Instant).ofEpochMilli(${sampleMillisInstant.toEpochMilli}l)"), rValid(sampleMillisInstant, recordTimestampMillisSchema)),
      (rConfig(sampleMillisInstant.toEpochMilli, recordLongSchema, recordTimestampMillisSchema, Input), rValid(sampleMillisInstant, recordTimestampMillisSchema)),
      (rConfig(sampleString, recordStringSchema, recordTimestampMillisSchema, sampleMillisInstant.toEpochMilli), rValid(sampleMillisInstant, recordTimestampMillisSchema)),
      (rConfig(sampleInteger, recordIntegerSchema, recordTimestampMillisSchema, Input), invalidTypes("path 'field' actual: 'Integer' expected: 'Instant | Long'")),
      (rConfig(sampleInteger, recordIntegerSchema, recordTimestampMillisSchema, sampleInteger), invalidTypes(s"path 'field' actual: '${typeInt.display}' expected: 'Instant | Long'")),

      //Logical: Timestamp Micros -> Instant
      (rConfig(sampleMicrosInstant, recordTimestampMicrosSchema, recordTimestampMicrosSchema, Input), rValid(sampleMicrosInstant, recordTimestampMicrosSchema)),
      (rConfig(sampleString, recordStringSchema, recordTimestampMicrosSchema, s"T(java.time.Instant).ofEpochSecond(${sampleMicrosInstant.toMicrosFromEpoch}l, ${sampleMicrosInstant.toNanoAdjustment}l)"), rValid(sampleMicrosInstant, recordTimestampMicrosSchema)),
      (rConfig(sampleMicrosInstant.toMicros, recordLongSchema, recordTimestampMicrosSchema, Input), rValid(sampleMicrosInstant, recordTimestampMicrosSchema)),
      (rConfig(sampleString, recordStringSchema, recordTimestampMicrosSchema, sampleMicrosInstant.toMicros), rValid(sampleMicrosInstant, recordTimestampMicrosSchema)),
      (rConfig(sampleInteger, recordIntegerSchema, recordTimestampMicrosSchema, Input), invalidTypes("path 'field' actual: 'Integer' expected: 'Instant | Long'")),
      (rConfig(sampleInteger, recordIntegerSchema, recordTimestampMicrosSchema, sampleInteger), invalidTypes(s"path 'field' actual: '${typeInt.display}' expected: 'Instant | Long'")),
    )

    forAll(testData) { (config: ScenarioConfig, expected: Validated[_, RunResult[_]]) =>
      val results = runWithValueResults(config)
      results shouldBe expected
    }

  }

  test("should catch runtime errors") {
    val testData = Table(
      ("config", "expectedMessage"),
      //Comparing String -> Enum returns true, but in runtime BestEffortAvroEncoder tries to encode String (that doesn't meet the requirements) to Enum
      (rConfig(sampleFixedV2String, recordStringSchema, recordEnumSchema, Input), badContainerMessage (recordStringSchema, recordEnumSchema)),

      //Comparing EnumV2 -> Enum returns true, but in runtime BestEffortAvroEncoder tries to encode String (that doesn't meet the requirements) to Enum
      (rConfig(sampleEnumV2, recordEnumSchemaV2, recordEnumSchema, Input), badContainerMessage(recordEnumSchemaV2, recordEnumSchema)),

      //Comparing String -> Fixed returns true, but in runtime BestEffortAvroEncoder tries to encode String (that doesn't meet the requirements) to Fixed
      (rConfig(sampleString, recordStringSchema, recordFixedSchema, Input), badContainerMessage (recordStringSchema, recordFixedSchema)),

      //Comparing FixedV2 -> Fixed returns true, but in runtime BestEffortAvroEncoder tries to encode value FixedV2 to Fixed
      (rConfig(sampleFixedV2, recordFixedSchemaV2, recordFixedSchema, Input), badContainerMessage(recordFixedSchemaV2, recordFixedSchema)),

      //Situation when we put String -> UUID, where String isn't valid UUID type...
      (rConfig(sampleString, recordStringSchema, recordUUIDSchema, Input), badContainerMessage(recordStringSchema, recordUUIDSchema)),
    )

    forAll(testData) { (config: ScenarioConfig, expectedMessage: String) =>
      val results = runWithValueResults(config)
      val message = results.validValue.errors.head.throwable.asInstanceOf[AvroRuntimeException].getMessage
      message shouldBe expectedMessage
    }

  }

  //Error / bug on field schema evolution... SubV1 -> SubV2 ( currency with default value - optional field )
  test("should catch runtime errors on field schema evolution") {
    val config = rConfig(sampleNestedRecord, nestedRecordSchema, nestedRecordSchemaV2, s"""{"sub": #input.field.sub, "str": "$sampleString"}""")
    val results = runWithValueResults(config)

    val error = results.validValue.errors.head.throwable.asInstanceOf[SerializationException]
    error.getMessage shouldBe "Error serializing Avro message"

    error.getCause.getMessage shouldBe s"""Not in union ${nestedRecordSchemaV2Fields}: {"sub": {"price": $sampleDouble}, "str": "$sampleString"} (field=$RecordFieldName)"""
  }

  private def runWithValueResults(config: ScenarioConfig) =
    runWithResults(config).map{runResult =>
      runResult.copy(successes = runResult.successes.map(r => r.value() match {
        case bytes: Array[Byte] => ByteBuffer.wrap(bytes) //We convert bytes to byte buffer because comparing array[byte] compares reference
        case v => v
      }))
    }

  private def runWithResults(config: ScenarioConfig): RunnerResult[ProducerRecord[String, Any]] = {
    val avroScenario: EspProcess = createScenario(config)
    val sourceSchemaId = runtime.registerAvroSchema(config.sourceTopic, config.sourceSchema)
    runtime.registerAvroSchema(config.sinkTopic, config.sinkSchema)

    val input = KafkaAvroConsumerRecord(config.sourceTopic, config.inputData, sourceSchemaId)
    runtime.runWithAvroData(avroScenario, List(input))
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
        SinkValueParamName -> s"${config.sinkDefinition}",
        SinkValidationModeParameterName -> s"'${config.validationModeName}'"
      )

  object ScenarioConfig {

    private def randomTopic = UUID.randomUUID().toString

    def apply(inputData: Any, schema: Schema, sinkDefinition: String, validationMode: Option[ValidationMode]): ScenarioConfig =
      new ScenarioConfig(randomTopic, inputData, schema, schema, sinkDefinition, validationMode)

    def apply(inputData: Any, sourceSchema: Schema, sinkSchema: Schema, sinkDefinition: String, validationMode: Option[ValidationMode]): ScenarioConfig =
      new ScenarioConfig(randomTopic, inputData, sourceSchema, sinkSchema, sinkDefinition, validationMode)

  }

  case class ScenarioConfig(topic: String, inputData: Any, sourceSchema: Schema, sinkSchema: Schema, sinkDefinition: String, validationMode: Option[ValidationMode]) {
    lazy val validationModeName: String = validationMode.map(_.name).getOrElse(ValidationMode.strict.name)
    lazy val sourceTopic = s"$topic-input"
    lazy val sinkTopic = s"$topic-output"
  }

  //RecordValid -> valid success record with base field
  private def rValid(data: Any, schema: Schema): Valid[RunResult[GenericRecord]] = {
    valid(AvroUtils.createRecord(schema, Map(RecordFieldName -> data)))
  }

  private def valid[T](data: T): Valid[RunResult[T]] =
    Valid(RunResult.success(data))

  private def invalidTypes(typeErrors: String*): Invalid[NonEmptyList[CustomNodeError]] =
    invalid(typeErrors.toList, Nil, Nil)

  private def invalid(typeFieldErrors: List[String], missingFieldsError: List[String], redundantFieldsError: List[String]): Invalid[NonEmptyList[CustomNodeError]] = {
    val finalMessage = OutputValidatorErrorsMessageFormatter.makeMessage(typeFieldErrors, missingFieldsError, redundantFieldsError)
    Invalid(NonEmptyList.one(CustomNodeError(sinkName, finalMessage, Some(KafkaAvroBaseComponentTransformer.SinkValueParamName))))
  }

  //RecordConfig -> config with record as a input
  private def rConfig(inputData: Any, sourceSchema: Schema, sinkSchema: Schema, output: Any, validationMode: Option[ValidationMode] = None): ScenarioConfig = {
    val sinkDefinition = AvroSinkOutputSpELConverter.convertToMap(RecordFieldName, output)

    val input = inputData match {
      case record: GenericRecord => record
      case any => AvroUtils.createRecord(sourceSchema, Map(RecordFieldName -> any))
    }

    ScenarioConfig(input, sourceSchema, sinkSchema, sinkDefinition, validationMode)
  }

  //StandardConfig -> simple avro type as a input
  private def sConfig(inputData: Any, schema: Schema, output: Any): ScenarioConfig =
    sConfig(inputData, schema, schema, output, None)

  private def sConfig(inputData: Any, sourceSchema: Schema, sinkSchema: Schema, output: Any, validationMode: Option[ValidationMode] = None): ScenarioConfig = {
    val sinkDefinition = AvroSinkOutputSpELConverter.convert(output)
    ScenarioConfig(inputData, sourceSchema, sinkSchema, sinkDefinition, validationMode)
  }

}
