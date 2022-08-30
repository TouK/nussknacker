package pl.touk.nussknacker.streaming.embedded

import pl.touk.nussknacker.engine.api.deployment.ProcessActionType
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.spel.Implicits._

class StreamingEmbeddedDeploymentManagerRestartTest extends BaseStreamingEmbeddedDeploymentManagerTest {

  // This test is in separate suite to make sure that restarting of kafka server have no influence on other test case scenarios
  test("Set status to restarting when scenario fails and back to running when the problems are fixed") {
    val fixture@FixtureParam(manager, _, inputTopic, outputTopic) = prepareFixture()

    val name = ProcessName("testName")
    val scenario = ScenarioBuilder
      .streamingLite(name.value)
      .source("source", "kafka", "Topic" -> s"'$inputTopic'", "Schema version" -> "'latest'")
      .emptySink("sink", "kafka", "Topic" -> s"'$outputTopic'", "Schema version" -> "'latest'", "Key" -> "null",
        "Raw editor" -> "true", "Value validation mode" -> "'strict'", "Value" -> "#input")

    wrapInFailingLoader {
      fixture.deployScenario(scenario)
    }

    kafkaServer.kafkaServer.shutdown()
    kafkaServer.kafkaServer.awaitShutdown()

    eventually {
      val jobStatus = manager.findJobStatus(name).futureValue
      jobStatus.map(_.status) shouldBe Some(SimpleStateStatus.Restarting)
      jobStatus.map(_.allowedActions).get should contain only (ProcessActionType.Cancel)
    }

    kafkaServer.kafkaServer.startup()

    eventually {
      manager.findJobStatus(name).futureValue.map(_.status) shouldBe Some(SimpleStateStatus.Running)
    }
  }

}
