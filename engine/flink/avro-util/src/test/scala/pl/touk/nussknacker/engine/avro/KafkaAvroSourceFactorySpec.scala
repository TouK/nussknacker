package pl.touk.nussknacker.engine.avro

import java.nio.charset.StandardCharsets

import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import io.confluent.kafka.schemaregistry.client.{MockSchemaRegistryClient, SchemaRegistryClient => ConfluentKafkaSchemaRegistryClient}
import io.confluent.kafka.serializers.KafkaAvroSerializer
import org.apache.avro.generic.GenericData
import org.apache.avro.specific.SpecificRecordBase
import org.apache.avro.{AvroRuntimeException, Schema}
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.scala._
import org.scalatest.{BeforeAndAfterAll, FunSpec, Matchers}
import pl.touk.nussknacker.engine.api.namespaces.DefaultObjectNaming
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, Source, TestDataGenerator, TestDataParserProvider}
import pl.touk.nussknacker.engine.api.{MetaData, StreamMetaData}
import pl.touk.nussknacker.engine.avro.confluent.{ConfluentAvroKeyValueDeserializationSchemaFactory, ConfluentSchemaRegistryClient, ConfluentSchemaRegistryProvider}
import pl.touk.nussknacker.engine.kafka.{KafkaSourceFactory, KafkaSpec}

class KafkaAvroSourceFactorySpec extends FunSpec with BeforeAndAfterAll with KafkaSpec with Matchers with LazyLogging {

  import MockSchemaRegistry._

  import collection.JavaConverters._

  // schema.registry.url have to be defined even for MockSchemaRegistryClient
  override lazy val config: Config = ConfigFactory.load()
    .withValue("kafka.kafkaAddress", fromAnyRef(kafkaZookeeperServer.kafkaAddress))
    .withValue("kafka.kafkaProperties.\"schema.registry.url\"", fromAnyRef("not_used"))

  private lazy val keySerializer = {
    val serializer = new KafkaAvroSerializer(Client.confluentClient)
    serializer.configure(Map[String, AnyRef]("schema.registry.url" -> "not_used").asJava, true)
    serializer
  }

  private lazy val valueSerializer = new KafkaAvroSerializer(Client.confluentClient)

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

  private def createAvroSourceFactory(useSpecificAvroReader: Boolean): KafkaAvroSourceFactory[AnyRef] = {
    val provider = ConfluentSchemaRegistryProvider[AnyRef](Client, useSpecificAvroReader, formatKey = false)
    new KafkaAvroSourceFactory(provider, ProcessObjectDependencies(config, DefaultObjectNaming))
  }

  private def createKeyValueAvroSourceFactory[K: TypeInformation, V: TypeInformation]: KafkaAvroSourceFactory[(K, V)] = {
    val deserializerFactory = new TupleAvroKeyValueDeserializationSchemaFactory[K, V](Client.confluentClient)
    val provider = ConfluentSchemaRegistryProvider(Client, None, Some(deserializerFactory), useSpecificAvroReader = false, formatKey = true)
    new KafkaAvroSourceFactory(provider, ProcessObjectDependencies(config, DefaultObjectNaming))
  }

}

class TupleAvroKeyValueDeserializationSchemaFactory[Key, Value](schemaRegistryClient: ConfluentKafkaSchemaRegistryClient)(implicit keyTypInfo: TypeInformation[Key], valueTypInfo: TypeInformation[Value])
  extends ConfluentAvroKeyValueDeserializationSchemaFactory[(Key, Value)](schemaRegistryClient, useSpecificAvroReader = false)(
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

  val Client: ConfluentSchemaRegistryClient = {
    val mockSchemaRegistry = new MockSchemaRegistryClient
    def registerSchema(topic: String, isKey: Boolean, schema: Schema): Unit = {
      val subject = topic + "-" + (if (isKey) "key" else "value")
      mockSchemaRegistry.register(subject, schema)
    }

    registerSchema(RecordTopic, isKey = false, RecordSchemaV1)
    registerSchema(RecordTopic, isKey = false, RecordSchemaV2)
    registerSchema(IntTopic, isKey = true, IntSchema)
    registerSchema(IntTopic, isKey = false, IntSchema)

    new ConfluentSchemaRegistryClient(mockSchemaRegistry)
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
