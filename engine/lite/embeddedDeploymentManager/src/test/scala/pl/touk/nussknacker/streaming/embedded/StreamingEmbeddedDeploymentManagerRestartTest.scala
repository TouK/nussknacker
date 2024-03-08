package pl.touk.nussknacker.streaming.embedded

import pl.touk.nussknacker.engine.api.deployment.DataFreshnessPolicy
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.spel.Implicits._

class StreamingEmbeddedDeploymentManagerRestartTest extends BaseStreamingEmbeddedDeploymentManagerTest {
  import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer._

  protected implicit val freshnessPolicy: DataFreshnessPolicy = DataFreshnessPolicy.Fresh

  // This test is in separate suite to make sure that restarting of kafka server have no influence on other test case scenarios
  test("Set status to restarting when scenario fails and back to running when the problems are fixed") {
    val fixture @ FixtureParam(manager, _, inputTopic, outputTopic) = prepareFixture()

    val name = ProcessName("testName")
    val scenario = ScenarioBuilder
      .streamingLite(name.value)
      .source("source", "kafka", TopicParamName.value -> s"'$inputTopic'", SchemaVersionParamName.value -> "'latest'")
      .emptySink(
        "sink",
        "kafka",
        TopicParamName.value              -> s"'$outputTopic'",
        SchemaVersionParamName.value      -> "'latest'",
        "Key"                             -> "null",
        SinkRawEditorParamName.value      -> "true",
        SinkValidationModeParamName.value -> "'strict'",
        SinkValueParamName.value          -> "#input"
      )

    wrapInFailingLoader {
      fixture.deployScenario(scenario)
    }

    kafkaServer.kafkaServer.shutdown()
    kafkaServer.kafkaServer.awaitShutdown()

    eventually {
      val jobStatuses = manager.getProcessStates(name).futureValue.value
      jobStatuses.map(_.status) shouldBe List(SimpleStateStatus.Restarting)
    }

    kafkaServer.kafkaServer.startup()

    eventually {
      manager.getProcessStates(name).futureValue.value.map(_.status) shouldBe List(SimpleStateStatus.Running)
    }
  }

}
