package pl.touk.nussknacker.defaultmodel

import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import io.circe.Json
import io.confluent.kafka.schemaregistry.json.JsonSchema
import pl.touk.nussknacker.engine.api.process.TopicName
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.kafka.KafkaTestUtils.richConsumer
import pl.touk.nussknacker.engine.kafka.UnspecializedTopicName.ToUnspecializedTopicName
import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.SchemaVersionOption
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.ConfluentUtils

abstract class BaseFlinkNamespacedKafkaTest extends FlinkWithKafkaSuite {

  import pl.touk.nussknacker.engine.spel.SpelExtension._

  private val namespaceName = "ns"
  protected val inputTopic  = TopicName.ForSource("input")
  protected val outputTopic = TopicName.ForSink("output")

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

  def runTest(
      nuVisibleInputTopic: TopicName.ForSource,
      externalInputTopicName: TopicName.ForSource,
      nuVisibleOutputTopic: TopicName.ForSink,
      externalOutputTopicName: TopicName.ForSink
  ): Unit = {
    val inputSubject  = ConfluentUtils.topicSubject(externalInputTopicName.toUnspecialized, isKey = false)
    val outputSubject = ConfluentUtils.topicSubject(externalOutputTopicName.toUnspecialized, isKey = false)

    schemaRegistryMockClient.register(inputSubject, schema)
    schemaRegistryMockClient.register(outputSubject, schema)

    sendAsJson(record, externalInputTopicName)

    val scenarioId = "scenarioId"
    val sourceId   = "input"
    val process = ScenarioBuilder
      .streaming(scenarioId)
      .parallelism(1)
      .source(
        sourceId,
        "kafka",
        KafkaUniversalComponentTransformer.topicParamName.value -> s"'${nuVisibleInputTopic.name}'".spel,
        KafkaUniversalComponentTransformer.schemaVersionParamName.value -> s"'${SchemaVersionOption.LatestOptionName}'".spel
      )
      .emptySink(
        "output",
        "kafka",
        KafkaUniversalComponentTransformer.sinkKeyParamName.value   -> "".spel,
        KafkaUniversalComponentTransformer.sinkValueParamName.value -> "#input".spel,
        KafkaUniversalComponentTransformer.topicParamName.value     -> s"'${nuVisibleOutputTopic.name}'".spel,
        KafkaUniversalComponentTransformer.schemaVersionParamName.value -> s"'${SchemaVersionOption.LatestOptionName}'".spel,
        KafkaUniversalComponentTransformer.sinkRawEditorParamName.value -> s"true".spel,
      )

    run(process) {
      val processed =
        kafkaClient
          .createConsumer()
          .consumeWithJson[Json](externalOutputTopicName.name)
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

    implicit val sourceNamespaced: Namespaced[TopicName.ForSource] = new Namespaced[TopicName.ForSource] {
      override def withNamespace(topic: TopicName.ForSource, namespace: String): TopicName.ForSource =
        TopicName.ForSource(s"${namespace}_${topic.name}")
    }

    implicit val sinkNamespaced: Namespaced[TopicName.ForSink] = new Namespaced[TopicName.ForSink] {
      override def withNamespace(topic: TopicName.ForSink, namespace: String): TopicName.ForSink =
        TopicName.ForSink(s"${namespace}_${topic.name}")
    }

  }

  protected def namespacedTopic[T <: TopicName](topic: T)(implicit ns: Namespaced[T]): T = Namespaced(topic)

}

class FlinkNamespacedKafkaTest extends BaseFlinkNamespacedKafkaTest {

  test("should send message to topic with appended namespace") {
    val namespacedInputTopic  = namespacedTopic(inputTopic)
    val namespacedOutputTopic = namespacedTopic(outputTopic)
    runTest(
      nuVisibleInputTopic = inputTopic,
      externalInputTopicName = namespacedInputTopic,
      nuVisibleOutputTopic = outputTopic,
      externalOutputTopicName = namespacedOutputTopic
    )
  }

}

class FlinkDisabledNamespacedKafkaTest extends BaseFlinkNamespacedKafkaTest {

  override def kafkaComponentsConfig: Config =
    super.kafkaComponentsConfig.withValue("disableNamespace", fromAnyRef(true))

  test("should send message to topic without appended namespace when namespace is disabled for used kafka topic") {
    runTest(
      nuVisibleInputTopic = inputTopic,
      externalInputTopicName = inputTopic,
      nuVisibleOutputTopic = outputTopic,
      externalOutputTopicName = outputTopic
    )
  }

}
