package pl.touk.nussknacker.engine.management.streaming

import com.typesafe.config.Config
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.scalalogging.StrictLogging
import io.circe.Json
import io.circe.syntax.EncoderOps
import org.apache.commons.lang3.SystemUtils
import org.apache.flink.api.common.JobID
import org.apache.flink.util.FileUtils
import org.scalatest.Inside.inside
import org.scalatest.Inspectors.forAll
import org.scalatest.funsuite.AnyFunSuiteLike
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.{ModelData, ModelDependencies}
import pl.touk.nussknacker.engine.ModelConfig.LiveDataPreviewMode
import pl.touk.nussknacker.engine.api.{ContextId, NodeId, ProcessVersion}
import pl.touk.nussknacker.engine.api.component.{ComponentId, ComponentType, DesignerWideComponentId}
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.DeploymentUpdateStrategy.StateRestoringStrategy
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessIdWithName, ProcessName, VersionId}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.classloader.ModelClassLoaderFactory
import pl.touk.nussknacker.engine.definition.component.Components.ComponentDefinitionExtractionMode
import pl.touk.nussknacker.engine.deployment.{DeploymentData, DeploymentId, ExternalDeploymentId}
import pl.touk.nussknacker.engine.livedata._

import java.net.URI
import java.nio.file.Files
import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.duration._

class RemoteFlinkDeploymentManagerSpec extends BaseFlinkDeploymentManagerSpec {
  override protected val useMiniClusterForDeployment: Boolean = false
}

class MiniClusterFlinkDeploymentManagerSpec extends BaseFlinkDeploymentManagerSpec {
  override protected val useMiniClusterForDeployment: Boolean = true

  override def resolveProcessingTypeConfig(config: Config): Config = {
    super.resolveProcessingTypeConfig(config).withValue("modelConfig.liveDataPreview.enabled", fromAnyRef(true))
  }

}

trait BaseFlinkDeploymentManagerSpec
    extends AnyFunSuiteLike
    with Matchers
    with FlinkKafkaDockerSpec
    with StrictLogging {

  import pl.touk.nussknacker.engine.kafka.KafkaTestUtils.richConsumer

  override protected def modelClassPath: List[String] = TestModelClassPaths.scalaClasspath

  private val defaultDeploymentData = DeploymentData.empty

  private val processId = ProcessId(765)

  test("deploy scenario in running flink") {
    val processName = ProcessName("runningFlink")

    val version      = VersionId(15)
    val process      = SampleProcess.prepareProcess(processName)
    val deploymentId = DeploymentId("not-a-uuid")

    val externalDeploymentIdOpt = deployProcessAndWaitIfRunning(
      process = process,
      processVersion = ProcessVersion(version, processName, processId, List.empty, "user1", Some(13)),
      deploymentId = deploymentId
    )
    try {
      deploymentStatus(processName) should matchPattern {
        case List(DeploymentStatusDetails(SimpleStateStatus.Running(`version`, _), Some(`deploymentId`))) =>
      }
      externalDeploymentIdOpt shouldBe defined
    } finally {
      cancelProcess(processName)
    }
  }

  test("deploy scenario in running flink with event generator") {
    val processName = ProcessName("runningFlinkEventGenerator")

    val version      = VersionId(15)
    val process      = SampleProcess.prepareProcessWithEventGeneratorSource(processName)
    val deploymentId = DeploymentId("with-event-generator")

    val configuredMaxNumbersOfRecords = 15
    LiveDataCollectingListener.createListenerFor(
      processIdWithName = ProcessIdWithName(processId, processName),
      deploymentIdOpt = None,
      liveDataEnabledConfig = LiveDataPreviewMode.Enabled(
        maxNumberOfRecords = configuredMaxNumbersOfRecords,
        retentionTime = 60 minutes,
        throughputTimeWindow = 60 seconds,
        liveDataStorage = LiveDataPreviewMode.LiveDataStorage.DesignerJvm
      ),
      skipLiveDataUploaderWithReason = None
    )
    val externalDeploymentIdOpt = deployProcessAndWaitIfRunning(
      process = process,
      processVersion = ProcessVersion(version, processName, processId, List.empty, "user1", Some(13)),
      deploymentId = deploymentId
    )
    externalDeploymentIdOpt shouldBe defined
    try {
      deploymentStatus(processName) should matchPattern {
        case List(DeploymentStatusDetails(SimpleStateStatus.Running(`version`, _), Some(`deploymentId`))) =>
      }

      if (useMiniClusterForDeployment) {
        val totalCountWaitLimit = 15
        val liveDataSamples = eventually {
          // Wait until first live data samples are collected
          val liveDataOpt     = LiveDataCollectingListenerStorageHolder.getLiveDataPreview(processName)
          val liveDataSamples = liveDataOpt.value

          // Wait until first 15 live data samples are collected
          liveDataSamples.nodeTransitions
            .get(NodeTransition(NodeId("start"), Some(NodeId("endSend")), isDirectTransition = true))
            .value
            .totalCount should be >= totalCountWaitLimit.toLong
          liveDataSamples
        }

        val nodeTransitions = liveDataSamples.nodeTransitions
          .get(NodeTransition(NodeId("start"), Some(NodeId("endSend")), isDirectTransition = true))
          .value
          .samples
        nodeTransitions.size should be >= totalCountWaitLimit
        nodeTransitions.size should be <= configuredMaxNumbersOfRecords
        forAll(nodeTransitions.zipWithIndex) { case (sample, idx) =>
          sample.contextId shouldBe ContextId(
            scenarioName = processName,
            originatingNodeId = NodeId("start"),
            taskId = 0,
            index = idx
          )
          sample.variables shouldBe Map("input" -> Json.obj("pretty" -> "abrakadabra".asJson))
        }

        val startNodeExpressionEvaluationResults = liveDataSamples.expressionEvaluationResults
          .get(NodeId("start"))
          .value
        val valueEvaluationResults     = startNodeExpressionEvaluationResults.filter(_.name == "value")
        val eventTimeEvaluationResults = startNodeExpressionEvaluationResults.filter(_.name == "Event time")
        // We can exceed `configuredMaxNumbersOfRecords`, because:
        // - `start` node has 2 expressions: `value` and `Event time`
        // - the limit (configuredMaxNumbersOfRecords) is applied separately for each variable
        startNodeExpressionEvaluationResults.size shouldBe configuredMaxNumbersOfRecords * 2
        valueEvaluationResults.size shouldBe configuredMaxNumbersOfRecords
        eventTimeEvaluationResults.size shouldBe configuredMaxNumbersOfRecords

        forAll(valueEvaluationResults) { expressionEvaluationResults =>
          inside(expressionEvaluationResults) {
            case ExpressionEvaluationResult(valueContextId, _, "value", valueJson) =>
              valueContextId.scenarioName shouldBe processName
              valueContextId.originatingNodeId shouldBe NodeId("start")
              valueContextId.taskId shouldBe 0
              valueJson shouldBe Json.obj("pretty" -> "abrakadabra".asJson)
          }
        }
        forAll(eventTimeEvaluationResults) { expressionEvaluationResults =>
          inside(expressionEvaluationResults) {
            case ExpressionEvaluationResult(eventTimeContextId, _, "Event time", _) =>
              eventTimeContextId.scenarioName shouldBe processName
              eventTimeContextId.originatingNodeId shouldBe NodeId("start")
              eventTimeContextId.taskId shouldBe 0
          }
        }
        val endSendInvocationResults = liveDataSamples.expressionEvaluationResults
          .get(NodeId("endSend"))
          .value
        endSendInvocationResults.size should be >= totalCountWaitLimit
        endSendInvocationResults.size should be <= configuredMaxNumbersOfRecords
        forAll(endSendInvocationResults.zipWithIndex) { case (invocationResult, idx) =>
          invocationResult.contextId shouldBe ContextId(
            scenarioName = processName,
            originatingNodeId = NodeId("start"),
            taskId = 0,
            index = idx
          )
          invocationResult.name shouldBe "Value"
          invocationResult.value shouldBe Json.obj("pretty" -> "message".asJson)
        }

        val endSendSinkOutputResults = eventually {
          val sinkOutputResults = LiveDataCollectingListenerStorageHolder
            .getLiveDataPreview(processName)
            .value
            .externalServiceInvocationResults
            .get(NodeId("endSend"))
            .value
          sinkOutputResults.size should be >= totalCountWaitLimit
          sinkOutputResults
        }
        endSendSinkOutputResults.size should be <= configuredMaxNumbersOfRecords
        forAll(endSendSinkOutputResults.zipWithIndex) { case (invocationResult, idx) =>
          invocationResult.contextId shouldBe ContextId(
            scenarioName = processName,
            originatingNodeId = NodeId("start"),
            taskId = 0,
            index = idx
          )
          invocationResult.name shouldBe "endSend"
          invocationResult.value shouldBe Json.obj("pretty" -> "message".asJson)
        }
      }
    } finally {
      cancelProcess(processName)
    }
  }

  test("use deploymentId passed as a jobId") {
    val processName = ProcessName("jobWithDeploymentIdAsAUuid")

    val version          = VersionId(15)
    val process          = SampleProcess.prepareProcess(processName)
    val deploymentIdUuid = UUID.randomUUID()
    val deploymentId     = DeploymentId(deploymentIdUuid.toString)

    val externalDeploymentIdOpt = deployProcessAndWaitIfRunning(
      process = process,
      processVersion = ProcessVersion(version, processName, processId, List.empty, "user1", Some(13)),
      deploymentId = deploymentId
    )
    try {
      deploymentStatus(processName) should matchPattern {
        case List(DeploymentStatusDetails(SimpleStateStatus.Running(`version`, _), Some(`deploymentId`))) =>
      }
      externalDeploymentIdOpt.value shouldBe ExternalDeploymentId(
        new JobID(deploymentIdUuid.getLeastSignificantBits, deploymentIdUuid.getMostSignificantBits).toHexString
      )
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
      .futureValue shouldBe ()
  }

  test("save state when redeploying") {
    val processEmittingOneElementAfterStart = StatefulSampleProcess.prepareProcess(ProcessName("redeploy"))
    testRedeployWithStatefulSampleProcess(processEmittingOneElementAfterStart)
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
    val tempDir = Files.createTempDirectory("customSavepoint")
    try {
      // we wait for first element to appear in kafka to be sure it's processed, before we proceed to checkpoint
      messagesFromTopic(outTopic, 1) shouldBe List("[One element]")

      val customSavepointDir = if (useMiniClusterForDeployment || !SystemUtils.IS_OS_WINDOWS) {
        tempDir.toUri
      } else {
        URI.create(s"file:/tmp/${tempDir.getFileName}")
      }
      val savepointPathFuture = deploymentManager
        .processCommand(
          DMMakeScenarioSavepointCommand(
            processEmittingOneElementAfterStart.name,
            savepointDir = Some(customSavepointDir.toString)
          )
        )
        .map(_.path)
      val savepointPath = new URI(savepointPathFuture.futureValue)
      savepointPath.toString should startWith(customSavepointDir.toURL.toString)

      cancelProcess(processName)
      deployProcessAndWaitIfRunning(
        processEmittingOneElementAfterStart,
        empty(processName),
        stateRestoringStrategy = StateRestoringStrategy.RestoreStateFromCustomSavepoint(savepointPath.toString)
      )

      val messages = messagesFromTopic(outTopic, 2)
      messages shouldBe List("[One element]", "[One element, One element]")
    } finally {
      cancelProcess(processName)
      FileUtils.deleteDirectoryQuietly(tempDir.toFile)
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
        val status = deploymentManager.getScenarioDeploymentsStatuses(processName).futureValue
        status.value.map(_.status) shouldBe List(SimpleStateStatus.Canceled)
      }

      deployProcessAndWaitIfRunning(
        processEmittingOneElementAfterStart,
        empty(processName),
        stateRestoringStrategy = StateRestoringStrategy.RestoreStateFromCustomSavepoint(savepointPath.futureValue)
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

      val statefulProcess = StatefulSampleProcess.prepareProcessWithLongState(processName)
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
        componentDefinitionExtractionMode = ComponentDefinitionExtractionMode.FinalDefinition,
        designerDbRef = None
      ),
      ModelClassLoaderFactory.create(processingTypeConfig.classPath, None, deploymentManagerClassLoader)
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

  private def deploymentStatus(name: ProcessName): List[DeploymentStatusDetails] =
    deploymentManager.getScenarioDeploymentsStatuses(name).futureValue.value

}
