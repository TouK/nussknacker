package pl.touk.nussknacker.engine.management.streaming

import org.apache.kafka.clients.producer.ProducerRecord
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.DataFreshnessPolicy
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.management.FlinkStateStatus

import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import java.util.concurrent.TimeUnit
import scala.concurrent.ExecutionContext.Implicits._

class FlinkStreamingDeploymentManagerSpec extends AnyFunSuite with Matchers with StreamingDockerTest {

  import pl.touk.nussknacker.engine.kafka.KafkaTestUtils._

  override protected def classPath: List[String] = ClassPaths.scalaClasspath

  private val defaultDeploymentData = DeploymentData.empty

  private val processId = ProcessId(765)

  test("deploy scenario in running flink") {
    val processName = "runningFlink"

    val version = ProcessVersion(VersionId(15), ProcessName(processName), processId, "user1", Some(13))
    val process = SampleProcess.prepareProcess(processName)

    deployProcessAndWaitIfRunning(process, version)
    try {
      processVersion(ProcessName(processName)) shouldBe Some(version)
    } finally {
      cancelProcess(processName)
    }
  }

  //manual test because it is hard to make it automatic
  //to run this test you have to add Thread.sleep(over 1 minute) to FlinkProcessMain.main method
  ignore("continue on timeout exception during scenario deploy") {
    val processName = "runningFlink"
    val process = SampleProcess.prepareProcess(processName)
    val version = ProcessVersion(VersionId(15), ProcessName(processName), processId, "user1", Some(13))

    val deployedResponse = deploymentManager.deploy(version, defaultDeploymentData, process, None)

    deployedResponse.futureValue
  }

  //this is for the case where e.g. we manually cancel flink job, or it fail and didn't restart...
  test("cancel of not existing job should not fail") {
    deploymentManager.cancel(ProcessName("not existing job"), user = userToAct).futureValue shouldBe (())
  }

  test("be able verify&redeploy kafka scenario") {
    val processId = "verifyAndRedeploy"
    val outTopic = s"output-$processId"
    val inTopic = s"input-$processId"
    val kafkaProcess = SampleProcess.kafkaProcess(processId, inTopic)

    kafkaClient.createTopic(outTopic, 1)
    kafkaClient.createTopic(inTopic, 1)
    logger.info("Kafka topics created, deploying scenario")

    deployProcessAndWaitIfRunning(kafkaProcess, empty(processId))
    try {
      kafkaClient.producer.send(new ProducerRecord[String, String](inTopic, "1")).get(30, TimeUnit.SECONDS)
      messagesFromTopic(outTopic, 1).head shouldBe "1"

      deployProcessAndWaitIfRunning(kafkaProcess, empty(processId))

      kafkaClient.producer.send(new ProducerRecord[String, String](inTopic, "2")).get(30, TimeUnit.SECONDS)
      messagesFromTopic(outTopic, 2).last shouldBe "2"
    } finally {
      cancelProcess(kafkaProcess.id)
    }
  }

  test("save state when redeploying") {
    val processId = "redeploy"
    val outTopic = s"output-$processId"
    val processEmittingOneElementAfterStart = StatefulSampleProcess.prepareProcess(processId)

    kafkaClient.createTopic(outTopic, 1)

    deployProcessAndWaitIfRunning(processEmittingOneElementAfterStart, empty(processId))
    try {
      //we wait for first element to appear in kafka to be sure it's processed, before we proceed to checkpoint
      messagesFromTopic(outTopic, 1) shouldBe List("List(One element)")

      deployProcessAndWaitIfRunning(processEmittingOneElementAfterStart, empty(processId))

      val messages = messagesFromTopic(outTopic, 2)
      messages shouldBe List("List(One element)", "List(One element, One element)")
    } finally {
      cancelProcess(processId)
    }
  }

  test("snapshot state and be able to deploy using it") {
    val processId = "snapshot"
    val outTopic = s"output-$processId"
    val processEmittingOneElementAfterStart = StatefulSampleProcess.prepareProcess(processId)

    kafkaClient.createTopic(outTopic, 1)

    deployProcessAndWaitIfRunning(processEmittingOneElementAfterStart, empty(processId))
    try {
      //we wait for first element to appear in kafka to be sure it's processed, before we proceed to checkpoint
      messagesFromTopic(outTopic, 1) shouldBe List("List(One element)")

      val savepointDir = Files.createTempDirectory("customSavepoint")
      val savepointPathFuture = deploymentManager.savepoint(ProcessName(processEmittingOneElementAfterStart.id), savepointDir = Some(savepointDir.toUri.toString))
        .map(_.path)
      val savepointPath = new URI(savepointPathFuture.futureValue)
      Paths.get(savepointPath).startsWith(savepointDir) shouldBe true

      cancelProcess(processId)
      deployProcessAndWaitIfRunning(processEmittingOneElementAfterStart, empty(processId), Some(savepointPath.toString))

      val messages = messagesFromTopic(outTopic, 2)
      messages shouldBe List("List(One element)", "List(One element, One element)")
    } finally {
      cancelProcess(processId)
    }
  }

  test("should stop scenario and deploy it using savepoint") {
    val processId = "stop"
    val outTopic = s"output-$processId"
    val processEmittingOneElementAfterStart = StatefulSampleProcess.prepareProcess(processId)

    kafkaClient.createTopic(outTopic, 1)

    deployProcessAndWaitIfRunning(processEmittingOneElementAfterStart, empty(processId))
    try {
      messagesFromTopic(outTopic, 1) shouldBe List("List(One element)")

      val savepointPath = deploymentManager.stop(ProcessName(processId), savepointDir = None, user = userToAct).map(_.path)
      eventually {
        val status = deploymentManager.getProcessState(ProcessName(processId)).futureValue
        status.value.map(_.status) shouldBe Some(FlinkStateStatus.Finished)
      }

      deployProcessAndWaitIfRunning(processEmittingOneElementAfterStart, empty(processId), Some(savepointPath.futureValue))

      messagesFromTopic(outTopic, 2) shouldBe List("List(One element)", "List(One element, One element)")
    } finally {
      cancelProcess(processId)
    }
  }

  test("fail to redeploy if old is incompatible") {
    val processId = "redeployFail"
    val outTopic = s"output-$processId"
    val process = StatefulSampleProcess.prepareProcessStringWithStringState(processId)

    kafkaClient.createTopic(outTopic, 1)

    deployProcessAndWaitIfRunning(process, empty(process.id))
    try {
      messagesFromTopic(outTopic, 1) shouldBe List("")

      logger.info("Starting to redeploy")

      val statefullProcess = StatefulSampleProcess.prepareProcessWithLongState(processId)
      val exception = deploymentManager.deploy(empty(process.id), defaultDeploymentData, statefullProcess, None).failed.futureValue
      exception.getMessage shouldBe "State is incompatible, please stop scenario and start again with clean state"
    } finally {
      cancelProcess(processId)
    }
  }

  test("fail to redeploy if result produced by aggregation is incompatible") {
    val processId = "redeployFailAggregator"
    val outTopic = s"output-$processId"
    val process = StatefulSampleProcess.processWithAggregator(processId, "#AGG.set")

    kafkaClient.createTopic(outTopic, 1)

    deployProcessAndWaitIfRunning(process, empty(process.id))
    try {
      messagesFromTopic(outTopic, 1) shouldBe List("test")

      logger.info("Starting to redeploy")

      val statefulProcess = StatefulSampleProcess.processWithAggregator(processId, "#AGG.approxCardinality")
      val exception = deploymentManager.deploy(empty(process.id), defaultDeploymentData, statefulProcess, None).failed.futureValue
      exception.getMessage shouldBe "State is incompatible, please stop scenario and start again with clean state"
    } finally {
      cancelProcess(processId)
    }
  }

  def empty(processId: String): ProcessVersion = ProcessVersion.empty.copy(processName = ProcessName(processId))

  test("extract scenario definition") {
    val modelData = ModelData(processingTypeConfig)
    val definition = modelData.processDefinition
    definition.services should contain key "accountService"
  }

  private def messagesFromTopic(outTopic: String, count: Int): List[String] = {
    kafkaClient.createConsumer()
      .consume(outTopic)
      .map(_.message()).map(new String(_, StandardCharsets.UTF_8))
      .take(count).toList
  }

  private def processVersion(processId: ProcessName): Option[ProcessVersion] =
    deploymentManager.getProcessState(processId).futureValue.value.flatMap(_.version)
}
