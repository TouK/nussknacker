package pl.touk.nussknacker.defaultmodel

import com.typesafe.scalalogging.LazyLogging
import io.circe.Json
import pl.touk.nussknacker.defaultmodel.SampleSchemas.RecordSchemas
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.kafka.KafkaTestUtils.richConsumer
import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer
import pl.touk.nussknacker.engine.spel.SpelExtension._
import pl.touk.nussknacker.test.PatientScalaFutures

class MergeOnKafkaSourceOutputSpec
    extends FlinkWithKafkaSuite
    with PatientScalaFutures
    with LazyLogging
    with WithKafkaComponentsConfig {

  override val avroAsJsonSerialization: Boolean = true

  private val inputMessage =
    """
      |{
      |  "first": "Jan",
      |  "last": "Kowalski"
      |}
      |""".stripMargin

  private val outputMessage =
    """
      |{
      |  "first": "Jan",
      |  "last": "Kowalski"
      |}
      |""".stripMargin

  test("should perform merge on kafka source output") {
    val topicConfig = createAndRegisterAvroTopicConfig("cash-transactions", RecordSchemas)
    kafkaClient.createTopic(topicConfig.input.name, partitions = 1)
    kafkaClient.createTopic(topicConfig.output.name, partitions = 1)

    val sendResult = sendAsJson(inputMessage, topicConfig.input).futureValue
    logger.info(s"Messages sent successful: $sendResult")

    run(buildScenario(topicConfig)) {
      val consumer = kafkaClient.createConsumer()
      val result   = consumer.consumeWithJson[Json](topicConfig.output.name).take(1).head
      result.message() shouldEqual parseJson(outputMessage)
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
      .buildSimpleVariable(
        "someId",
        "someVarName",
        s"""#COLLECTION.merge(#input, #input).first""".spel
      )
      .emptySink(
        "end",
        "kafka",
        KafkaUniversalComponentTransformer.sinkKeyParamName.value       -> "".spel,
        KafkaUniversalComponentTransformer.topicParamName.value         -> s"'${topicConfig.output.name}'".spel,
        KafkaUniversalComponentTransformer.schemaVersionParamName.value -> s"'1'".spel,
        KafkaUniversalComponentTransformer.sinkRawEditorParamName.value -> s"false".spel,
        "first"                                                         -> "#someVarName".spel,
        "last"                                                          -> "#input.last".spel,
      )

}
