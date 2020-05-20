package pl.touk.nussknacker.engine.avro.source

import java.nio.charset.StandardCharsets

import org.apache.avro.Schema
import org.apache.avro.generic.GenericData
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.scala._
import org.scalatest.Assertion
import pl.touk.nussknacker.engine.api.process.{Source, TestDataGenerator, TestDataParserProvider}
import pl.touk.nussknacker.engine.api.typed.ReturningType
import pl.touk.nussknacker.engine.avro.dto.{FullNameV1, FullNameV2}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.{ConfluentSchemaRegistryClient, ConfluentSchemaRegistryClientFactory, MockConfluentSchemaRegistryClientBuilder}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.{ConfluentAvroKeyValueDeserializationSchemaFactory, ConfluentSchemaRegistryProvider}
import pl.touk.nussknacker.engine.avro.schemaregistry.{SchemaSubjectNotFound, SchemaVersionNotFound}
import pl.touk.nussknacker.engine.avro.typed.AvroSchemaTypeDefinitionExtractor
import pl.touk.nussknacker.engine.avro.{AvroUtils, KafkaAvroSpec, TestMockSchemaRegistry}

class KafkaAvroSourceFactorySpec extends KafkaAvroSpec {

  import MockSchemaRegistry._

  override protected def schemaRegistryClient: ConfluentSchemaRegistryClient =
    confluentSchemaRegistryMockClient

  override protected def topics: List[(String, Int)] = List(
    (recordTopic, 2),
    (intTopic, 2)
  )

  test("should read generated record in v1") {
    val sourceFactory = createAvroSourceFactory(useSpecificAvroReader = false)
    val givenObj = createRecordV1("Jan", "Kowalski")

    roundTripSingleObject(sourceFactory, givenObj, 1, FullNameV1.schema, recordTopic)
  }

  test("should read generated record in v2") {
    val sourceFactory = createAvroSourceFactory(useSpecificAvroReader = false)
    val givenObj = createRecordV2("Jan", "Maria", "Kowalski")

    roundTripSingleObject(sourceFactory, givenObj, 2, FullNameV2.schema, recordTopic)
  }

  test("should read generated record in last version") {
    val sourceFactory = createAvroSourceFactory(useSpecificAvroReader = false)
    val givenObj = createRecordV2("Jan", "Maria", "Kowalski")

    roundTripSingleObject(sourceFactory, givenObj, null, FullNameV2.schema, recordTopic)
  }

  test("should throw exception when schema doesn't exist") {
    val sourceFactory = createAvroSourceFactory(useSpecificAvroReader = false)
    val givenObj = createRecordV2("Jan", "Maria", "Kowalski")

    assertThrowsWithParent[SchemaSubjectNotFound] {
      readLastMessageAndVerify(sourceFactory, givenObj, 1, FullNameV2.schema, "fake-topic")
    }
  }

  test("should throw exception when schema version doesn't exist") {
    val sourceFactory = createAvroSourceFactory(useSpecificAvroReader = false)
    val givenObj = createRecordV2("Jan", "Maria", "Kowalski")

    assertThrowsWithParent[SchemaVersionNotFound]{
      readLastMessageAndVerify(sourceFactory, givenObj, 3, FullNameV2.schema, recordTopic)
    }
  }

  test("should read last generated simple object") {
    val sourceFactory = createAvroSourceFactory(useSpecificAvroReader = false)
    val givenObj = 123123

    roundTripSingleObject(sourceFactory, givenObj, 1, intSchema, intTopic)
  }

  test("should read last generated record as a specific class") {
    val sourceFactory = createAvroSourceFactory(useSpecificAvroReader = true)
    val givenObj = FullNameV2("Jan", "Maria", "Nowak")

    roundTripSingleObject(sourceFactory, givenObj, 2, FullNameV2.schema, recordTopic)
  }

  test("should read last generated key-value object") {
    val givenObj = (123, 345)

    val serializedKey = keySerializer.serialize(intTopic, givenObj._1)
    val serializedValue = valueSerializer.serialize(intTopic, givenObj._2)
    kafkaClient.sendRawMessage(intTopic, serializedKey, serializedValue, Some(0))

    readLastMessageAndVerify(createKeyValueAvroSourceFactory[Int, Int], givenObj, 1, intSchema, intTopic)
  }

  private def createRecordV1(first: String, last: String): GenericData.Record =
    AvroUtils.createRecord(FullNameV1.schema, Map("first" -> first, "last" -> last))

  private def createRecordV2(first: String, last: String, middle: String): GenericData.Record =
    AvroUtils.createRecord(FullNameV2.schema, Map("first" -> first, "last" -> last, "middle" -> middle))

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
      .create(metaData, topic, schemaVersion)(nodeId)
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

  val intSchema: Schema = AvroUtils.parseSchema(
    """{
      |  "type": "int"
      |}
    """.stripMargin
  )

  val confluentSchemaRegistryMockClient: ConfluentSchemaRegistryClient = new MockConfluentSchemaRegistryClientBuilder()
    .register(recordTopic, FullNameV1.schema, 1, isKey = false)
    .register(recordTopic, FullNameV2.schema, 2, isKey = false)
    .register(intTopic, intSchema, 1, isKey = false)
    .register(intTopic, intSchema, 1, isKey = true)
    .build

  val mockConfluentSchemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory =
    createSchemaRegistryClientFactory(confluentSchemaRegistryMockClient)
}
