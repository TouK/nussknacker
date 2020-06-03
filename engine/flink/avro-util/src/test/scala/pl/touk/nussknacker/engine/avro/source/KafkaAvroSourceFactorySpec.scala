package pl.touk.nussknacker.engine.avro.source

import java.nio.charset.StandardCharsets

import io.confluent.kafka.schemaregistry.client.{SchemaRegistryClient => CSchemaRegistryClient}
import org.apache.avro.Schema
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.scala._
import org.scalatest.Assertion
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.process.{Source, TestDataGenerator, TestDataParserProvider}
import pl.touk.nussknacker.engine.api.typed.ReturningType
import pl.touk.nussknacker.engine.api.{MetaData, StreamMetaData}
import pl.touk.nussknacker.engine.avro.schema.{FullNameV1, FullNameV2}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentSchemaRegistryProvider
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client._
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization.{ConfluentKeyValueKafkaAvroDeserializationFactory, SchemaDeterminingStrategy}
import pl.touk.nussknacker.engine.avro.schemaregistry.{SchemaSubjectNotFound, SchemaVersionFound}
import pl.touk.nussknacker.engine.avro.typed.AvroSchemaTypeDefinitionExtractor
import pl.touk.nussknacker.engine.avro.{AvroUtils, KafkaAvroSpec, TestSchemaRegistryClientFactory}

class KafkaAvroSourceFactorySpec extends KafkaAvroSpec {

  import MockSchemaRegistry._

  test("should read generated record in v1") {
    val sourceFactory = createAvroSourceFactory(useSpecificAvroReader = false)
    val givenObj = FullNameV1.createRecord("Jan", "Kowalski")

    roundTripSingleObject(sourceFactory, givenObj, 1, FullNameV1.schema, RecordTopic)
  }

  test("should read generated record in v2") {
    val sourceFactory = createAvroSourceFactory(useSpecificAvroReader = false)
    val givenObj = FullNameV2.createRecord("Jan", "Maria", "Kowalski")

    roundTripSingleObject(sourceFactory, givenObj, 2, FullNameV2.schema, RecordTopic)
  }

  test("should read generated record in last version") {
    val sourceFactory = createAvroSourceFactory(useSpecificAvroReader = false)
    val givenObj = FullNameV2.createRecord("Jan", "Maria", "Kowalski")

    roundTripSingleObject(sourceFactory, givenObj, null, FullNameV2.schema, RecordTopic)
  }

  test("should throw exception when schema doesn't exist") {
    val sourceFactory = createAvroSourceFactory(useSpecificAvroReader = false)
    val givenObj = FullNameV2.createRecord("Jan", "Maria", "Kowalski")

    assertThrowsWithParent[SchemaSubjectNotFound] {
      readLastMessageAndVerify(sourceFactory, givenObj, 1, FullNameV2.schema, "fake-topic")
    }
  }

  test("should throw exception when schema version doesn't exist") {
    val sourceFactory = createAvroSourceFactory(useSpecificAvroReader = false)
    val givenObj = FullNameV2.createRecord("Jan", "Maria", "Kowalski")

    assertThrowsWithParent[SchemaVersionFound]{
      readLastMessageAndVerify(sourceFactory, givenObj, 3, FullNameV2.schema, RecordTopic)
    }
  }

  test("should read last generated simple object") {
    val sourceFactory = createAvroSourceFactory(useSpecificAvroReader = false)
    val givenObj = 123123

    roundTripSingleObject(sourceFactory, givenObj, 1, IntSchema, IntTopic)
  }

  test("should read last generated record as a specific class") {
    val sourceFactory = createAvroSourceFactory(useSpecificAvroReader = true)
    val givenObj = FullNameV2("Jan", "Maria", "Nowak")

    roundTripSingleObject(sourceFactory, givenObj, 2, FullNameV2.schema, RecordTopic)
  }

  test("should read last generated key-value object") {
    val sourceFactory = createKeyValueAvroSourceFactory[Int, Int]
    val givenObj = (123, 345)

    val serializedKey = keySerializer.serialize(IntTopic, givenObj._1)
    val serializedValue = valueSerializer.serialize(IntTopic, givenObj._2)
    kafkaClient.sendRawMessage(IntTopic, serializedKey, serializedValue, Some(0))

    readLastMessageAndVerify(sourceFactory, givenObj, 1, IntSchema, IntTopic)
  }

  override protected def schemaRegistryClient: CSchemaRegistryClient = schemaRegistryMockClient

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
      factory,
      processObjectDependencies,
      useSpecificAvroReader,
      formatKey = false
    )
    new KafkaAvroSourceFactory(schemaRegistryProvider, processObjectDependencies, None)
  }

  private def createKeyValueAvroSourceFactory[K: TypeInformation, V: TypeInformation]: KafkaAvroSourceFactory[(K, V)] = {
    val deserializerFactory = new TupleAvroKeyValueKafkaAvroDeserializerSchemaFactory[K, V](factory)
    val provider = ConfluentSchemaRegistryProvider(
      factory,
      None,
      Some(deserializerFactory),
      kafkaConfig,
      useSpecificAvroReader = false,
      formatKey = true
    )
    new KafkaAvroSourceFactory(provider, processObjectDependencies, None)
  }
}

class TupleAvroKeyValueKafkaAvroDeserializerSchemaFactory[Key, Value](schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory)
                                                                     (implicit keyTypInfo: TypeInformation[Key], valueTypInfo: TypeInformation[Value])
  extends ConfluentKeyValueKafkaAvroDeserializationFactory[(Key, Value)](SchemaDeterminingStrategy.FromSubjectVersion, schemaRegistryClientFactory, useSpecificAvroReader = false)(
    createTuple2TypeInformation(keyTypInfo, valueTypInfo)
  ) {

  override protected type K = Key
  override protected type V = Value

  override protected def createObject(key: Key, value: Value, topic: String): (Key, Value) = {
    (key, value)
  }
}

object MockSchemaRegistry {

  val RecordTopic: String = "testAvroRecordTopic1"
  val IntTopic: String = "testAvroIntTopic1"

  val IntSchema: Schema = AvroUtils.parseSchema(
    """{
      |  "type": "int"
      |}
    """.stripMargin
  )

  val schemaRegistryMockClient: CSchemaRegistryClient = new MockConfluentSchemaRegistryClientBuilder()
    .register(RecordTopic, FullNameV1.schema, 1, isKey = false)
    .register(RecordTopic, FullNameV2.schema, 2, isKey = false)
    .register(IntTopic, IntSchema, 1, isKey = false)
    .register(IntTopic, IntSchema, 1, isKey = true)
    .build

  val factory: CachedConfluentSchemaRegistryClientFactory = TestSchemaRegistryClientFactory(schemaRegistryMockClient)
}
