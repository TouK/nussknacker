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
import pl.touk.nussknacker.engine.avro.{AvroUtils, KafkaAvroBaseComponentTransformer}
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.lite.components.AvroGen.genValueForSchema
import pl.touk.nussknacker.engine.lite.components.AvroTestData._
import pl.touk.nussknacker.engine.lite.util.test.{KafkaAvroConsumerRecord, LiteKafkaTestScenarioRunner}
import pl.touk.nussknacker.engine.util.namespaces.DefaultNamespacedObjectNaming
import pl.touk.nussknacker.engine.util.output.OutputValidatorErrorsMessageFormatter
import pl.touk.nussknacker.engine.util.test.RunResult
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner.RunnerResult
import pl.touk.nussknacker.test.{SpecialSpELElement, ValidatedValuesDetailedMessage}

import java.nio.ByteBuffer
import java.util.UUID

class LiteKafkaAvroFunctionalTest extends FunSuite with Matchers with ScalaCheckDrivenPropertyChecks with Inside with TableDrivenPropertyChecks with ValidatedValuesDetailedMessage {

  import LiteKafkaComponentProvider._
  import LiteKafkaTestScenarioRunner._
  import ValidationMode._
  import pl.touk.nussknacker.engine.avro.KafkaAvroBaseComponentTransformer._
  import pl.touk.nussknacker.engine.spel.Implicits._
  import pl.touk.nussknacker.test.LiteralSpEL._
  import LiteralSpELWithAvroImplicits._
  import SpecialSpELElement._

  private val EmptyBaseObject: SpecialSpELElement = SpecialSpELElement("{:}")

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

  implicit override val generatorDrivenConfig: PropertyCheckConfiguration = PropertyCheckConfiguration(minSuccessful = 1000, minSize = 0, workers = 5)

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
    //We can't in easy way put bytes information, because ByteArray | ByteBuffer is not available at SpEL context
    val config = ExcludedConfig.Base.withGlobal(Type.BYTES, Type.FIXED)
    val genScenarioConfig = for {
      schema <- AvroGen.genSchema(config)
      value <- genValueForSchema(schema)
    } yield (value, ScenarioConfig(sampleBytes, bytesSchema, schema, value.toSpELLiteral, None))

    forAll(genScenarioConfig) { case (value, config) =>
      val resultsInput = runWithValueResults(config)
      val expected = valid(value)

      resultsInput shouldBe expected
    }
  }

  test("should test end to end kafka avro record data at sink / source") {
    val testData = Table(
      ("config", "result"),
      //FIXME: java.nio.ByteBuffer is not available from SpEL (sConfig(sampleString, stringSchema, bytesSchema, """T(java.nio.ByteBuffer).wrap(#input.getBytes("UTF-8"))"""), valid(ByteBuffer.wrap(sampleBytes))),

      //Primitive integer validations
      (sConfig(sampleInteger, longSchema, integerSchema, Input), valid(sampleInteger)), //FIXME: Long -> Int?
      (sConfig(sampleLong, longSchema, integerSchema, Input), valid(sampleLong.toInt)), //FIXME: Long -> Int?
      (sConfig(sampleBoolean, booleanSchema, integerSchema, sampleLong), valid(sampleLong.toInt)), //FIXME: Long -> Int?

      (sConfig(null, nullSchema, integerSchema, Input), invalidTypes("path 'Data' actual: 'Null' expected: 'Integer'")),
      (sConfig(sampleBoolean, booleanSchema, integerSchema, Input), invalidTypes("path 'Data' actual: 'Boolean' expected: 'Integer'")),
      (sConfig(sampleString, stringSchema, integerSchema, Input), invalidTypes("path 'Data' actual: 'String' expected: 'Integer'")),
      (sConfig(sampleFloat, floatSchema, integerSchema, Input), invalidTypes("path 'Data' actual: 'Float' expected: 'Integer'")),
      (sConfig(sampleDouble, doubleSchema, integerSchema, Input), invalidTypes("path 'Data' actual: 'Double' expected: 'Integer'")),
      (sConfig(sampleBytes, bytesSchema, integerSchema, Input), invalidTypes("path 'Data' actual: 'ByteBuffer' expected: 'Integer'")),

      (sConfig(sampleInteger, integerSchema, null), invalidTypes("path 'Data' actual: 'Null' expected: 'Integer'")),
      (sConfig(sampleInteger, integerSchema, sampleString), invalidTypes(s"path 'Data' actual: '${typedStr.display}' expected: 'Integer'")),
      (sConfig(sampleInteger, integerSchema, sampleBoolean), invalidTypes(s"path 'Data' actual: '${typedBool.display}' expected: 'Integer'")),
      (sConfig(sampleInteger, integerSchema, sampleFloat), invalidTypes(s"path 'Data' actual: '${typedFloat.display}' expected: 'Integer'")),
      (sConfig(sampleInteger, integerSchema, double(1)), invalidTypes(s"path 'Data' actual: 'Double' expected: 'Integer'")),

      //Primitive long validations
      (sConfig(sampleInteger, integerSchema, longSchema, Input), valid(sampleInteger)), //FIXME: Int -> Long
      (sConfig(sampleBoolean, booleanSchema, longSchema, sampleInteger), valid(sampleInteger)), //FIXME: Int -> Long

      (sConfig(null, nullSchema, longSchema, Input), invalidTypes("path 'Data' actual: 'Null' expected: 'Long'")),
      (sConfig(sampleBoolean, booleanSchema, longSchema, Input), invalidTypes("path 'Data' actual: 'Boolean' expected: 'Long'")),
      (sConfig(sampleString, stringSchema, longSchema, Input), invalidTypes("path 'Data' actual: 'String' expected: 'Long'")),
      (sConfig(sampleFloat, floatSchema, longSchema, Input), invalidTypes("path 'Data' actual: 'Float' expected: 'Long'")),
      (sConfig(sampleDouble, doubleSchema, longSchema, Input), invalidTypes("path 'Data' actual: 'Double' expected: 'Long'")),
      (sConfig(sampleBytes, bytesSchema, longSchema, Input), invalidTypes("path 'Data' actual: 'ByteBuffer' expected: 'Long'")),

      (sConfig(sampleLong, longSchema, null), invalidTypes("path 'Data' actual: 'Null' expected: 'Long'")),
      (sConfig(sampleLong, longSchema, sampleString), invalidTypes(s"path 'Data' actual: '${typedStr.display}' expected: 'Long'")),
      (sConfig(sampleLong, longSchema, sampleBoolean), invalidTypes(s"path 'Data' actual: '${typedBool.display}' expected: 'Long'")),
      (sConfig(sampleLong, longSchema, sampleFloat), invalidTypes(s"path 'Data' actual: '${typedFloat.display}' expected: 'Long'")),
      (sConfig(sampleLong, longSchema, double(1)), invalidTypes("path 'Data' actual: 'Double' expected: 'Long'")),

      //Primitive float validations
      (sConfig(sampleDouble, doubleSchema, floatSchema, Input), valid(sampleDouble)), //FIXME: Double -> Float?
      (sConfig(sampleBoolean, booleanSchema, floatSchema, sampleDouble), valid(sampleDouble)), //FIXME: Double -> Float?
      (sConfig(sampleInteger, integerSchema, floatSchema, Input), valid(sampleInteger)), //FIXME: Int -> Float?
      (sConfig(sampleBoolean, booleanSchema, floatSchema, sampleInteger), valid(sampleInteger)), //FIXME: Int -> Float?
      (sConfig(sampleLong, longSchema, floatSchema, Input), valid(sampleLong)), //FIXME: Long -> Float?
      (sConfig(sampleBoolean, booleanSchema, floatSchema, sampleLong), valid(sampleLong)), //FIXME: Long -> Float?

      (sConfig(null, nullSchema, floatSchema, Input), invalidTypes("path 'Data' actual: 'Null' expected: 'Float'")),
      (sConfig(sampleBoolean, booleanSchema, floatSchema, Input), invalidTypes("path 'Data' actual: 'Boolean' expected: 'Float'")),
      (sConfig(sampleString, stringSchema, floatSchema, Input), invalidTypes("path 'Data' actual: 'String' expected: 'Float'")),
      (sConfig(sampleBytes, bytesSchema, floatSchema, Input), invalidTypes("path 'Data' actual: 'ByteBuffer' expected: 'Float'")),

      (sConfig(sampleFloat, floatSchema, null), invalidTypes("path 'Data' actual: 'Null' expected: 'Float'")),
      (sConfig(sampleFloat, floatSchema, sampleString), invalidTypes(s"path 'Data' actual: '${typedStr.display}' expected: 'Float'")),
      (sConfig(sampleFloat, floatSchema, sampleBoolean), invalidTypes(s"path 'Data' actual: '${typedBool.display}' expected: 'Float'")),

      //Primitive Double validations
      (sConfig(sampleFloat, floatSchema, doubleSchema, Input), valid(sampleFloat)), //FIXME: Float with Double Schema => Float ?
      (sConfig(sampleBoolean, booleanSchema, doubleSchema, sampleFloat), valid(sampleFloat)), //FIXME: Float with Double Schema => Float ?
      (sConfig(sampleInteger, integerSchema, doubleSchema, Input), valid(sampleInteger)), //FIXME: Int with Double Schema => Int ?
      (sConfig(sampleBoolean, booleanSchema, doubleSchema, sampleInteger), valid(sampleInteger)), //FIXME: Int with Double Schema => Int ?
      (sConfig(sampleLong, longSchema, doubleSchema, Input), valid(sampleLong)), //FIXME: Long with Double Schema => Long ?
      (sConfig(sampleLong, longSchema, doubleSchema, sampleLong), valid(sampleLong)), //FIXME: Long with Double Schema => Long ?

      (sConfig(null, nullSchema, doubleSchema, Input), invalidTypes("path 'Data' actual: 'Null' expected: 'Double'")),
      (sConfig(sampleBoolean, booleanSchema, doubleSchema, Input), invalidTypes("path 'Data' actual: 'Boolean' expected: 'Double'")),
      (sConfig(sampleString, stringSchema, doubleSchema, Input), invalidTypes("path 'Data' actual: 'String' expected: 'Double'")),
      (sConfig(sampleBytes, bytesSchema, doubleSchema, Input), invalidTypes("path 'Data' actual: 'ByteBuffer' expected: 'Double'")),

      (sConfig(sampleDouble, doubleSchema, null), invalidTypes("path 'Data' actual: 'Null' expected: 'Double'")),
      (sConfig(sampleDouble, doubleSchema, sampleString), invalidTypes(s"path 'Data' actual: '${typedStr.display}' expected: 'Double'")),
      (sConfig(sampleDouble, doubleSchema, sampleBoolean), invalidTypes(s"path 'Data' actual: '${typedBool.display}' expected: 'Double'")),

      //Record with simple union field validations
      (rConfig(sampleBoolean, recordBooleanSchema, recordUnionOfStringIntegerSchema, sampleInteger, None), rValid(sampleInteger, recordUnionOfStringIntegerSchema)),
      (rConfig(sampleBoolean, recordBooleanSchema, recordUnionOfStringIntegerSchema, sampleString, None), rValid(sampleString, recordUnionOfStringIntegerSchema)),
      (ScenarioConfig(sampleString, stringSchema, recordUnionOfStringIntegerSchema, Input.toSpELLiteral, None), invalidTypes("path 'Data' actual: 'String' expected: '{field: String | Integer}'")),
      (rConfig(sampleBoolean, recordMaybeBooleanSchema, recordUnionOfStringIntegerSchema, Input), invalidTypes("path 'field' actual: 'Boolean' expected: 'String | Integer'")),

      //Array validations
      (rConfig(List("12"), recordArrayOfStringsSchema, recordArrayOfNumbersSchema, Input), invalidTypes("path 'field[]' actual: 'String' expected: 'Integer | Double'")),
      (rConfig(sampleInteger, recordIntegerSchema, recordArrayOfNumbersSchema, List(1.0, 2.5)), rValid(List(1, 2), recordArrayOfNumbersSchema)), //bug with serialization / deserialization union?? There should be List(1.0, 2.5) - casting to first schema: there first is int
      (rConfig(sampleInteger, recordIntegerSchema, recordArrayOfNumbersSchema,  List(sampleString)), invalidTypes(s"path 'field[]' actual: '${typedStr.display}' expected: 'Integer | Double'")),
      //FIXME: List[Unknown] (rConfig(sampleInteger, recordIntegerSchema, recordWithArrayOfNumbers, s"""{$sampleBoolean, "$sampleString"}"""), invalidTypes(s"path 'field[]' actual: '${typeBool.display} | ${typeStr.display}' expected: 'Integer | Double'")),
      (rConfig(sampleInteger, recordIntegerSchema, recordArrayOfNumbersSchema, sampleInteger), invalidTypes(s"path 'field' actual: '${typedInt.display}' expected: 'List[Integer | Double]'")),
      (rConfig(sampleInteger, recordIntegerSchema, recordArrayOfNumbersSchema, null), invalidTypes("path 'field' actual: 'Null' expected: 'List[Integer | Double]'")),

      (rConfig(List("12"), recordArrayOfStringsSchema, recordMaybeArrayOfNumbersSchema, Input), invalidTypes("path 'field[]' actual: 'String' expected: 'Integer | Double'")),
      (rConfig(sampleInteger, recordIntegerSchema, recordMaybeArrayOfNumbersSchema, List(1.0, 2.5)), rValid(List(1, 2), recordMaybeArrayOfNumbersSchema)), //bug with serialization / deserialization union?? There should be List(1.0, 2.5) - casting to first schema: there first is int
      (rConfig(sampleInteger, recordIntegerSchema, recordMaybeArrayOfNumbersSchema, List(sampleString)), invalidTypes(s"path 'field[]' actual: '${typedStr.display}' expected: 'Integer | Double'")),
      //FIXME: List[Unknown] (rConfig(sampleInteger, recordIntegerSchema, recordWithMaybeArrayOfNumbers, s"""{$sampleBoolean, "$sampleString"}"""), invalidTypes("path 'field[]' actual: '${typeBool.display} | ${typeStr.display}' expected: 'Integer | Double'")),
      (rConfig(sampleInteger, recordIntegerSchema, recordMaybeArrayOfNumbersSchema, sampleInteger), invalidTypes(s"path 'field' actual: '${typedInt.display}' expected: 'Null | List[Integer | Double]'")),
      (rConfig(sampleInteger, recordIntegerSchema, recordMaybeArrayOfNumbersSchema, null), rValid(null, recordMaybeArrayOfNumbersSchema)),

      (rConfig(List("12"), recordArrayOfStringsSchema, recordOptionalArrayOfNumbersSchema, Input), invalidTypes("path 'field[]' actual: 'String' expected: 'Integer | Double'")),
      (rConfig(sampleInteger, recordIntegerSchema, recordOptionalArrayOfNumbersSchema, List(1.0, 2.5)), rValid(List(1, 2), recordOptionalArrayOfNumbersSchema)), //bug with serialization / deserialization union?? There should be List(1.0, 2.5) - casting to first schema: there first is int
      //FIXME: List[Unknown]  (rConfig(sampleInteger, recordIntegerSchema, recordWithMaybeArrayOfNumbers, s"""{$sampleBoolean, "$sampleString"}"""), invalidTypes("path 'field[]' actual: '${typeBool.display} | ${typeStr.display}' expected: 'Integer | Double'")),
      (rConfig(sampleInteger, recordIntegerSchema, recordMaybeArrayOfNumbersSchema, List(sampleString)), invalidTypes(s"path 'field[]' actual: '${typedStr.display}' expected: 'Integer | Double'")),
      (rConfig(sampleInteger, recordIntegerSchema, recordOptionalArrayOfNumbersSchema, sampleInteger), invalidTypes(s"path 'field' actual: '${typedInt.display}' expected: 'Null | List[Integer | Double]'")),
      (rConfig(sampleInteger, recordIntegerSchema, recordOptionalArrayOfNumbersSchema, null), rValid(null, recordOptionalArrayOfNumbersSchema)),
      (rConfig(sampleInteger, recordIntegerSchema, recordOptionalArrayOfNumbersSchema, EmptyBaseObject), invalid(Nil, List("field"), Nil)),
      (rConfig(sampleInteger, recordIntegerSchema, recordOptionalArrayOfNumbersSchema, EmptyBaseObject, Some(lax)), rValid(null, recordOptionalArrayOfNumbersSchema)),

      (rConfig(List(List("12")), recordOptionalArrayOfArraysStringsSchema, recordOptionalArrayOfArraysNumbersSchema, Input), invalidTypes("path 'field[][]' actual: 'String' expected: 'Integer | Double'")),
      (rConfig(sampleInteger, recordIntegerSchema, recordOptionalArrayOfArraysNumbersSchema, List(List(1.0, 2.5))), rValid(List(List(1, 2)), recordOptionalArrayOfArraysNumbersSchema)), //bug with serialization / deserialization union?? There should be List(1.0, 2.5) - casting to first schema: there first is int
      //FIXME: List[Unknown]  (rConfig(sampleInteger, recordIntegerSchema, recordOptionalArrayOfArraysNumbers, s"""{{$sampleBoolean, "$sampleString"}}"""), invalidTypes("path 'field[][]' actual: 'Boolean | String' expected: 'Integer | Double'")),
      (rConfig(sampleInteger, recordIntegerSchema, recordOptionalArrayOfArraysNumbersSchema, List(List(sampleString))), invalidTypes(s"path 'field[][]' actual: '${typedStr.display}' expected: 'Integer | Double'")),
      (rConfig(sampleInteger, recordIntegerSchema, recordOptionalArrayOfArraysNumbersSchema, List(sampleInteger)), invalidTypes(s"path 'field[]' actual: '${typedInt.display}' expected: 'Null | List[Integer | Double]'")),
      (rConfig(sampleInteger, recordIntegerSchema, recordOptionalArrayOfArraysNumbersSchema, sampleInteger), invalidTypes(s"path 'field' actual: '${typedInt.display}' expected: 'Null | List[Null | List[Integer | Double]]'")),

      (rConfig(sampleInteger, recordIntegerSchema, recordOptionalArrayOfRecordsSchema, List(Map("price1" -> "15.5"))), invalid(Nil, List("field[].price"), List("field[].price1"))),
      (rConfig(sampleInteger, recordIntegerSchema, recordOptionalArrayOfRecordsSchema, List(Map("price1" -> "15.5")), Some(lax)), invalid(Nil, List("field[].price"), Nil)),
      (rConfig(sampleInteger, recordIntegerSchema, recordOptionalArrayOfRecordsSchema, List(Map("price" -> sampleString))), invalidTypes(s"path 'field[].price' actual: '${typedStr.display}' expected: 'Null | Double'")),
      (rConfig(sampleInteger, recordIntegerSchema, recordOptionalArrayOfRecordsSchema, sampleInteger), invalidTypes(s"""path 'field' actual: '${typedInt.display}' expected: 'Null | List[Null | {price: Null | Double}]'""")),
      (rConfig(sampleInteger, recordIntegerSchema, recordOptionalArrayOfRecordsSchema, List(sampleInteger)), invalidTypes(s"""path 'field[]' actual: '${typedInt.display}' expected: 'Null | {price: Null | Double}'""")),

      //Map validations
      (rConfig(Map("tax" -> "7"), recordMapOfStringsSchema, recordMapOfIntsSchema, Input), invalidTypes("path 'field[*]' actual: 'String' expected: 'Null | Integer'")),
      (rConfig(Map("price" -> sampleDouble.toString), fakeMapRecordWithStringPrice, recordMapOfStringsSchema, Input), invalidTypes("path 'field' actual: '{price: String}' expected: 'Map[String, Null | String]'")),
      (rConfig(sampleInteger, recordIntegerSchema, recordMapOfIntsSchema, Map("tax" -> 7, "vat" -> "23")), invalidTypes("path 'field.vat' actual: 'String{23}' expected: 'Null | Integer'")),
      (rConfig(sampleInteger, recordIntegerSchema, recordMapOfIntsSchema, sampleInteger), invalidTypes(s"path 'field' actual: '${typedInt.display}' expected: 'Map[String, Null | Integer]'")),
      (rConfig(sampleInteger, recordIntegerSchema, recordMapOfIntsSchema, Nil), invalidTypes("path 'field' actual: 'List[Unknown]' expected: 'Map[String, Null | Integer]'")),
      (rConfig(sampleInteger, recordIntegerSchema, recordMapOfIntsSchema, null), invalidTypes("path 'field' actual: 'Null' expected: 'Map[String, Null | Integer]'")),

      (rConfig(Map("tax" -> "7"), recordMapOfStringsSchema, recordMaybeMapOfIntsSchema, Input), invalidTypes("path 'field[*]' actual: 'String' expected: 'Null | Integer'")),
      (rConfig(sampleInteger, recordIntegerSchema, recordMaybeMapOfIntsSchema, Map("tax" -> 7, "vat" -> "23")), invalidTypes("path 'field.vat' actual: 'String{23}' expected: 'Null | Integer'")),
      (rConfig(sampleInteger, recordIntegerSchema, recordMaybeMapOfIntsSchema, sampleInteger), invalidTypes(s"path 'field' actual: '${typedInt.display}' expected: 'Null | Map[String, Null | Integer]'")),
      (rConfig(sampleInteger, recordIntegerSchema, recordMaybeMapOfIntsSchema, Nil), invalidTypes("path 'field' actual: 'List[Unknown]' expected: 'Null | Map[String, Null | Integer]'")),

      (rConfig(Map("first" -> Map("tax" -> "7")), recordMapOfMapsStringsSchema, recordOptionalMapOfMapsIntsSchema, Input), invalidTypes("path 'field[*][*]' actual: 'String' expected: 'Null | Integer'")),
      (rConfig(sampleInteger, recordIntegerSchema, recordOptionalMapOfMapsIntsSchema, Map("first" -> Map("tax" -> 7, "vat" -> "23"))), invalidTypes("path 'field.first.vat' actual: 'String{23}' expected: 'Null | Integer'")),
      (rConfig(sampleInteger, recordIntegerSchema, recordOptionalMapOfMapsIntsSchema, sampleInteger), invalidTypes(s"path 'field' actual: '${typedInt.display}' expected: 'Null | Map[String, Null | Map[String, Null | Integer]]'")),
      (rConfig(sampleInteger, recordIntegerSchema, recordOptionalMapOfMapsIntsSchema, Map("first" -> sampleInteger)), invalidTypes(s"path 'field.first' actual: '${typedInt.display}' expected: 'Null | Map[String, Null | Integer]'")),
      (rConfig(sampleInteger, recordIntegerSchema, recordOptionalMapOfMapsIntsSchema, Nil), invalidTypes("path 'field' actual: 'List[Unknown]' expected: 'Null | Map[String, Null | Map[String, Null | Integer]]'")),
      (rConfig(sampleInteger, recordIntegerSchema, recordOptionalMapOfMapsIntsSchema, null), rValid(null, recordOptionalMapOfMapsIntsSchema)),
      (rConfig(sampleInteger, recordIntegerSchema, recordOptionalMapOfMapsIntsSchema, EmptyBaseObject), invalid(Nil, List("field"), Nil)),
      (rConfig(sampleInteger, recordIntegerSchema, recordOptionalMapOfMapsIntsSchema, EmptyBaseObject, Some(lax)), rValid(null, recordOptionalMapOfMapsIntsSchema)),

      (rConfig(Map("first" -> Map("price" -> "15.5")), recordOptionalMapOfStringRecordsSchema, recordOptionalMapOfRecordsSchema, Input), invalidTypes("path 'field[*].price' actual: 'String' expected: 'Null | Double'")),
      (rConfig(sampleInteger, recordIntegerSchema, recordOptionalMapOfRecordsSchema, Map("first" -> Map("price" -> sampleString))), invalidTypes(s"path 'field.first.price' actual: '${typedStr.display}' expected: 'Null | Double'")),
      (rConfig(sampleInteger, recordIntegerSchema, recordOptionalMapOfRecordsSchema, sampleInteger), invalidTypes(s"path 'field' actual: '${typedInt.display}' expected: 'Null | Map[String, Null | {price: Null | Double}]'")),
      (rConfig(sampleInteger, recordIntegerSchema, recordOptionalMapOfRecordsSchema, Map("first" -> sampleInteger)), invalidTypes(s"path 'field.first' actual: '${typedInt.display}' expected: 'Null | {price: Null | Double}'")),
      (rConfig(sampleInteger, recordIntegerSchema, recordOptionalMapOfRecordsSchema, Nil), invalidTypes("path 'field' actual: 'List[Unknown]' expected: 'Null | Map[String, Null | {price: Null | Double}]'")),
      (rConfig(sampleInteger, recordIntegerSchema, recordOptionalMapOfRecordsSchema, null), rValid(null, recordOptionalMapOfRecordsSchema)),
      (rConfig(sampleInteger, recordIntegerSchema, recordOptionalMapOfRecordsSchema, EmptyBaseObject), invalid(Nil, List("field"), Nil)),
      (rConfig(sampleInteger, recordIntegerSchema, recordOptionalMapOfRecordsSchema, EmptyBaseObject, Some(lax)), rValid(null, recordOptionalMapOfRecordsSchema)),

      //Record validations
      (rConfig(Map("sub" -> Map("price" -> "15.5")), nestedRecordWithStringPriceSchema, nestedRecordSchema, Input), invalidTypes("path 'field.sub.price' actual: 'String' expected: 'Null | Double'")),
      (rConfig(sampleInteger, recordIntegerSchema, nestedRecordSchema, Map("sub" -> Map("price2" -> sampleDouble))), invalid(Nil, List("field.sub.price"), List("field.sub.price2"))),
      (rConfig(sampleInteger, recordIntegerSchema, nestedRecordSchema, Map("sub" -> Map("price2" -> sampleDouble)), Some(lax)), invalid(Nil, List("field.sub.price"), Nil)),
      (rConfig(sampleInteger, recordIntegerSchema, nestedRecordSchema, Map("sub" -> Map("price" -> sampleString))), invalidTypes(s"path 'field.sub.price' actual: '${typedStr.display}' expected: 'Null | Double'")),
      (rConfig(sampleInteger, recordIntegerSchema, nestedRecordSchema, Input), invalidTypes("path 'field' actual: 'Integer' expected: 'Null | {sub: Null | {price: Null | Double}}'")),
      (rConfig(sampleNestedRecord, nestedRecordSchema, nestedRecordSchema, sampleInteger), invalidTypes(s"path 'field' actual: '${typedInt.display}' expected: 'Null | {sub: Null | {price: Null | Double}}'")),
      (rConfig(sampleInteger, recordIntegerSchema, nestedRecordSchema, Map("sub" -> sampleInteger)), invalidTypes(s"path 'field.sub' actual: '${typedInt.display}' expected: 'Null | {price: Null | Double}'")),
      (rConfig(sampleInteger, recordIntegerSchema, nestedRecordSchema, Nil), invalidTypes("path 'field' actual: 'List[Unknown]' expected: 'Null | {sub: Null | {price: Null | Double}}'")),
      (rConfig(sampleNestedRecordV2, nestedRecordSchemaV2, nestedRecordSchema, Input), invalid(Nil, Nil, List("field.sub.currency", "field.str"))),
      (rConfig(sampleNestedRecordV2, nestedRecordSchemaV2, nestedRecordSchema, Input, Some(lax)), valid(sampleNestedRecord)),
      (rConfig(sampleNestedRecord, nestedRecordSchema, nestedRecordSchemaV2, Input), invalid(Nil, List("field.str", "field.sub.currency"), Nil)),
      (rConfig(sampleString, recordStringSchema, recordWithBigUnionSchema, Input), invalidTypes("path 'field' actual: 'String' expected: 'Null | Boolean | {sub: Null | {price: Null | String}} | {sub: Null | {price: Null | Double}}'")),

      //Enum validations
      (rConfig(sampleInteger, recordIntegerSchema, recordEnumSchema, sampleEnumString), rValid(sampleEnum, recordEnumSchema)),
      (rConfig(sampleInteger, recordIntegerSchema, recordEnumSchema, sampleStrEnumV2), invalidTypes(s"path 'field' actual: '${typedStrEnumV2.display}' expected: 'EnumSymbol[SPADES | HEARTS | DIAMONDS | CLUBS] | String'")),
      (rConfig(sampleInteger, recordIntegerSchema, recordEnumSchema, Input), invalidTypes("path 'field' actual: 'Integer' expected: 'EnumSymbol[SPADES | HEARTS | DIAMONDS | CLUBS] | String'")),
      (rConfig(sampleInteger, recordIntegerSchema, recordEnumSchema, sampleInteger), invalidTypes(s"path 'field' actual: '${typedInt.display}' expected: 'EnumSymbol[SPADES | HEARTS | DIAMONDS | CLUBS] | String'")),

      //Fixed validations
      (rConfig(sampleFixed, recordFixedSchema, recordFixedSchema, Input), rValid(sampleFixed, recordFixedSchema)),
      (rConfig(sampleInteger, recordIntegerSchema, recordFixedSchema, sampleStrFixed), rValid(sampleFixed, recordFixedSchema)),
      (rConfig(sampleInteger, recordIntegerSchema, recordFixedSchema, sampleStrFixedV), invalidTypes(s"path 'field' actual: '${typeStrFixedV2.display}' expected: 'Fixed[32] | ByteBuffer | String'")),
      (rConfig(sampleInteger, recordIntegerSchema, recordFixedSchema, Input), invalidTypes("path 'field' actual: 'Integer' expected: 'Fixed[32] | ByteBuffer | String'")),
      (rConfig(sampleInteger, recordIntegerSchema, recordFixedSchema, sampleInteger), invalidTypes(s"path 'field' actual: '${typedInt.display}' expected: 'Fixed[32] | ByteBuffer | String'")),

      //Logical: UUID validations
      (rConfig(sampleUUID, recordUUIDSchema, recordUUIDSchema, Input), rValid(sampleUUID, recordUUIDSchema)),
      (rConfig(sampleUUID.toString, recordStringSchema, recordUUIDSchema, Input), rValid(sampleUUID, recordUUIDSchema)),
      (rConfig(sampleInteger, recordIntegerSchema, recordUUIDSchema, uuid(sampleUUID.toString)), rValid(sampleUUID, recordUUIDSchema)),
      (rConfig(sampleInteger, recordIntegerSchema, recordUUIDSchema, sampleUUID.toString), rValid(sampleUUID, recordUUIDSchema)),
      (rConfig(sampleInteger, recordIntegerSchema, recordUUIDSchema, Input), invalidTypes("path 'field' actual: 'Integer' expected: 'UUID | String'")),
      (rConfig(sampleInteger, recordIntegerSchema, recordUUIDSchema, sampleInteger), invalidTypes(s"path 'field' actual: '${typedInt.display}' expected: 'UUID | String'")),
      (rConfig(sampleInteger, recordIntegerSchema, recordUUIDSchema, sampleString), invalidTypes(s"path 'field' actual: '${typedStr.display}' expected: 'UUID | String'")),

      //Logical: BigDecimal validations
      (rConfig(sampleDecimal, recordDecimalSchema, recordDecimalSchema, Input), rValid(sampleDecimal, recordDecimalSchema)),
      (rConfig(sampleString, recordStringSchema, recordDecimalSchema, bigDecimal(1, 2)), rValid(sampleDecimal, recordDecimalSchema)),
      (rConfig(sampleString, recordStringSchema, recordDecimalSchema, sampleDecimal), invalidTypes(s"path 'field' actual: 'Double{1.0}' expected: 'BigDecimal | ByteBuffer'")),
      (rConfig(sampleInteger, recordIntegerSchema, recordDecimalSchema, Input), invalidTypes("path 'field' actual: 'Integer' expected: 'BigDecimal | ByteBuffer'")),
      (rConfig(sampleInteger, recordIntegerSchema, recordDecimalSchema, sampleInteger), invalidTypes(s"path 'field' actual: '${typedInt.display}' expected: 'BigDecimal | ByteBuffer'")),

      //Logical: Date validations -> LocalDate
      (rConfig(sampleDate, recordDateSchema, recordDateSchema, Input), rValid(sampleDate, recordDateSchema)),
      (rConfig(sampleString, recordStringSchema, recordDateSchema, localDate(sampleDate)), rValid(sampleDate, recordDateSchema)),
      (rConfig(sampleDate.toEpochDay, recordIntegerSchema, recordDateSchema, Input), rValid(sampleDate, recordDateSchema)),
      (rConfig(sampleString, recordStringSchema, recordDateSchema, sampleDate.toEpochDay.toInt), rValid(sampleDate, recordDateSchema)),
      (rConfig(sampleString, recordStringSchema, recordDateSchema, Input), invalidTypes("path 'field' actual: 'String' expected: 'LocalDate | Integer'")),
      (rConfig(sampleDate, recordDateSchema, recordDateSchema, sampleString), invalidTypes(s"path 'field' actual: '${typedStr.display}' expected: 'LocalDate | Integer'")),

      //Logical: Time Millis -> LocalTime
      (rConfig(sampleMillisLocalTime, recordTimeMillisSchema, recordTimeMillisSchema, Input), rValid(sampleMillisLocalTime, recordTimeMillisSchema)),
      (rConfig(sampleString, recordStringSchema, recordTimeMillisSchema, localTime(sampleMillisLocalTime)), rValid(sampleMillisLocalTime, recordTimeMillisSchema)),
      (rConfig(sampleMillisLocalTime.toMillis, recordIntegerSchema, recordTimeMillisSchema, Input), rValid(sampleMillisLocalTime, recordTimeMillisSchema)),
      (rConfig(sampleString, recordStringSchema, recordTimeMillisSchema, sampleMillisLocalTime.toMillis), rValid(sampleMillisLocalTime, recordTimeMillisSchema)),
      (rConfig(sampleString, recordStringSchema, recordTimeMillisSchema, Input), invalidTypes("path 'field' actual: 'String' expected: 'LocalTime | Integer'")),
      (rConfig(sampleString, recordStringSchema, recordTimeMillisSchema, sampleString), invalidTypes(s"path 'field' actual: '${typedStr.display}' expected: 'LocalTime | Integer'")),

      //Logical: Time Micros -> LocalTime
      (rConfig(sampleMicrosLocalTime, recordTimeMicrosSchema, recordTimeMicrosSchema, Input), rValid(sampleMicrosLocalTime, recordTimeMicrosSchema)),
      (rConfig(sampleString, recordStringSchema, recordTimeMicrosSchema, localTime(sampleMicrosLocalTime)), rValid(sampleMicrosLocalTime, recordTimeMicrosSchema)),
      (rConfig(sampleMicrosLocalTime.toMicros, recordLongSchema, recordTimeMicrosSchema, Input), rValid(sampleMicrosLocalTime, recordTimeMicrosSchema)),
      (rConfig(sampleString, recordStringSchema, recordTimeMicrosSchema, sampleMicrosLocalTime.toMicros), rValid(sampleMicrosLocalTime, recordTimeMicrosSchema)),
      (rConfig(sampleInteger, recordIntegerSchema, recordTimeMicrosSchema, Input), invalidTypes("path 'field' actual: 'Integer' expected: 'LocalTime | Long'")),
      (rConfig(sampleInteger, recordIntegerSchema, recordTimeMicrosSchema, sampleInteger), invalidTypes(s"path 'field' actual: '${typedInt.display}' expected: 'LocalTime | Long'")),

      //Logical: Timestamp Millis -> Instant
      (rConfig(sampleMillisInstant, recordTimestampMillisSchema, recordTimestampMillisSchema, Input), rValid(sampleMillisInstant, recordTimestampMillisSchema)),
      (rConfig(sampleString, recordStringSchema, recordTimestampMillisSchema, instant(sampleMillisInstant)), rValid(sampleMillisInstant, recordTimestampMillisSchema)),
      (rConfig(sampleMillisInstant.toEpochMilli, recordLongSchema, recordTimestampMillisSchema, Input), rValid(sampleMillisInstant, recordTimestampMillisSchema)),
      (rConfig(sampleString, recordStringSchema, recordTimestampMillisSchema, sampleMillisInstant.toEpochMilli), rValid(sampleMillisInstant, recordTimestampMillisSchema)),
      (rConfig(sampleInteger, recordIntegerSchema, recordTimestampMillisSchema, Input), invalidTypes("path 'field' actual: 'Integer' expected: 'Instant | Long'")),
      (rConfig(sampleInteger, recordIntegerSchema, recordTimestampMillisSchema, sampleInteger), invalidTypes(s"path 'field' actual: '${typedInt.display}' expected: 'Instant | Long'")),

      //Logical: Timestamp Micros -> Instant
      (rConfig(sampleMicrosInstant, recordTimestampMicrosSchema, recordTimestampMicrosSchema, Input), rValid(sampleMicrosInstant, recordTimestampMicrosSchema)),
      (rConfig(sampleString, recordStringSchema, recordTimestampMicrosSchema, instant(sampleMicrosInstant.toMicrosFromEpoch, sampleMicrosInstant.toNanoAdjustment)), rValid(sampleMicrosInstant, recordTimestampMicrosSchema)),
      (rConfig(sampleMicrosInstant.toMicros, recordLongSchema, recordTimestampMicrosSchema, Input), rValid(sampleMicrosInstant, recordTimestampMicrosSchema)),
      (rConfig(sampleString, recordStringSchema, recordTimestampMicrosSchema, sampleMicrosInstant.toMicros), rValid(sampleMicrosInstant, recordTimestampMicrosSchema)),
      (rConfig(sampleInteger, recordIntegerSchema, recordTimestampMicrosSchema, Input), invalidTypes("path 'field' actual: 'Integer' expected: 'Instant | Long'")),
      (rConfig(sampleInteger, recordIntegerSchema, recordTimestampMicrosSchema, sampleInteger), invalidTypes(s"path 'field' actual: '${typedInt.display}' expected: 'Instant | Long'")),
    )

    forAll(testData) { (config: ScenarioConfig, expected: Validated[_, RunResult[_]]) =>
      val results = runWithValueResults(config)
      results shouldBe expected
    }

  }

  test("should catch runtime errors") {
    val testData = Table(
      "config",
      //Comparing String -> Enum returns true, but in runtime BestEffortAvroEncoder tries to encode String (that doesn't meet the requirements) to Enum
      rConfig(sampleStrFixedV, recordStringSchema, recordEnumSchema, Input),

      //FIXME: Comparing EnumV2 -> Enum returns true, but in runtime BestEffortAvroEncoder tries to encode String (that doesn't meet the requirements) to Enum
      rConfig(sampleEnumV2, recordEnumSchemaV2, recordEnumSchema, Input),

      //Comparing String -> Fixed returns true, but in runtime BestEffortAvroEncoder tries to encode String (that doesn't meet the requirements) to Fixed
      rConfig(sampleString, recordStringSchema, recordFixedSchema, Input),

      //FIXME: Comparing FixedV2 -> Fixed returns true, but in runtime BestEffortAvroEncoder tries to encode value FixedV2 to Fixed
      rConfig(sampleFixedV2, recordFixedSchemaV2, recordFixedSchema, Input),

      //Situation when we put String -> UUID, where String isn't valid UUID type...
      rConfig(sampleString, recordStringSchema, recordUUIDSchema, Input),

      //FIXME: Schema evolution with Long as input and output as Int
      rConfig(sampleLong, recordLongSchema, recordIntegerSchema, Input), //Long -> Int?
    )

    forAll(testData) { config: ScenarioConfig =>
      val results = runWithValueResults(config)
      val message = results.validValue.errors.head.throwable.asInstanceOf[AvroRuntimeException].getMessage
      message shouldBe s"Not expected container: ${config.sourceSchema} for schema: ${config.sinkSchema}"
    }

  }

  //Error / bug on field schema evolution... SubV1 -> SubV2 ( currency with default value - optional field )
  test("should catch runtime errors on field schema evolution") {
    val config = rConfig(sampleNestedRecord, nestedRecordSchema, nestedRecordSchemaV2, Map("sub" -> SpecialSpELElement("#input.field.sub"), "str" -> sampleString), Some(lax))
    val results = runWithValueResults(config)

    val error = results.validValue.errors.head.throwable.asInstanceOf[SerializationException]
    error.getMessage shouldBe "Error serializing Avro message"

    error.getCause.getMessage shouldBe s"""Not in union ${nestedRecordV2FieldsSchema}: {"sub": {"price": $sampleDouble}, "str": "$sampleString"} (field=$RecordFieldName)"""
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
    val sinkDefinition = output match {
      case element: SpecialSpELElement if List(EmptyBaseObject, Input).contains(element) => element
      case any => Map(RecordFieldName -> any)
    }

    val input = inputData match {
      case record: GenericRecord => record
      case any => AvroUtils.createRecord(sourceSchema, Map(RecordFieldName -> any))
    }

    ScenarioConfig(input, sourceSchema, sinkSchema, sinkDefinition.toSpELLiteral, validationMode)
  }

  //StandardConfig -> simple avro type as a input
  private def sConfig(inputData: Any, schema: Schema, output: Any): ScenarioConfig =
    sConfig(inputData, schema, schema, output, None)

  private def sConfig(inputData: Any, sourceSchema: Schema, sinkSchema: Schema, output: Any, validationMode: Option[ValidationMode] = None): ScenarioConfig =
    ScenarioConfig(inputData, sourceSchema, sinkSchema, output.toSpELLiteral, validationMode)

}
