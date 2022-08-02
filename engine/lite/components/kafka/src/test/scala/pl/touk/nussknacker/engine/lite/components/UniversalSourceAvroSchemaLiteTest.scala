package pl.touk.nussknacker.engine.lite.components

import com.typesafe.config.ConfigValueFactory._
import io.circe.parser.parse
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.avro.AvroUtils
import pl.touk.nussknacker.engine.avro.schemaregistry.SchemaVersionOption
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.{MockConfluentSchemaRegistryClientFactory, MockSchemaRegistryClient}
import pl.touk.nussknacker.engine.avro.sink.UniversalKafkaSinkFactory.RawEditorParamName
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.lite.util.test.LiteKafkaTestScenarioRunner
import pl.touk.nussknacker.engine.util.namespaces.DefaultNamespacedObjectNaming
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage

class UniversalSourceAvroSchemaLiteTest extends FunSuite with Matchers with ValidatedValuesDetailedMessage {

  import LiteKafkaComponentProvider._
  import LiteKafkaTestScenarioRunner._
  import pl.touk.nussknacker.engine.avro.KafkaAvroBaseComponentTransformer._
  import pl.touk.nussknacker.engine.spel.Implicits._

  private val inputTopic = "input"
  private val outputTopic = "output"
  private val schema = AvroUtils.parseSchema(
    s"""{
       |  "type": "record",
       |  "namespace": "pl.touk.nussknacker.engine.avro",
       |  "name": "FullName",
       |  "fields": [
       |    { "name": "first", "type": "string" },
       |    { "name": "last", "type": "string" },
       |    { "name": "age", "type": "int" }
       |  ]
       |}
    """.stripMargin)

  private val scenario = ScenarioBuilder.streamingLite("check json serialization")
    .source("my-source", KafkaUniversalName, TopicParamName -> s"'$inputTopic'", SchemaVersionParamName -> s"'${SchemaVersionOption.LatestOptionName}'")
    .emptySink("my-sink", KafkaUniversalName, TopicParamName -> s"'$outputTopic'", SchemaVersionParamName -> s"'${SchemaVersionOption.LatestOptionName}'", SinkKeyParamName -> "", RawEditorParamName -> "false",
      "first" -> s"#input.first", "last" -> "#input.last", "age" -> "#input.age")

  test("should read data with json payload on avro schema based topic") {
    //Given
    val runtime = createRuntime
    val schemaId = runtime.registerAvroSchema(inputTopic, schema)
    runtime.registerAvroSchema(outputTopic, schema)

    //When
    val jsonRecord =
      """{
        |  "first": "John",
        |  "last": "Doe",
        |  "age": 21
        |}""".stripMargin.getBytes()

    val input = new ConsumerRecord(inputTopic, 1, 1, null.asInstanceOf[Array[Byte]], jsonRecord)

    val list: List[ConsumerRecord[Array[Byte],Array[Byte]]] = List(input)
    val result = runtime.runWithRawData(scenario, list).validValue
    val resultWithValue = result.copy(successes = result.successes.map(_.value()))

    //Then
    val resultJson = new String(resultWithValue.successes.head)
    val expected = new String(jsonRecord)
    parse(expected) shouldBe parse(resultJson)
  }


  private def createRuntime = {
    val config = DefaultKafkaConfig
      // we disable default kafka components to replace them by mocked
      .withValue("components.kafka.disabled", fromAnyRef(true))
      .withValue("kafka.kafkaProperties.\"schema.registry.url\"", fromAnyRef("schema-registry:666"))
      .withValue("kafka.avroAsJsonSerialization", fromAnyRef(true))

    val mockSchemaRegistryClient = new MockSchemaRegistryClient
    val mockedKafkaComponents = new LiteKafkaComponentProvider(new MockConfluentSchemaRegistryClientFactory(mockSchemaRegistryClient))
    val processObjectDependencies = ProcessObjectDependencies(config, DefaultNamespacedObjectNaming)
    val mockedComponents = mockedKafkaComponents.create(config, processObjectDependencies)

    new LiteKafkaTestScenarioRunner(mockSchemaRegistryClient, mockedComponents, config)
  }
}
