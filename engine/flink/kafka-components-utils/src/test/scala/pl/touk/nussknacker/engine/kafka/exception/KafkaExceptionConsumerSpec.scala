package pl.touk.nussknacker.engine.kafka.exception

import com.typesafe.config.ConfigValueFactory.fromAnyRef
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.process.SinkFactory
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.kafka.KafkaSpec
import pl.touk.nussknacker.engine.process.helpers.SampleNodes
import pl.touk.nussknacker.engine.process.helpers.SampleNodes.SimpleRecord
import pl.touk.nussknacker.engine.process.runner.UnitTestsFlinkRunner
import pl.touk.nussknacker.engine.spel.SpelExtension._
import pl.touk.nussknacker.engine.testing.LocalModelData

import java.util.Date

class KafkaExceptionConsumerSpec extends AnyFunSuite with FlinkSpec with KafkaSpec with Matchers {

  import pl.touk.nussknacker.engine.kafka.KafkaTestUtils.richConsumer
  private val topicName = "testingErrors"

  protected var modelData: ModelData = _

  protected override def beforeAll(): Unit = {
    super.beforeAll()
    val configWithExceptionHandler = config
      .withValue("exceptionHandler.type", fromAnyRef("Kafka"))
      .withValue("exceptionHandler.topic", fromAnyRef(topicName))
      .withValue("exceptionHandler.additionalParams.configurableKey", fromAnyRef("sampleValue"))
      .withValue("exceptionHandler.kafka", config.getConfig("kafka").root())

    modelData = LocalModelData(
      configWithExceptionHandler,
      List(
        ComponentDefinition(
          "source",
          SampleNodes.simpleRecordSource(SimpleRecord("id1", 1, "value1", new Date()) :: Nil)
        ),
        ComponentDefinition("sink", SinkFactory.noParam(SampleNodes.MonitorEmptySink))
      )
    )
  }

  test("should record errors on topic") {
    val process = ScenarioBuilder
      .streaming("testProcess")
      .source("source", "source")
      .filter("shouldFail", "1/{0, 1}[0] != 10".spel)
      .emptySink("end", "sink")

    val env = flinkMiniCluster.createExecutionEnvironment()
    UnitTestsFlinkRunner.registerInEnvironmentWithModel(env, modelData)(process)
    env.withJobRunning(process.name.value) {
      val consumed = kafkaClient.createConsumer().consumeWithJson[KafkaExceptionInfo](topicName).take(1).head
      consumed.key() shouldBe "testProcess-shouldFail"

      consumed.message().nodeId shouldBe Some("shouldFail")
      consumed.message().processName.value shouldBe "testProcess"
      consumed.message().message shouldBe Some("Expression [1/{0, 1}[0] != 10] evaluation failed, message: / by zero")
      consumed.message().exceptionInput shouldBe Some("1/{0, 1}[0] != 10")
      consumed.message().additionalData shouldBe Map("configurableKey" -> "sampleValue")
    }

  }

}
