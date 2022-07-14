package pl.touk.nussknacker.streaming.embedded

import pl.touk.nussknacker.engine.api.deployment.ProcessActionType
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.spel.Implicits._

class StreamingEmbeddedDeploymentManagerRestartTest extends BaseStreamingEmbeddedDeploymentManagerTest {

  // TODO: after switch to kafka-raft started failing with "maybeBalancePartitionLeaders: unable to start processing because of TimeoutException"
  // This test is in separate suite to make sure that restarting of kafka server have no influence on other test case scenarios
  ignore("Set status to restarting when scenario fails and back to running when the problems are fixed") {
    val fixture@FixtureParam(manager, _, inputTopic, outputTopic) = prepareFixture()

    val name = ProcessName("testName")
    val scenario = ScenarioBuilder
      .streamingLite(name.value)
      .source("source", "kafka-json", "topic" -> s"'$inputTopic'")
      .emptySink("sink", "kafka-json", "topic" -> s"'$outputTopic'", "value" -> "#input")

    wrapInFailingLoader {
      fixture.deployScenario(scenario)
    }

    // wait until error topic created
    Thread.sleep(5000)
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
