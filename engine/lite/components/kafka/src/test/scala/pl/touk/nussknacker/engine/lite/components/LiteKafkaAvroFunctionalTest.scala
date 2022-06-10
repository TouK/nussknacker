package pl.touk.nussknacker.engine.lite.components

import com.typesafe.config.ConfigValueFactory
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.avro.AvroUtils
import pl.touk.nussknacker.engine.avro.encode.ValidationMode
import pl.touk.nussknacker.engine.avro.schemaregistry.SchemaVersionOption
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.{MockConfluentSchemaRegistryClientFactory, MockSchemaRegistryClient}
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.lite.util.test.{KafkaAvroConsumerRecord, LiteKafkaTestScenarioRunner}
import pl.touk.nussknacker.engine.util.namespaces.DefaultNamespacedObjectNaming

class LiteKafkaAvroFunctionalTest extends FunSuite with Matchers {

  import LiteKafkaComponentProvider._
  import LiteKafkaTestScenarioRunner._
  import pl.touk.nussknacker.engine.avro.KafkaAvroBaseComponentTransformer._
  import pl.touk.nussknacker.engine.spel.Implicits._

  private val recordSchema = AvroUtils.parseSchema(
    """{
      |  "type": "record",
      |  "namespace": "pl.touk.nussknacker.engine.avro",
      |  "name": "FullName",
      |  "fields": [
      |    { "name": "first", "type": "string" },
      |    { "name": "last", "type": "string" },
      |    { "name": "age", "type": "int" },
      |    { "name": "address", "type": {"type":"record", "name": "Address", "fields": [{ "name": "city", "type": "string" }]} }
      |  ]
      |}
    """.stripMargin)

  private val inputTopic = "input"
  private val outputTopic = "output"


  test("should test end to end kafka avro sink / source") {
    //Given
    val scenario = ScenarioBuilder.streamingLite("check avro serialization")
      .source("my-source", KafkaAvroName, TopicParamName -> s"'$inputTopic'", SchemaVersionParamName -> s"'${SchemaVersionOption.LatestOptionName}'")
      .emptySink("my-sink", KafkaSinkRawAvroName, TopicParamName -> s"'$outputTopic'",  SchemaVersionParamName -> s"'${SchemaVersionOption.LatestOptionName}'", SinkKeyParamName -> "", SinkValueParamName -> s"#input", SinkValidationModeParameterName -> s"'${ValidationMode.strict.name}'")

    val (config, mockedComponents, mockSchemaRegistryClient) = prepareRunnerConfig
    val runtime = new LiteKafkaTestScenarioRunner(mockSchemaRegistryClient, mockedComponents, config)
    val schemaId = runtime.registerSchemaAvro(inputTopic, recordSchema)
    runtime.registerSchemaAvro(outputTopic, recordSchema)

    //When
    val record = AvroUtils.createRecord(recordSchema, Map(
      "first" -> "Jan", "last" -> "Kowalski", "age" -> 18, "address" -> Map("city" -> "Warsaw")
    ))

    val input = KafkaAvroConsumerRecord(inputTopic, record, schemaId)
    val result = runtime.runWithAvroData(scenario, List(input))

    //Then
    result.map(_.value()) shouldBe List(input.value().record)
  }

  private def prepareRunnerConfig = {
    // we disable default kafka components to replace them by mocked
    val config = DefaultKafkaConfig.withValue("components.kafka.disabled", ConfigValueFactory.fromAnyRef(true))
    val mockSchemaRegistryClient = new MockSchemaRegistryClient
    val mockedKafkaComponents = new LiteKafkaComponentProvider(new MockConfluentSchemaRegistryClientFactory(mockSchemaRegistryClient))
    val processObjectDependencies = ProcessObjectDependencies(config, DefaultNamespacedObjectNaming)
    val mockedComponents = mockedKafkaComponents.create(config, processObjectDependencies)
    (config, mockedComponents, mockSchemaRegistryClient)
  }
}
