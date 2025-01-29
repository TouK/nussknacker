package pl.touk.nussknacker.engine.kafka.exception

import com.typesafe.config.ConfigValueFactory.fromAnyRef
import org.scalatest.{EitherValues, OptionValues}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.process.SinkFactory
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.kafka.KafkaSpec
import pl.touk.nussknacker.engine.process.helpers.SampleNodes
import pl.touk.nussknacker.engine.process.helpers.SampleNodes.SimpleRecord
import pl.touk.nussknacker.engine.process.runner.UnitTestsFlinkRunner
import pl.touk.nussknacker.engine.spel.SpelExtension._
import pl.touk.nussknacker.engine.testing.LocalModelData

import java.util.Date

class KafkaExceptionConsumerSpec
    extends AnyFunSuite
    with OptionValues
    with FlinkSpec
    with KafkaSpec
    with Matchers
    with EitherValues {

  import pl.touk.nussknacker.engine.kafka.KafkaTestUtils.richConsumer

  test("should record errors on topic") {
    val message = runTest(s"testProcess-shortString", stringVariable = "'short string'".spel)

    val inputEvent = extractInputEventMap(message)
    (inputEvent - "input") shouldBe Map(
      "string" -> "short string"
    )
  }

  test("should record errors on topic - strips context from too large error input") {
    // long string variable: 8^7 = 2097152 = 2 MB
    val message = runTest(
      "testProcess-longString",
      stringVariable = ("'xxxxxxxx'" + ".replaceAll('x', 'xxxxxxxx')".repeat(6)).spel,
      maxMessageBytes = 5242880
    )

    val inputEvent = extractInputEventMap(message)
    inputEvent.keySet shouldBe Set("!warning")
    inputEvent("!warning") should startWith(
      "variables truncated, they didn't fit within max allowed size of a Kafka message:"
    )
  }

  private def runTest(
      scenarioName: String,
      stringVariable: Expression,
      maxMessageBytes: Int = 1048576
  ): KafkaExceptionInfo = {
    val topicName = s"$scenarioName.errors"

    val configWithExceptionHandler = config
      .withValue("exceptionHandler.type", fromAnyRef("Kafka"))
      .withValue("exceptionHandler.topic", fromAnyRef(topicName))
      .withValue("exceptionHandler.maxMessageBytes", fromAnyRef(maxMessageBytes))
      .withValue("exceptionHandler.includeInputEvent", fromAnyRef(true))
      .withValue("exceptionHandler.additionalParams.configurableKey", fromAnyRef("sampleValue"))
      .withValue("exceptionHandler.kafka", config.getConfig("kafka").root())

    val modelData = LocalModelData(
      configWithExceptionHandler,
      List(
        ComponentDefinition(
          "source",
          SampleNodes.simpleRecordSource(SimpleRecord("id1", 1, "value1", new Date()) :: Nil)
        ),
        ComponentDefinition("sink", SinkFactory.noParam(SampleNodes.MonitorEmptySink))
      )
    )

    val process = ScenarioBuilder
      .streaming(scenarioName)
      .source("source", "source")
      .buildSimpleVariable("string", "string", stringVariable)
      .filter("shouldFail", "1/{0, 1}[0] != 10".spel)
      .emptySink("end", "sink")

    flinkMiniCluster.withExecutionEnvironment { env =>
      UnitTestsFlinkRunner.registerInEnvironmentWithModel(env.env, modelData)(process)
      val message = env.withJobRunning(process.name.value) {
        val consumed = kafkaClient.createConsumer().consumeWithJson[KafkaExceptionInfo](topicName).take(1).head

        consumed.key() shouldBe s"$scenarioName-shouldFail"

        consumed.message()
      }

      message.processName.value shouldBe scenarioName
      message.nodeId shouldBe Some("shouldFail")
      message.message shouldBe Some("Expression [1/{0, 1}[0] != 10] evaluation failed, message: / by zero")
      message.exceptionInput shouldBe Some("1/{0, 1}[0] != 10")
      message.stackTrace.value should include("evaluation failed, message:")
      message.additionalData shouldBe Map("configurableKey" -> "sampleValue")

      message
    }
  }

  def extractInputEventMap(message: KafkaExceptionInfo): Map[String, String] =
    message.inputEvent.value.as[Map[String, String]].value

}
