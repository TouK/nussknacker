package pl.touk.nussknacker.engine.management.streaming

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.{ModelData, ModelDependencies}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.component.{ComponentId, ComponentType, DesignerWideComponentId}
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.engine.deployment.DeploymentData

import java.net.URI
import java.nio.file.{Files, Paths}
import scala.concurrent.ExecutionContext.Implicits._

class FlinkStreamingDeploymentManagerSpec extends AnyFunSuite with Matchers with StreamingDockerTest {

  import pl.touk.nussknacker.engine.kafka.KafkaTestUtils.richConsumer

  override protected def classPath: List[String] = ClassPaths.scalaClasspath

  private val defaultDeploymentData = DeploymentData.empty

  private val processId = ProcessId(765)

  test("deploy scenario in running flink") {
    val processName = ProcessName("runningFlink")

    val version = ProcessVersion(VersionId(15), processName, processId, "user1", Some(13))
    val process = SampleProcess.prepareProcess(processName)

    deployProcessAndWaitIfRunning(process, version)
    try {
      processVersion(processName) shouldBe List(version)
    } finally {
      cancelProcess(processName)
    }
  }

  // manual test because it is hard to make it automatic
  // to run this test you have to add Thread.sleep(over 1 minute) to FlinkProcessMain.main method
  ignore("continue on timeout exception during scenario deploy") {
    val processName = ProcessName("runningFlink")
    val process     = SampleProcess.prepareProcess(processName)
    val version     = ProcessVersion(VersionId(15), processName, processId, "user1", Some(13))

    val deployedResponse = deploymentManager.deploy(version, defaultDeploymentData, process, None)

    deployedResponse.futureValue
  }

  // this is for the case where e.g. we manually cancel flink job, or it fail and didn't restart...
  test("cancel of not existing job should not fail") {
    deploymentManager.cancel(ProcessName("not existing job"), user = userToAct).futureValue shouldBe (())
  }

  test("be able verify&redeploy kafka scenario") {
    val processName  = ProcessName("verifyAndRedeploy")
    val outTopic     = s"output-$processName"
    val inTopic      = s"input-$processName"
    val kafkaProcess = SampleProcess.kafkaProcess(processName, inTopic)

    kafkaClient.createTopic(outTopic, 1)
    kafkaClient.createTopic(inTopic, 1)
    logger.info("Kafka topics created, deploying scenario")

    deployProcessAndWaitIfRunning(kafkaProcess, empty(processName))
    try {
      kafkaClient.sendMessage(inTopic, "1").futureValue
      messagesFromTopic(outTopic, 1).head shouldBe "1"

      deployProcessAndWaitIfRunning(kafkaProcess, empty(processName))

      kafkaClient.sendMessage(inTopic, "2").futureValue
      messagesFromTopic(outTopic, 2).last shouldBe "2"
    } finally {
      cancelProcess(kafkaProcess.name)
    }
  }

  test("save state when redeploying") {
    val processName                         = ProcessName("redeploy")
    val outTopic                            = s"output-$processName"
    val processEmittingOneElementAfterStart = StatefulSampleProcess.prepareProcess(processName)

    kafkaClient.createTopic(outTopic, 1)

    deployProcessAndWaitIfRunning(processEmittingOneElementAfterStart, empty(processName))
    try {
      // we wait for first element to appear in kafka to be sure it's processed, before we proceed to checkpoint
      messagesFromTopic(outTopic, 1) shouldBe List("List(One element)")

      deployProcessAndWaitIfRunning(processEmittingOneElementAfterStart, empty(processName))

      val messages = messagesFromTopic(outTopic, 2)
      messages shouldBe List("List(One element)", "List(One element, One element)")
    } finally {
      cancelProcess(processName)
    }
  }

  test("snapshot state and be able to deploy using it") {
    val processName                         = ProcessName("snapshot")
    val outTopic                            = s"output-$processName"
    val processEmittingOneElementAfterStart = StatefulSampleProcess.prepareProcess(processName)

    kafkaClient.createTopic(outTopic, 1)

    deployProcessAndWaitIfRunning(processEmittingOneElementAfterStart, empty(processName))
    try {
      // we wait for first element to appear in kafka to be sure it's processed, before we proceed to checkpoint
      messagesFromTopic(outTopic, 1) shouldBe List("List(One element)")

      val savepointDir = Files.createTempDirectory("customSavepoint")
      val savepointPathFuture = deploymentManager
        .savepoint(
          processEmittingOneElementAfterStart.name,
          savepointDir = Some(savepointDir.toUri.toString)
        )
        .map(_.path)
      val savepointPath = new URI(savepointPathFuture.futureValue)
      Paths.get(savepointPath).startsWith(savepointDir) shouldBe true

      cancelProcess(processName)
      deployProcessAndWaitIfRunning(
        processEmittingOneElementAfterStart,
        empty(processName),
        Some(savepointPath.toString)
      )

      val messages = messagesFromTopic(outTopic, 2)
      messages shouldBe List("List(One element)", "List(One element, One element)")
    } finally {
      cancelProcess(processName)
    }
  }

  test("should stop scenario and deploy it using savepoint") {
    val processName                         = ProcessName("stop")
    val outTopic                            = s"output-$processName"
    val processEmittingOneElementAfterStart = StatefulSampleProcess.prepareProcess(processName)

    kafkaClient.createTopic(outTopic, 1)

    deployProcessAndWaitIfRunning(processEmittingOneElementAfterStart, empty(processName))
    try {
      messagesFromTopic(outTopic, 1) shouldBe List("List(One element)")

      val savepointPath =
        deploymentManager.stop(processName, savepointDir = None, user = userToAct).map(_.path)
      eventually {
        val status = deploymentManager.getProcessStates(processName).futureValue
        status.value.map(_.status) shouldBe List(SimpleStateStatus.Canceled)
      }

      deployProcessAndWaitIfRunning(
        processEmittingOneElementAfterStart,
        empty(processName),
        Some(savepointPath.futureValue)
      )

      val messages = messagesFromTopic(outTopic, 2)
      messages shouldBe List("List(One element)", "List(One element, One element)")
    } finally {
      cancelProcess(processName)
    }
  }

  test("fail to redeploy if old is incompatible") {
    val processName = ProcessName("redeployFail")
    val outTopic    = s"output-$processName"
    val process     = StatefulSampleProcess.prepareProcessStringWithStringState(processName)

    kafkaClient.createTopic(outTopic, 1)

    deployProcessAndWaitIfRunning(process, empty(process.name))
    try {
      messagesFromTopic(outTopic, 1) shouldBe List("")

      logger.info("Starting to redeploy")

      val statefullProcess = StatefulSampleProcess.prepareProcessWithLongState(processName)
      val exception =
        deploymentManager.deploy(empty(process.name), defaultDeploymentData, statefullProcess, None).failed.futureValue
      exception.getMessage shouldBe "State is incompatible, please stop scenario and start again with clean state"
    } finally {
      cancelProcess(processName)
    }
  }

  test("fail to redeploy if result produced by aggregation is incompatible") {
    val processName = ProcessName("redeployFailAggregator")
    val outTopic    = s"output-$processName"
    val process     = StatefulSampleProcess.processWithAggregator(processName, "#AGG.set")

    kafkaClient.createTopic(outTopic, 1)

    deployProcessAndWaitIfRunning(process, empty(process.name))
    try {
      messagesFromTopic(outTopic, 1) shouldBe List("test")

      logger.info("Starting to redeploy")

      val statefulProcess = StatefulSampleProcess.processWithAggregator(processName, "#AGG.approxCardinality")
      val exception =
        deploymentManager.deploy(empty(process.name), defaultDeploymentData, statefulProcess, None).failed.futureValue
      exception.getMessage shouldBe "State is incompatible, please stop scenario and start again with clean state"
    } finally {
      cancelProcess(processName)
    }
  }

  def empty(processName: ProcessName): ProcessVersion = ProcessVersion.empty.copy(processName = processName)

  test("extract scenario definition") {
    val modelData = ModelData(
      processingTypeConfig = processingTypeConfig,
      ModelDependencies(
        additionalConfigsFromProvider = Map.empty,
        determineDesignerWideId = id => DesignerWideComponentId(id.toString),
        workingDirectoryOpt = None,
        _ => true
      )
    )
    val definition = modelData.modelDefinition
    definition.components.map(_.id) should contain(ComponentId(ComponentType.Service, "accountService"))
  }

  private def messagesFromTopic(outTopic: String, count: Int): List[String] =
    kafkaClient
      .createConsumer()
      .consumeWithJson[String](outTopic)
      .take(count)
      .map(_.message())
      .toList

  private def processVersion(name: ProcessName): List[ProcessVersion] =
    deploymentManager.getProcessStates(name).futureValue.value.flatMap(_.version)
}
