package pl.touk.nussknacker.defaultmodel

import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import io.circe.Json
import io.confluent.kafka.schemaregistry.json.JsonSchema
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.kafka.KafkaTestUtils.richConsumer
import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.SchemaVersionOption
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.ConfluentUtils

class FlinkNamespacedKafkaTest extends FlinkWithKafkaSuite {

  import pl.touk.nussknacker.engine.spel.SpelExtension._

  private val namespaceName: String            = "ns"
  private val inputTopic: String               = "input"
  private val outputTopic: String              = "output"
  private def namespaced(name: String): String = s"${namespaceName}_$name"

  override lazy val config: Config = ConfigFactory
    .load()
    .withValue("namespace", fromAnyRef(namespaceName))

  private val schema = new JsonSchema("""{
                                        |  "type": "object",
                                        |  "properties": {
                                        |    "value" : { "type": "string" }
                                        |  }
                                        |}
                                        |""".stripMargin)

  private val record =
    """{
      |  "value": "Jan"
      |}""".stripMargin

  test("should send message to topic with appended namespace") {
    val inputSubject  = ConfluentUtils.topicSubject(namespaced(inputTopic), isKey = false)
    val outputSubject = ConfluentUtils.topicSubject(namespaced(outputTopic), isKey = false)
    schemaRegistryMockClient.register(inputSubject, schema)
    schemaRegistryMockClient.register(outputSubject, schema)

    sendAsJson(record, namespaced(inputTopic))

    val scenarioId = "scenarioId"
    val sourceId   = "input"
    val process = ScenarioBuilder
      .streaming(scenarioId)
      .parallelism(1)
      .source(
        sourceId,
        "kafka",
        KafkaUniversalComponentTransformer.topicParamName.value -> s"'$inputTopic'".spel,
        KafkaUniversalComponentTransformer.schemaVersionParamName.value -> s"'${SchemaVersionOption.LatestOptionName}'".spel
      )
      .emptySink(
        "output",
        "kafka",
        KafkaUniversalComponentTransformer.sinkKeyParamName.value   -> "".spel,
        KafkaUniversalComponentTransformer.sinkValueParamName.value -> "#input".spel,
        KafkaUniversalComponentTransformer.topicParamName.value     -> s"'$outputTopic'".spel,
        KafkaUniversalComponentTransformer.schemaVersionParamName.value -> s"'${SchemaVersionOption.LatestOptionName}'".spel,
        KafkaUniversalComponentTransformer.sinkRawEditorParamName.value -> s"true".spel,
      )

    run(process) {
      val processed =
        kafkaClient
          .createConsumer()
          .consumeWithJson[Json](namespaced(outputTopic))
          .take(1)
          .map(_.message())
          .toList
      processed.head shouldBe parseJson(record)
    }
  }

}
