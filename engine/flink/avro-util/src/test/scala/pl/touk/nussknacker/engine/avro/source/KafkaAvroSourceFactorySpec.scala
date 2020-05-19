package pl.touk.nussknacker.engine.avro.source

import java.nio.charset.StandardCharsets

import org.apache.avro.Schema
import org.apache.avro.generic.GenericData
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.scala._
import org.scalatest.Assertion
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.process.{Source, TestDataGenerator, TestDataParserProvider}
import pl.touk.nussknacker.engine.api.typed.ReturningType
import pl.touk.nussknacker.engine.api.{MetaData, StreamMetaData}
import pl.touk.nussknacker.engine.avro.dto.{FullName, FullNameV1}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.{ConfluentSchemaRegistryClient, ConfluentSchemaRegistryClientFactory, MockConfluentSchemaRegistryClientBuilder}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.{ConfluentAvroKeyValueDeserializationSchemaFactory, ConfluentSchemaRegistryProvider}
import pl.touk.nussknacker.engine.avro.schemaregistry.{SchemaSubjectNotFound, SchemaVersionFound}
import pl.touk.nussknacker.engine.avro.typed.AvroSchemaTypeDefinitionExtractor
import pl.touk.nussknacker.engine.avro.{AvroUtils, KafkaAvroSpec, TestMockSchemaRegistry}

class KafkaAvroSourceFactorySpec extends KafkaAvroSpec(MockSchemaRegistry.topics) {

  import MockSchemaRegistry._

  override def schemaRegistryClient: ConfluentSchemaRegistryClient =
    confluentSchemaRegistryMockClient

  test("should read generated record in v1") {
    val givenObj = {
      val r = new GenericData.Record(recordSchemaV1)
      r.put("first", "Jan")
      r.put("last", "Kowalski")
      r
    }

    roundTripSingleObject(createAvroSourceFactory(useSpecificAvroReader = false), givenObj, 1, recordSchemaV1, recordTopic)
  }

  test("should read generated record in v2") {
    val givenObj = {
      val r = new GenericData.Record(recordSchemaV2)
      r.put("first", "Jan")
      r.put("middle", "Maria")
      r.put("last", "Kowalski")
      r
    }

    roundTripSingleObject(createAvroSourceFactory(useSpecificAvroReader = false), givenObj, 2, recordSchemaV2, recordTopic)
  }

  test("should read generated record in last version") {
    val givenObj = {
      val r = new GenericData.Record(recordSchemaV2)
      r.put("first", "Jan")
      r.put("middle", "Maria")
      r.put("last", "Kowalski")
      r
    }

    roundTripSingleObject(createAvroSourceFactory(useSpecificAvroReader = false), givenObj, null, recordSchemaV2, recordTopic)
  }

  test("should throw exception when schema doesn't exist") {
    val givenObj = {
      val r = new GenericData.Record(recordSchemaV2)
      r.put("first", "Jan")
      r.put("middle", "Maria")
      r.put("last", "Kowalski")
      r
    }

    assertThrowsWithParent[SchemaSubjectNotFound] {
      readLastMessageAndVerify(createAvroSourceFactory(useSpecificAvroReader = false), givenObj, 1, recordSchemaV2, "fake-topic")
    }
  }

  test("should throw exception when schema version doesn't exist") {
    val givenObj = {
      val r = new GenericData.Record(recordSchemaV2)
      r.put("first", "Jan")
      r.put("middle", "Maria")
      r.put("last", "Kowalski")
      r
    }

    assertThrowsWithParent[SchemaVersionFound]{
      readLastMessageAndVerify(createAvroSourceFactory(useSpecificAvroReader = false), givenObj, 3, recordSchemaV2, recordTopic)
    }
  }

  test("should read last generated simple object") {
    val givenObj = 123123

    roundTripSingleObject(createAvroSourceFactory(useSpecificAvroReader = false), givenObj, 1, intSchema, intTopic)
  }

  test("should read last generated record as a specific class") {
    val givenObj = FullName("Jan", "Maria", "Nowak")

    roundTripSingleObject(createAvroSourceFactory(useSpecificAvroReader = true), givenObj, 2, recordSchemaV2, recordTopic)
  }

  test("should read last generated key-value object") {
    val givenObj = (123, 345)

    val serializedKey = keySerializer.serialize(intTopic, givenObj._1)
    val serializedValue = valueSerializer.serialize(intTopic, givenObj._2)
    kafkaClient.sendRawMessage(intTopic, serializedKey, serializedValue, Some(0))

    readLastMessageAndVerify(createKeyValueAvroSourceFactory[Int, Int], givenObj, 1, intSchema, intTopic)
  }

  private def roundTripSingleObject(sourceFactory: KafkaAvroSourceFactory[_],
                                    givenObj: Any,
                                    schemaVersion: Integer,
                                    exceptedSchema: Schema,
                                    topic: String): Assertion = {
    val serializedObj = valueSerializer.serialize(topic, givenObj)
    kafkaClient.sendRawMessage(topic, Array.empty, serializedObj, Some(0))

    readLastMessageAndVerify(sourceFactory, givenObj, schemaVersion, exceptedSchema, topic)
  }

  private def readLastMessageAndVerify(sourceFactory: KafkaAvroSourceFactory[_],
                                       givenObj: Any,
                                       schemaVersion: Integer,
                                       exceptedSchema: Schema,
                                       topic: String): Assertion = {
    val source = sourceFactory
      .create(MetaData("", StreamMetaData()), topic, schemaVersion)(NodeId(""))
      .asInstanceOf[Source[AnyRef] with TestDataGenerator with TestDataParserProvider[AnyRef] with ReturningType]

    source.returnType shouldEqual AvroSchemaTypeDefinitionExtractor.typeDefinition(exceptedSchema)

    val bytes = source.generateTestData(1)
    info("test object: " + new String(bytes, StandardCharsets.UTF_8))
    val deserializedObj = source.testDataParser.parseTestData(bytes)

    deserializedObj shouldEqual List(givenObj)
  }

  private def createAvroSourceFactory(useSpecificAvroReader: Boolean): KafkaAvroSourceFactory[AnyRef] = {
    val schemaRegistryProvider = ConfluentSchemaRegistryProvider[AnyRef](
      mockConfluentSchemaRegistryClientFactory,
      processObjectDependencies,
      useSpecificAvroReader,
      formatKey = false
    )
    new KafkaAvroSourceFactory(schemaRegistryProvider, processObjectDependencies, None)
  }

  private def createKeyValueAvroSourceFactory[K: TypeInformation, V: TypeInformation]: KafkaAvroSourceFactory[(K, V)] = {
    val deserializerFactory = new TupleAvroKeyValueDeserializationSchemaFactory[K, V](mockConfluentSchemaRegistryClientFactory)
    val provider = ConfluentSchemaRegistryProvider(
      mockConfluentSchemaRegistryClientFactory,
      None,
      Some(deserializerFactory),
      kafkaConfig,
      useSpecificAvroReader = false,
      formatKey = true
    )
    new KafkaAvroSourceFactory(provider, processObjectDependencies, None)
  }
}

class TupleAvroKeyValueDeserializationSchemaFactory[Key, Value](schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory)(implicit keyTypInfo: TypeInformation[Key], valueTypInfo: TypeInformation[Value])
  extends ConfluentAvroKeyValueDeserializationSchemaFactory[(Key, Value)](schemaRegistryClientFactory, useSpecificAvroReader = false)(
    createTuple2TypeInformation(keyTypInfo, valueTypInfo)
  ) {

  override protected type K = Key
  override protected type V = Value

  override protected def createObject(key: Key, value: Value, topic: String): (Key, Value) = {
    (key, value)
  }
}

object MockSchemaRegistry extends TestMockSchemaRegistry {

  val recordTopic: String = "records"
  val intTopic: String = "ints"

  override def topics: List[(String, Int)] = List(
    (recordTopic, 2),
    (intTopic, 2)
  )

  val recordSchemaV1: Schema = FullNameV1.schema
  val recordSchemaV2: Schema = FullName.schema

  val intSchema: Schema = AvroUtils.parseSchema(
    """{
      |  "type": "int"
      |}
    """.stripMargin
  )

  val confluentSchemaRegistryMockClient: ConfluentSchemaRegistryClient = new MockConfluentSchemaRegistryClientBuilder()
    .register(recordTopic, recordSchemaV1, 1, isKey = false)
    .register(recordTopic, recordSchemaV2, 2, isKey = false)
    .register(intTopic, intSchema, 1, isKey = false)
    .register(intTopic, intSchema, 1, isKey = true)
    .build

  val mockConfluentSchemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory =
    createSchemaRegistryClientFactory(confluentSchemaRegistryMockClient)
}
