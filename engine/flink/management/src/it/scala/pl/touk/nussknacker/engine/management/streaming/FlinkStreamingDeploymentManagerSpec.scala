package pl.touk.nussknacker.engine.management.streaming

import com.typesafe.scalalogging.StrictLogging
import org.scalatest.funsuite.AnyFunSuiteLike
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.component.{ComponentId, ComponentType, DesignerWideComponentId}
import pl.touk.nussknacker.engine.api.deployment.DeploymentUpdateStrategy.StateRestoringStrategy
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.definition.component.Components.ComponentDefinitionExtractionMode
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.flink.minicluster.FlinkMiniClusterFactory
import pl.touk.nussknacker.engine.util.loader.ModelClassLoader
import pl.touk.nussknacker.engine.{ModelData, ModelDependencies}

import java.net.URI
import java.nio.file.{Files, Paths}
import scala.concurrent.ExecutionContext.Implicits._

class RemoteFlinkStreamingDeploymentManagerSpec extends BaseFlinkStreamingDeploymentManagerSpec {
  override protected def useMiniClusterForDeployment: Boolean = false
}

class MiniClusterFlinkStreamingDeploymentManagerSpec extends BaseFlinkStreamingDeploymentManagerSpec {
  override protected def useMiniClusterForDeployment: Boolean = true
}

trait BaseFlinkStreamingDeploymentManagerSpec
    extends AnyFunSuiteLike
    with Matchers
    with StreamingDockerTest
    with StrictLogging {

  import pl.touk.nussknacker.engine.kafka.KafkaTestUtils.richConsumer

  override protected def modelClassPath: List[String] = TestModelClassPaths.scalaClasspath

  private val defaultDeploymentData = DeploymentData.empty

  private val processId = ProcessId(765)

  test("deploy scenario in running flink") {
    val processName = ProcessName("runningFlink")

    val version = ProcessVersion(VersionId(15), processName, processId, List.empty, "user1", Some(13))
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
    val version     = ProcessVersion(VersionId(15), processName, processId, List.empty, "user1", Some(13))

    val deployedResponse =
      deploymentManager.processCommand(
        DMRunDeploymentCommand(
          version,
          defaultDeploymentData,
          process,
          DeploymentUpdateStrategy.ReplaceDeploymentWithSameScenarioName(
            StateRestoringStrategy.RestoreStateFromReplacedJobSavepoint
          )
        )
      )

    deployedResponse.futureValue
  }

  // this is for the case where e.g. we manually cancel flink job, or it fails and didn't restart...
  test("cancel of not existing job should not fail") {
    deploymentManager
      .processCommand(DMCancelScenarioCommand(ProcessName("not existing job"), user = userToAct))
      .futureValue shouldBe (())
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
    val processEmittingOneElementAfterStart = StatefulSampleProcess.prepareProcess(ProcessName("redeploy"))
    testRedeployWithStatefulSampleProcess(processEmittingOneElementAfterStart)
  }

  test("redeploy scenario with greater parallelism than configured in mini cluster") {
    // For useMiniClusterForDeployment mode, this test has no sense
    if (!useMiniClusterForDeployment) {
      val greaterParallelism = FlinkMiniClusterFactory.DefaultTaskSlots + 1
      val processEmittingOneElementAfterStart =
        StatefulSampleProcess.prepareProcess(
          ProcessName(s"redeploy-parallelism-$greaterParallelism"),
          parallelism = greaterParallelism
        )
      testRedeployWithStatefulSampleProcess(processEmittingOneElementAfterStart)
    }
  }

  private def testRedeployWithStatefulSampleProcess(processEmittingOneElementAfterStart: CanonicalProcess) = {
    val outTopic = s"output-${processEmittingOneElementAfterStart.name}"

    kafkaClient.createTopic(outTopic, 1)

    deployProcessAndWaitIfRunning(processEmittingOneElementAfterStart, empty(processEmittingOneElementAfterStart.name))
    try {
      // we wait for first element to appear in kafka to be sure it's processed, before we proceed to checkpoint
      messagesFromTopic(outTopic, 1) shouldBe List("[One element]")

      deployProcessAndWaitIfRunning(
        processEmittingOneElementAfterStart,
        empty(processEmittingOneElementAfterStart.name)
      )

      val messages = messagesFromTopic(outTopic, 2)
      messages shouldBe List("[One element]", "[One element, One element]")
    } finally {
      cancelProcess(processEmittingOneElementAfterStart.name)
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
      messagesFromTopic(outTopic, 1) shouldBe List("[One element]")

      val savepointDir = Files.createTempDirectory("customSavepoint")
      val savepointPathFuture = deploymentManager
        .processCommand(
          DMMakeScenarioSavepointCommand(
            processEmittingOneElementAfterStart.name,
            savepointDir = Some(savepointDir.toUri.toString)
          )
        )
        .map(_.path)
      val savepointPath = new URI(savepointPathFuture.futureValue)
      Paths.get(savepointPath).startsWith(savepointDir) shouldBe true

      cancelProcess(processName)
      deployProcessAndWaitIfRunning(
        processEmittingOneElementAfterStart,
        empty(processName),
        StateRestoringStrategy.RestoreStateFromCustomSavepoint(savepointPath.toString)
      )

      val messages = messagesFromTopic(outTopic, 2)
      messages shouldBe List("[One element]", "[One element, One element]")
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
      messagesFromTopic(outTopic, 1) shouldBe List("[One element]")

      val savepointPath =
        deploymentManager
          .processCommand(DMStopScenarioCommand(processName, savepointDir = None, user = userToAct))
          .map(_.path)
      eventually {
        val status = deploymentManager.getProcessStates(processName).futureValue
        status.value.map(_.status) shouldBe List(SimpleStateStatus.Canceled)
      }

      deployProcessAndWaitIfRunning(
        processEmittingOneElementAfterStart,
        empty(processName),
        StateRestoringStrategy.RestoreStateFromCustomSavepoint(savepointPath.futureValue)
      )

      val messages = messagesFromTopic(outTopic, 2)
      messages shouldBe List("[One element]", "[One element, One element]")
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
        deploymentManager
          .processCommand(
            DMRunDeploymentCommand(
              empty(process.name),
              defaultDeploymentData,
              statefullProcess,
              DeploymentUpdateStrategy.ReplaceDeploymentWithSameScenarioName(
                StateRestoringStrategy.RestoreStateFromReplacedJobSavepoint
              )
            )
          )
          .failed
          .futureValue
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
        deploymentManager
          .processCommand(
            DMRunDeploymentCommand(
              empty(process.name),
              defaultDeploymentData,
              statefulProcess,
              DeploymentUpdateStrategy.ReplaceDeploymentWithSameScenarioName(
                StateRestoringStrategy.RestoreStateFromReplacedJobSavepoint
              )
            )
          )
          .failed
          .futureValue
      exception.getMessage shouldBe "State is incompatible, please stop scenario and start again with clean state"
    } finally {
      cancelProcess(processName)
    }
  }

  test("extract scenario definition") {
    val modelData = ModelData(
      processingTypeConfig = processingTypeConfig,
      ModelDependencies(
        additionalConfigsFromProvider = Map.empty,
        determineDesignerWideId = id => DesignerWideComponentId(id.toString),
        workingDirectoryOpt = None,
        ComponentDefinitionExtractionMode.FinalDefinition
      ),
      ModelClassLoader(processingTypeConfig.classPath, None, deploymentManagerClassLoader)
    )
    val definition = modelData.modelDefinition
    definition.components.components.map(_.id) should contain(ComponentId(ComponentType.Service, "accountService"))
  }

  def empty(processName: ProcessName): ProcessVersion = ProcessVersion.empty.copy(processName = processName)

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
