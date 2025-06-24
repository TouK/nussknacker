package pl.touk.nussknacker.engine.management.streaming

import com.typesafe.scalalogging.StrictLogging
import io.circe.Json
import io.circe.syntax.EncoderOps
import org.apache.flink.api.common.JobID
import org.scalatest.funsuite.AnyFunSuiteLike
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.{ModelData, ModelDependencies}
import pl.touk.nussknacker.engine.api.{ContextId, NodeId, ProcessVersion}
import pl.touk.nussknacker.engine.api.component.{ComponentId, ComponentType, DesignerWideComponentId}
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.DeploymentUpdateStrategy.StateRestoringStrategy
import pl.touk.nussknacker.engine.api.deployment.LiveDataPreviewSupported._
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.classloader.ModelClassLoaderFactory
import pl.touk.nussknacker.engine.definition.component.Components.ComponentDefinitionExtractionMode
import pl.touk.nussknacker.engine.deployment.{DeploymentData, DeploymentId, ExternalDeploymentId}
import pl.touk.nussknacker.engine.flink.minicluster.FlinkMiniClusterFactory
import pl.touk.nussknacker.engine.livedata.LiveDataCollectingListenerHolder

import java.net.URI
import java.nio.file.{Files, Paths}
import java.time.Instant
import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits._

class RemoteFlinkDeploymentManagerSpec extends BaseFlinkDeploymentManagerSpec {
  override protected def useMiniClusterForDeployment: Boolean = false
}

class MiniClusterFlinkDeploymentManagerSpec extends BaseFlinkDeploymentManagerSpec {
  override protected def useMiniClusterForDeployment: Boolean = true
}

trait BaseFlinkDeploymentManagerSpec extends AnyFunSuiteLike with Matchers with StreamingDockerTest with StrictLogging {

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

    LiveDataCollectingListenerHolder.createListenerFor(
      processName = processName,
      maxNumberOfRecords = 20,
      throughputTimeWindowInSeconds = 60
    )
    val externalDeploymentIdOpt = deployProcessAndWaitIfRunning(
      process = process,
      processVersion = ProcessVersion(version, processName, processId, List.empty, "user1", Some(13)),
      deploymentId = deploymentId
    )
    try {
      deploymentStatus(processName) should matchPattern {
        case List(DeploymentStatusDetails(SimpleStateStatus.Running(`version`, _), Some(`deploymentId`))) =>
      }

      eventually {
        if (useMiniClusterForDeployment) {
          // Wait until first live data samples are collected
          val liveDataOpt = LiveDataCollectingListenerHolder.getLiveDataPreview(processName).toOption
          liveDataOpt shouldBe defined
          val liveDataSamples = liveDataOpt.get

          // Wait until first 15 live data samples are collected
          liveDataSamples.nodeTransitions
            .get(NodeTransition("start", Some("endSend")))
            .map(_.samples.size) shouldBe Some(15)

          val (liveDataWithMockedTimestamp, mockedTimestamp) = withFixedTimestamp(liveDataSamples)

          externalDeploymentIdOpt shouldBe defined
          val expected = LiveData(
            timestamp = mockedTimestamp,
            nodeTransitions = Map(
              NodeTransition("start", Some("endSend")) ->
                LiveDataForNodeTransition(
                  samples = (0 to 14).map { idx =>
                    LiveDataSample(
                      ContextId(
                        scenarioId = "runningFlinkEventGenerator",
                        originatingNodeId = "start",
                        taskId = 0,
                        index = idx
                      ),
                      mockedTimestamp,
                      Map("input" -> Json.obj("pretty" -> "abrakadabra".asJson)),
                    )
                  }.toList,
                  totalCount = 15,
                  currentThroughput = 1
                )
            ),
            invocationResults = Map(
              NodeId("start") ->
                (0 to 14).map { idx =>
                  InvocationResult(
                    ContextId(
                      scenarioId = "runningFlinkEventGenerator",
                      originatingNodeId = "start",
                      taskId = 0,
                      index = idx
                    ),
                    mockedTimestamp,
                    "value",
                    Json.obj("pretty" -> "abrakadabra".asJson)
                  )
                }.toList,
              NodeId("endSend") ->
                (0 to 14).map { idx =>
                  InvocationResult(
                    ContextId(
                      scenarioId = "runningFlinkEventGenerator",
                      originatingNodeId = "start",
                      taskId = 0,
                      index = idx
                    ),
                    mockedTimestamp,
                    "Value",
                    Json.obj("pretty" -> "message".asJson)
                  )
                }.toList,
            ),
            externalInvocationResults = Map.empty,
            exceptions = Map.empty,
          )
          liveDataWithMockedTimestamp shouldBe expected
        }
        externalDeploymentIdOpt shouldBe defined
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
      .futureValue shouldBe (())
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
        stateRestoringStrategy = StateRestoringStrategy.RestoreStateFromCustomSavepoint(savepointPath.toString)
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

  private def withFixedTimestamp(testResults: LiveData): (LiveData, Instant) = {
    val fixedInstant = Instant.now
    (
      LiveData(
        timestamp = fixedInstant,
        nodeTransitions = withFixedTimestamp(testResults.nodeTransitions, fixedInstant),
        invocationResults = withFixedTimestamp[NodeId, InvocationResult](
          testResults.invocationResults,
          fixedInstant,
          withFixedTimestamp
        ),
        externalInvocationResults = withFixedTimestamp[NodeId, InvocationResult](
          testResults.externalInvocationResults,
          fixedInstant,
          withFixedTimestamp
        ),
        exceptions = withFixedTimestamp[NodeId, ExceptionResult](
          testResults.exceptions,
          fixedInstant,
          withFixedTimestamp
        ),
      ),
      fixedInstant
    )
  }

  private def withFixedTimestamp[K, V](
      results: Map[K, List[V]],
      fixedTimestamp: Instant,
      withFixedTimestamp: (V, Instant) => V
  ): Map[K, List[V]] = {
    results.map { case (k, v) => (k, v.map(withFixedTimestamp(_, fixedTimestamp))) }
  }

  private def withFixedTimestamp(
      exceptionResult: ExceptionResult,
      fixedTimestamp: Instant
  ): ExceptionResult = {
    ExceptionResult(
      exceptionResult.contextId,
      fixedTimestamp,
      exceptionResult.variables,
      exceptionResult.throwable,
    )
  }

  private def withFixedTimestamp(result: InvocationResult, fixedTimestamp: Instant): InvocationResult = {
    InvocationResult(
      result.contextId,
      fixedTimestamp,
      result.name,
      result.value
    )
  }

  private def withFixedTimestamp[K](
      results: Map[K, LiveDataForNodeTransition],
      fixedTimestamp: Instant
  ): Map[K, LiveDataForNodeTransition] = {
    results.map { case (k, v) => (k, withFixedTimestamp(v, fixedTimestamp)) }
  }

  private def withFixedTimestamp(
      data: LiveDataForNodeTransition,
      fixedTimestamp: Instant
  ): LiveDataForNodeTransition = {
    LiveDataForNodeTransition(
      data.samples.map(withFixedTimestamp(_, fixedTimestamp)),
      data.totalCount,
      data.currentThroughput,
    )
  }

  private def withFixedTimestamp(sample: LiveDataSample, fixedTimestamp: Instant): LiveDataSample = {
    LiveDataSample(sample.contextId, fixedTimestamp, sample.variables)
  }

}
