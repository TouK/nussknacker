package pl.touk.nussknacker.defaultmodel

import com.typesafe.config.Config
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.scalalogging.LazyLogging
import io.circe.Json
import org.scalatest.time.{Millis, Seconds, Span}
import pl.touk.nussknacker.defaultmodel.SampleSchemas.RecordSchemas
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.kafka.KafkaTestUtils.richConsumer
import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer
import pl.touk.nussknacker.engine.spel.SpelExtension._
import pl.touk.nussknacker.test.{KafkaConfigProperties, PatientScalaFutures}

import scala.jdk.CollectionConverters._

class FinkExactlyOnceItSpec
    extends FlinkWithKafkaSuite
    with PatientScalaFutures
    with LazyLogging
    with WithKafkaComponentsConfig {

  override val avroAsJsonSerialization: Boolean = true

  override def kafkaComponentsConfig: Config = super.kafkaComponentsConfig
    .withValue("config.sinkDeliveryGuarantee", fromAnyRef("EXACTLY_ONCE"))
    .withValue(KafkaConfigProperties.property("config", "isolation.level"), fromAnyRef("read_committed"))

  private val inputOutputMessage =
    """
      |{
      |  "first": "Jan",
      |  "last": "Kowalski"
      |}
      |""".stripMargin

  test("should read message from kafka and write message in transaction to kafka on checkpoint") {
    val topicConfig = createAndRegisterAvroTopicConfig("cash-transactions", RecordSchemas)
    kafkaClient.createTopic(topicConfig.input.name, partitions = 1)
    kafkaClient.createTopic(topicConfig.output.name, partitions = 1)

    val sendResult = sendAsJson(inputOutputMessage, topicConfig.input).futureValue
    logger.info(s"Messages sent successful: $sendResult")

    run(buildScenario(topicConfig)) {
      val consumer = kafkaClient.createConsumer()
      val result   = consumer.consumeWithJson[Json](topicConfig.output.name).take(1).head
      result.message() shouldEqual parseJson(inputOutputMessage)
      eventually(timeout(Span(2, Seconds)), interval(Span(100, Millis))) {
        // https://stackoverflow.com/a/56183132
        // if message is committed with transaction there is additional control batch in a log
        consumer.getEndOffsets(topicConfig.output.name).values().asScala.head shouldEqual 2
      }
    }
  }

  private def buildScenario(topicConfig: TopicConfig): CanonicalProcess =
    ScenarioBuilder
      .streaming("exactly-once-test")
      .parallelism(1)
      .source(
        "read committed source",
        "kafka",
        KafkaUniversalComponentTransformer.topicParamName.value         -> s"'${topicConfig.input.name}'".spel,
        KafkaUniversalComponentTransformer.schemaVersionParamName.value -> s"'1'".spel
      )
      .emptySink(
        "end",
        "kafka",
        KafkaUniversalComponentTransformer.sinkKeyParamName.value       -> "".spel,
        KafkaUniversalComponentTransformer.topicParamName.value         -> s"'${topicConfig.output.name}'".spel,
        KafkaUniversalComponentTransformer.schemaVersionParamName.value -> s"'1'".spel,
        KafkaUniversalComponentTransformer.sinkRawEditorParamName.value -> s"false".spel,
        "first"                                                         -> "#input.first".spel,
        "last"                                                          -> "#input.last".spel
      )

}
