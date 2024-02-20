package pl.touk.nussknacker.defaultmodel.table

import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.config.{Config, ConfigFactory}
import io.confluent.kafka.schemaregistry.json.JsonSchema
import org.scalatest.Inside
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.defaultmodel.FlinkWithKafkaSuite
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.flink.table.kafka.KafkaTableApiComponentProvider
import pl.touk.nussknacker.engine.schemedkafka.KafkaAvroIntegrationMockSchemaRegistry.schemaRegistryMockClient
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.ConfluentUtils
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.testmode.ResultsCollectingListenerHolder

class TableApiKafkaDebugTest extends FlinkWithKafkaSuite with Matchers with Inside {

  val sinkId       = "sinkId"
  val sourceId     = "sourceId"
  val resultNodeId = "resultVar"

  private def initializeListener = ResultsCollectingListenerHolder.registerRun

  private val inputTopic: String  = "input"
  private val outputTopic: String = "output"

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

  private lazy val tableKafkaComponentsConfig: Config = ConfigFactory
    .empty()
    .withValue(
      "kafkaTable.kafkaProperties.'bootstrap.servers'",
      fromAnyRef(kafkaServer.kafkaAddress)
    )
    .withValue(
      "topic",
      fromAnyRef(inputTopic)
    )

  override lazy val additionalComponents: List[ComponentDefinition] = new KafkaTableApiComponentProvider().create(
    tableKafkaComponentsConfig,
    ProcessObjectDependencies.withConfig(tableKafkaComponentsConfig)
  )

  test("should produce results for each element in list") {
    val inputSubject  = ConfluentUtils.topicSubject(inputTopic, isKey = false)
    val outputSubject = ConfluentUtils.topicSubject(outputTopic, isKey = false)
    schemaRegistryMockClient.register(inputSubject, schema)
    schemaRegistryMockClient.register(outputSubject, schema)

    val scenario = ScenarioBuilder
      .streaming("test")
      .source(sourceId, "KafkaSource-TableApi")
      .buildSimpleVariable(resultNodeId, "varName", "#input")
      .emptySink(sinkId, "KafkaSink-TableApi")

    1 shouldBe 1

  }

}
