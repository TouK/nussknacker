package pl.touk.nussknacker.defaultmodel

import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import io.circe.Json
import io.confluent.kafka.schemaregistry.json.JsonSchema
import pl.touk.nussknacker.engine.api.process.TopicName
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.kafka.KafkaTestUtils.richConsumer
import pl.touk.nussknacker.engine.kafka.UncategorizedTopicName.ToUncategorizedTopicName
import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.SchemaVersionOption
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.ConfluentUtils
import pl.touk.nussknacker.engine.spel

class FlinkNamespacedKafkaTest extends FlinkWithKafkaSuite {

  import spel.Implicits._

  private val namespaceName = "ns"
  private val inputTopic    = TopicName.OfSource("input")
  private val outputTopic   = TopicName.OfSink("output")

  override lazy val config: Config = ConfigFactory
    .load()
    .withValue("namespace", fromAnyRef(namespaceName))

  private val schema = new JsonSchema(
    """{
      |  "type": "object",
      |  "properties": {
      |    "value" : { "type": "string" }
      |  }
      |}
      |""".stripMargin
  )

  private val record =
    """{
      |  "value": "Jan"
      |}""".stripMargin

  test("should send message to topic with appended namespace") {
    val namespacedInputTopic  = Namespaced(inputTopic)
    val namespacedOutputTopic = Namespaced(outputTopic)

    val inputSubject  = ConfluentUtils.topicSubject(namespacedInputTopic.toUncategorizedTopicName, isKey = false)
    val outputSubject = ConfluentUtils.topicSubject(namespacedOutputTopic.toUncategorizedTopicName, isKey = false)
    schemaRegistryMockClient.register(inputSubject, schema)
    schemaRegistryMockClient.register(outputSubject, schema)

    sendAsJson(record, namespacedInputTopic)

    val scenarioId = "scenarioId"
    val sourceId   = "input"
    val process = ScenarioBuilder
      .streaming(scenarioId)
      .parallelism(1)
      .source(
        sourceId,
        "kafka",
        KafkaUniversalComponentTransformer.topicParamName.value         -> s"'${inputTopic.name}'",
        KafkaUniversalComponentTransformer.schemaVersionParamName.value -> s"'${SchemaVersionOption.LatestOptionName}'"
      )
      .emptySink(
        "output",
        "kafka",
        KafkaUniversalComponentTransformer.sinkKeyParamName.value       -> "",
        KafkaUniversalComponentTransformer.sinkValueParamName.value     -> "#input",
        KafkaUniversalComponentTransformer.topicParamName.value         -> s"'${outputTopic.name}'",
        KafkaUniversalComponentTransformer.schemaVersionParamName.value -> s"'${SchemaVersionOption.LatestOptionName}'",
        KafkaUniversalComponentTransformer.sinkRawEditorParamName.value -> s"true",
      )

    run(process) {
      val processed =
        kafkaClient
          .createConsumer()
          .consumeWithJson[Json](namespacedOutputTopic.name)
          .take(1)
          .map(_.message())
          .toList
      processed.head shouldBe parseJson(record)
    }
  }

  trait Namespaced[T <: TopicName] {
    def withNamespace(topic: T, namespace: String): T
  }

  object Namespaced {

    def apply[T <: TopicName](topic: T)(implicit ns: Namespaced[T]): T = {
      ns.withNamespace(topic, namespaceName)
    }

    implicit val sourceNamespaced: Namespaced[TopicName.OfSource] = new Namespaced[TopicName.OfSource] {
      override def withNamespace(topic: TopicName.OfSource, namespace: String): TopicName.OfSource =
        TopicName.OfSource(s"${namespace}_${topic.name}")
    }

    implicit val sinkNamespaced: Namespaced[TopicName.OfSink] = new Namespaced[TopicName.OfSink] {
      override def withNamespace(topic: TopicName.OfSink, namespace: String): TopicName.OfSink =
        TopicName.OfSink(s"${namespace}_${topic.name}")
    }

  }

}
