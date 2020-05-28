package pl.touk.nussknacker.engine.avro

import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import io.confluent.kafka.serializers.KafkaAvroSerializer
import org.apache.avro.generic.GenericData
import org.apache.avro.specific.SpecificRecordBase
import org.apache.avro.{AvroRuntimeException, Schema}
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.scala._
import org.scalatest.{Assertion, BeforeAndAfterAll, FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.namespaces.DefaultObjectNaming
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, Source, TestDataGenerator, TestDataParserProvider}
import pl.touk.nussknacker.engine.api.typed.ReturningType
import pl.touk.nussknacker.engine.api.{MetaData, StreamMetaData}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentSchemaRegistryProvider
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.{CachedConfluentSchemaRegistryClientFactory, ConfluentSchemaRegistryClientFactory, MockConfluentSchemaRegistryClientBuilder, MockSchemaRegistryClient}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization.{ConfluentKafkaAvroDeserializationMode, ConfluentKeyValueKafkaAvroDeserializationFactory}
import pl.touk.nussknacker.engine.avro.schemaregistry.{SchemaSubjectNotFound, SchemaVersionFound}
import pl.touk.nussknacker.engine.avro.source.KafkaAvroSourceFactory
import pl.touk.nussknacker.engine.avro.typed.AvroSchemaTypeDefinitionExtractor
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, KafkaSpec}
import pl.touk.nussknacker.engine.util.cache.DefaultCache
import pl.touk.nussknacker.test.NussknackerAssertions

import scala.concurrent.duration.FiniteDuration

class KafkaAvroSourceFactorySpec extends FunSuite with BeforeAndAfterAll with KafkaSpec with Matchers with LazyLogging with NussknackerAssertions {

  import MockSchemaRegistry._

  import collection.JavaConverters._

  // schema.registry.url have to be defined even for MockSchemaRegistryClient
  override lazy val config: Config = ConfigFactory.load()
    .withValue("kafka.kafkaAddress", fromAnyRef(kafkaZookeeperServer.kafkaAddress))
    .withValue("kafka.kafkaProperties.\"schema.registry.url\"", fromAnyRef("not_used"))

  lazy val processObjectDependencies: ProcessObjectDependencies = ProcessObjectDependencies(config, DefaultObjectNaming)

  lazy val kafkaConfig: KafkaConfig = KafkaConfig.parseConfig(config, "kafka")

  private lazy val keySerializer: KafkaAvroSerializer = {
    val serializer = new KafkaAvroSerializer(schemaRegistryMockClient)
    serializer.configure(Map[String, AnyRef]("schema.registry.url" -> "not_used").asJava, true)
    serializer
  }

  private lazy val valueSerializer = new KafkaAvroSerializer(schemaRegistryMockClient)

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    kafkaClient.createTopic(RecordTopic, 2)
    kafkaClient.createTopic(IntTopic, 2)
  }

  test("should read generated record in v1") {
    val givenObj = {
      val r = new GenericData.Record(RecordSchemaV1)
      r.put("first", "Jan")
      r.put("last", "Kowalski")
      r
    }

    roundTripSingleObject(createAvroSourceFactory(useSpecificAvroReader = false), givenObj, 1, RecordSchemaV1, RecordTopic)
  }

  test("should read generated record in v2") {
    val givenObj = {
      val r = new GenericData.Record(RecordSchemaV2)
      r.put("first", "Jan")
      r.put("middle", "Maria")
      r.put("last", "Kowalski")
      r
    }

    roundTripSingleObject(createAvroSourceFactory(useSpecificAvroReader = false), givenObj, 2, RecordSchemaV2, RecordTopic)
  }

  test("should read generated record in last version") {
    val givenObj = {
      val r = new GenericData.Record(RecordSchemaV2)
      r.put("first", "Jan")
      r.put("middle", "Maria")
      r.put("last", "Kowalski")
      r
    }

    roundTripSingleObject(createAvroSourceFactory(useSpecificAvroReader = false), givenObj, null, RecordSchemaV2, RecordTopic)
  }

  test("should throw exception when schema doesn't exist") {
    val givenObj = {
      val r = new GenericData.Record(RecordSchemaV2)
      r.put("first", "Jan")
      r.put("middle", "Maria")
      r.put("last", "Kowalski")
      r
    }

    assertThrowsWithParent[SchemaSubjectNotFound] {
      readLastMessageAndVerify(createAvroSourceFactory(useSpecificAvroReader = false), givenObj, 1, RecordSchemaV2, "fake-topic")
    }
  }

  test("should throw exception when schema version doesn't exist") {
    val givenObj = {
      val r = new GenericData.Record(RecordSchemaV2)
      r.put("first", "Jan")
      r.put("middle", "Maria")
      r.put("last", "Kowalski")
      r
    }

    assertThrowsWithParent[SchemaVersionFound]{
      readLastMessageAndVerify(createAvroSourceFactory(useSpecificAvroReader = false), givenObj, 3, RecordSchemaV2, RecordTopic)
    }
  }

  test("should read last generated simple object") {
    val givenObj = 123123

    roundTripSingleObject(createAvroSourceFactory(useSpecificAvroReader = false), givenObj, 1, IntSchema, IntTopic)
  }

  test("should read last generated record as a specific class") {
    val givenObj = FullName("Jan", "Maria", "Nowak")

    roundTripSingleObject(createAvroSourceFactory(useSpecificAvroReader = true), givenObj, 2, RecordSchemaV2, RecordTopic)
  }

  test("should read last generated key-value object") {
    val givenObj = (123, 345)

    val serializedKey = keySerializer.serialize(IntTopic, givenObj._1)
    val serializedValue = valueSerializer.serialize(IntTopic, givenObj._2)
    kafkaClient.sendRawMessage(IntTopic, serializedKey, serializedValue, Some(0))

    readLastMessageAndVerify(createKeyValueAvroSourceFactory[Int, Int], givenObj, 1, IntSchema, IntTopic)
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
  extends ConfluentKeyValueKafkaAvroDeserializationFactory[(Key, Value)](ConfluentKafkaAvroDeserializationMode.STATIC, schemaRegistryClientFactory, useSpecificAvroReader = false)(
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

  val RecordSchemaV1: Schema = AvroUtils.parseSchema(
    """{
      |  "type": "record",
      |  "namespace": "pl.touk.nussknacker.engine.avro",
      |  "name": "FullName",
      |  "fields": [
      |    { "name": "first", "type": "string" },
      |    { "name": "last", "type": "string" }
      |  ]
      |}
    """.stripMargin)

  val RecordSchemaV2: Schema = AvroUtils.parseSchema(
    """{
      |  "type": "record",
      |  "namespace": "pl.touk.nussknacker.engine.avro",
      |  "name": "FullName",
      |  "fields": [
      |    { "name": "first", "type": "string" },
      |    { "name": "middle", "type": ["null", "string"], "default": null },
      |    { "name": "last", "type": "string" }
      |  ]
      |}
    """.stripMargin)

  val IntSchema: Schema = AvroUtils.parseSchema(
    """{
      |  "type": "int"
      |}
    """.stripMargin
  )

  val schemaRegistryMockClient: MockSchemaRegistryClient = new MockConfluentSchemaRegistryClientBuilder()
    .register(RecordTopic, RecordSchemaV1, 1, isKey = false)
    .register(RecordTopic, RecordSchemaV2, 2, isKey = false)
    .register(IntTopic, IntSchema, 1, isKey = false)
    .register(IntTopic, IntSchema, 1, isKey = true)
    .build

  val expirationTime: Option[FiniteDuration] = Some(FiniteDuration(5, TimeUnit.MINUTES))

  val factory: CachedConfluentSchemaRegistryClientFactory = new CachedConfluentSchemaRegistryClientFactory(DefaultCache.defaultMaximumSize, expirationTime, expirationTime) {
    override protected def confluentClient(kafkaConfig: KafkaConfig): SchemaRegistryClient =
      schemaRegistryMockClient
  }
}

case class FullNameV1(var first: CharSequence, var last: CharSequence) extends SpecificRecordBase {
  def this() = this(null, null)

  override def getSchema: Schema = MockSchemaRegistry.RecordSchemaV1

  override def get(field: Int): AnyRef =
    field match {
      case 0 => first
      case 1 => last
      case _ => throw new AvroRuntimeException("Bad index")
    }

  override def put(field: Int, value: scala.Any): Unit =
    field match {
      case 0 => first = value.asInstanceOf[CharSequence]
      case 1 => last = value.asInstanceOf[CharSequence]
      case _ => throw new AvroRuntimeException("Bad index")
    }
}

case class FullName(var first: CharSequence, var middle: CharSequence, var last: CharSequence) extends SpecificRecordBase {
  def this() = this(null, null, null)

  override def getSchema: Schema = MockSchemaRegistry.RecordSchemaV2

  override def get(field: Int): AnyRef =
    field match {
      case 0 => first
      case 1 => middle
      case 2 => last
      case _ => throw new AvroRuntimeException("Bad index")
    }

  override def put(field: Int, value: scala.Any): Unit =
    field match {
      case 0 => first = value.asInstanceOf[CharSequence]
      case 1 => middle = value.asInstanceOf[CharSequence]
      case 2 => last = value.asInstanceOf[CharSequence]
      case _ => throw new AvroRuntimeException("Bad index")
    }
}
