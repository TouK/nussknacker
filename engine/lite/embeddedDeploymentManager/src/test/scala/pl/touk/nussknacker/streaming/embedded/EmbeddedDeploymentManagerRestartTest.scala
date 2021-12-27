package pl.touk.nussknacker.streaming.embedded

import pl.touk.nussknacker.engine.api.deployment.ProcessActionType
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.build.StreamingLiteScenarioBuilder
import pl.touk.nussknacker.engine.spel.Implicits._

class EmbeddedDeploymentManagerRestartTest extends BaseEmbeddedDeploymentManagerTest {

  // This test is in separate suite to make sure that restarting of kafka server have no influence on other test case scenarios
  test("Set status to restarting when scenario fails and back to running when the problems are fixed") {
    val fixture@FixtureParam(manager, _, inputTopic, outputTopic) = prepareFixture()

    val name = ProcessName("testName")
    val scenario = StreamingLiteScenarioBuilder
      .id(name.value)
      .source("source", "kafka-json", "topic" -> s"'$inputTopic'")
      .emptySink("sink", "kafka-json", "topic" -> s"'$outputTopic'", "value" -> "#input")

    wrapInFailingLoader {
      fixture.deployScenario(scenario)
    }

    manager.findJobStatus(name).futureValue.map(_.status) shouldBe Some(SimpleStateStatus.Running)

    kafkaZookeeperServer.kafkaServer.shutdown()
    kafkaZookeeperServer.kafkaServer.awaitShutdown()

    eventually {
      val jobStatus = manager.findJobStatus(name).futureValue
      jobStatus.map(_.status) shouldBe Some(EmbeddedStateStatus.Restarting)
      jobStatus.map(_.allowedActions).get should contain only (ProcessActionType.Cancel)
    }

    kafkaZookeeperServer.kafkaServer.startup()

    eventually {
      manager.findJobStatus(name).futureValue.map(_.status) shouldBe Some(SimpleStateStatus.Running)
    }
  }

}
