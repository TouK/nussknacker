package pl.touk.nussknacker.engine.avro

import java.nio.charset.StandardCharsets

import com.typesafe.scalalogging.LazyLogging
import io.confluent.kafka.schemaregistry.client.{MockSchemaRegistryClient, SchemaRegistryClient}
import io.confluent.kafka.serializers.KafkaAvroSerializer
import org.apache.avro.generic.GenericData
import org.apache.avro.specific.SpecificRecordBase
import org.apache.avro.{AvroRuntimeException, Schema}
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.scalatest.{BeforeAndAfterAll, FunSpec, Matchers}
import pl.touk.nussknacker.engine.api.process.{Source, TestDataGenerator, TestDataParserProvider}
import pl.touk.nussknacker.engine.api.{MetaData, StreamMetaData}
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, KafkaSourceFactory, KafkaSpec}
import org.apache.flink.api.scala._

class KafkaAvroSourceFactorySpec extends FunSpec with BeforeAndAfterAll with KafkaSpec with Matchers with LazyLogging {

  import MockSchemaRegistry._
  import collection.JavaConverters._

  // schema.registry.url have to be defined even for MockSchemaRegistryClient
  override lazy val kafkaConfig = KafkaConfig(kafkaZookeeperServer.kafkaAddress,
    Some(Map("schema.registry.url" -> "not_used")), None)

  private lazy val keySerializer = {
    val serializer = new KafkaAvroSerializer(MockSchemaRegistry.Registry)
    serializer.configure(Map[String, AnyRef]("schema.registry.url" -> "not_used").asJava, true)
    serializer
  }

  private lazy val valueSerializer = new KafkaAvroSerializer(MockSchemaRegistry.Registry)

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    kafkaClient.createTopic(RecordTopic, 2)
    kafkaClient.createTopic(IntTopic, 2)
  }

  it("should read last generated record") {
    val givenObj = {
      val r = new GenericData.Record(RecordSchemaV1)
      r.put("first", "Jan")
      r.put("last", "Kowalski")
      r
    }

    roundTripSingleObject(createAvroSourceFactory(useSpecificAvroReader = false), givenObj, RecordTopic)
  }

  it("should handle schema evolution") {
    val givenObj = {
      val r = new GenericData.Record(RecordSchemaV2)
      r.put("first", "Jan")
      r.put("middle", "Maria")
      r.put("last", "Kowalski")
      r
    }

    roundTripSingleObject(createAvroSourceFactory(useSpecificAvroReader = false), givenObj, RecordTopic)
  }

  it("should read last generated simple object") {
    val givenObj = 123123

    roundTripSingleObject(createAvroSourceFactory(useSpecificAvroReader = false), givenObj, IntTopic)
  }

  it("should read last generated record as a specific class") {
    val givenObj = FullName("Jan", "Maria", "Nowak")

    roundTripSingleObject(createAvroSourceFactory(useSpecificAvroReader = true), givenObj, RecordTopic)
  }

  it("should read last generated key-value object") {
    val givenObj = (123, 345)

    val serializedKey = keySerializer.serialize(IntTopic, givenObj._1)
    val serializedValue = valueSerializer.serialize(IntTopic, givenObj._2)
    kafkaClient.sendRawMessage(IntTopic, serializedKey, serializedValue, Some(0))

    readLastMessageAndVerify(createKeyValueAvroSourceFactory[Int, Int], givenObj, IntTopic)
  }

  private def roundTripSingleObject(sourceFactory: KafkaSourceFactory[_], givenObj: Any, topic: String) = {
    val serializedObj = valueSerializer.serialize(topic, givenObj)
    kafkaClient.sendRawMessage(topic, Array.empty, serializedObj, Some(0))

    readLastMessageAndVerify(sourceFactory, givenObj, topic)
  }

  private def readLastMessageAndVerify(sourceFactory: KafkaSourceFactory[_], givenObj: Any, topic: String) = {
    val source = sourceFactory.create(MetaData("", StreamMetaData()), topic)
      .asInstanceOf[Source[AnyRef] with TestDataGenerator with TestDataParserProvider[AnyRef]]
    val bytes = source.generateTestData(1)
    info("test object: " + new String(bytes, StandardCharsets.UTF_8))
    val deserializedObj = source.testDataParser.parseTestData(bytes)

    deserializedObj shouldEqual List(givenObj)
  }

  private def createAvroSourceFactory(useSpecificAvroReader: Boolean) = {
    new KafkaAvroSourceFactory[AnyRef](kafkaConfig, new AvroDeserializationSchemaFactory(MockSchemaRegistryClientFactory, useSpecificAvroReader),
      MockSchemaRegistryClientFactory, None)
  }

  private def createKeyValueAvroSourceFactory[K: TypeInformation, V: TypeInformation] = {
    new KafkaAvroSourceFactory(kafkaConfig, new TupleAvroKeyValueDeserializationSchemaFactory[K, V](MockSchemaRegistryClientFactory),
      MockSchemaRegistryClientFactory, None, formatKey = true)
  }

}

class TupleAvroKeyValueDeserializationSchemaFactory[Key, Value](schemaRegistryClientFactory: SchemaRegistryClientFactory)
                                                               (implicit keyTypInfo: TypeInformation[Key],
                                                                valueTypInfo: TypeInformation[Value])
  extends AvroKeyValueDeserializationSchemaFactory[(Key, Value)](schemaRegistryClientFactory, useSpecificAvroReader = false)(
    createTuple2TypeInformation(keyTypInfo, valueTypInfo)) {

  override protected type K = Key
  override protected type V = Value

  override protected def createObject(key: Key, value: Value, topic: String): (Key, Value) = {
    (key, value)
  }

}

object MockSchemaRegistryClientFactory extends SchemaRegistryClientFactory {

  override def createSchemaRegistryClient(kafkaConfig: KafkaConfig): SchemaRegistryClient =
    MockSchemaRegistry.Registry

}

object MockSchemaRegistry {

  val RecordTopic: String = "testAvroRecordTopic1"
  val IntTopic: String = "testAvroIntTopic1"

  private def parser = new Schema.Parser()

  val RecordSchemaV1: Schema = parser.parse(
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

  val RecordSchemaV2: Schema = parser.parse(
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

  val IntSchema: Schema = parser.parse(
    """{
      |  "type": "int"
      |}
    """.stripMargin
  )

  val Registry: MockSchemaRegistryClient = {
    val mockSchemaRegistry = new MockSchemaRegistryClient
    def registerSchema(topic: String, isKey: Boolean, schema: Schema): Unit = {
      val subject = topic + "-" + (if (isKey) "key" else "value")
      mockSchemaRegistry.register(subject, schema)
    }
    registerSchema(RecordTopic, isKey = false, RecordSchemaV1)
    registerSchema(RecordTopic, isKey = false, RecordSchemaV2)
    registerSchema(IntTopic, isKey = true, IntSchema)
    registerSchema(IntTopic, isKey = false, IntSchema)
    mockSchemaRegistry
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