package pl.touk.nussknacker.ui.process.periodic.flink

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.{Inside, OptionValues}
import pl.touk.nussknacker.engine.api.deployment.DeploymentUpdateStrategy.StateRestoringStrategy
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus.ProblemStateStatus
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessIdWithName, ProcessName, VersionId}
import pl.touk.nussknacker.engine.api.{MetaData, ProcessVersion, StreamMetaData}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.{DeploymentData, User}
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.process.periodic.PeriodicProcessService.PeriodicProcessStatus
import pl.touk.nussknacker.ui.process.periodic.PeriodicStateStatus.{ScheduledStatus, WaitingForScheduleStatus}
import pl.touk.nussknacker.ui.process.periodic._
import pl.touk.nussknacker.ui.process.periodic.flink.db.InMemPeriodicProcessesManager
import pl.touk.nussknacker.ui.process.periodic.model.PeriodicProcessDeploymentStatus
import pl.touk.nussknacker.ui.process.periodic.service.{DefaultAdditionalDeploymentDataProvider, EmptyListener, ProcessConfigEnricher}

import java.time.{Clock, LocalDateTime, ZoneOffset}
import java.util.UUID

class PeriodicDeploymentManagerTest
    extends AnyFunSuite
    with Matchers
    with ScalaFutures
    with OptionValues
    with Inside
    with TableDrivenPropertyChecks
    with PatientScalaFutures {

  protected implicit val freshnessPolicy: DataFreshnessPolicy = DataFreshnessPolicy.Fresh

  import org.scalatest.LoneElement._

  import scala.concurrent.ExecutionContext.Implicits.global

  private val processName = ProcessName("test1")
  private val processId   = ProcessId(1)
  private val idWithName  = ProcessIdWithName(processId, processName)

  private val updateStrategy = DeploymentUpdateStrategy.ReplaceDeploymentWithSameScenarioName(
    StateRestoringStrategy.RestoreStateFromReplacedJobSavepoint
  )

  private val processVersion = ProcessVersion(
    versionId = VersionId(42L),
    processName = processName,
    processId = processId,
    labels = List.empty,
    user = "test user",
    modelVersion = None
  )

  class Fixture(executionConfig: PeriodicExecutionConfig = PeriodicExecutionConfig()) {
    val manager                       = new InMemPeriodicProcessesManager(processingType = "testProcessingType")
    val delegateDeploymentManagerStub = new DeploymentManagerStub
    val periodicDeploymentHandlerStub = new PeriodicDeploymentHandlerStub
    val preparedDeploymentData        = DeploymentData.withDeploymentId(UUID.randomUUID().toString)

    val periodicProcessService = new PeriodicProcessService(
      delegateDeploymentManager = delegateDeploymentManagerStub,
      periodicDeploymentHandler = periodicDeploymentHandlerStub,
      periodicProcessesManager = manager,
      periodicProcessListener = EmptyListener,
      additionalDeploymentDataProvider = DefaultAdditionalDeploymentDataProvider,
      deploymentRetryConfig = DeploymentRetryConfig(),
      executionConfig = executionConfig,
      maxFetchedPeriodicScenarioActivities = None,
      processConfigEnricher = ProcessConfigEnricher.identity,
      clock = Clock.systemDefaultZone(),
      new ProcessingTypeActionServiceStub,
      Map.empty,
    )

    val periodicDeploymentManager = new PeriodicDeploymentManager(
      delegate = delegateDeploymentManagerStub,
      service = periodicProcessService,
      periodicProcessesManager = manager,
      schedulePropertyExtractor = CronSchedulePropertyExtractor(),
      toClose = () => (),
    )

    def getAllowedActions(
        statusDetails: StatusDetails,
        latestVersionId: VersionId,
        deployedVersionId: Option[VersionId],
        currentlyPresentedVersionId: Option[VersionId],
    ): List[ScenarioActionName] = {
      periodicDeploymentManager.processStateDefinitionManager
        .processState(statusDetails, latestVersionId, deployedVersionId, currentlyPresentedVersionId)
        .allowedActions
    }

    def getMergedStatusDetails: StatusDetails =
      periodicProcessService
        .getStatusDetails(processName)
        .futureValue
        .value
        .status
        .asInstanceOf[PeriodicProcessStatus]
        .mergedStatusDetails

  }

  test("getProcessState - should return not deployed for no job") {
    val f = new Fixture

    val state = f.getMergedStatusDetails.status

    state shouldEqual SimpleStateStatus.NotDeployed
  }

  test("getProcessState - should return not deployed for scenario with different processing type") {
    val f = new Fixture
    f.manager.addActiveProcess(processName, PeriodicProcessDeploymentStatus.Scheduled, processingType = "other")

    val state = f.getMergedStatusDetails.status

    state shouldEqual SimpleStateStatus.NotDeployed
  }

  test("getProcessState - should be scheduled when scenario scheduled and no job on Flink") {
    val f = new Fixture
    f.manager.addActiveProcess(processName, PeriodicProcessDeploymentStatus.Scheduled)

    val statusDetails = f.getMergedStatusDetails
    statusDetails.status shouldBe a[ScheduledStatus]
    f.getAllowedActions(statusDetails, processVersion.versionId, None, Some(processVersion.versionId)) shouldBe List(
      ScenarioActionName.Cancel,
      ScenarioActionName.Deploy
    )
    f.periodicDeploymentManager
      .getProcessState(
        idWithName,
        None,
        processVersion.versionId,
        Some(processVersion.versionId),
        Some(processVersion.versionId)
      )
      .futureValue
      .value
      .status shouldBe a[ScheduledStatus]
  }

  test("getProcessState - should be scheduled when scenario scheduled and job finished on Flink") {
    val f            = new Fixture
    val deploymentId = f.manager.addActiveProcess(processName, PeriodicProcessDeploymentStatus.Scheduled)
    f.delegateDeploymentManagerStub.setStateStatus(processName, SimpleStateStatus.Finished, Some(deploymentId))

    val statusDetails = f.getMergedStatusDetails
    statusDetails.status shouldBe a[ScheduledStatus]
    f.getAllowedActions(statusDetails, processVersion.versionId, None, Some(processVersion.versionId)) shouldBe List(
      ScenarioActionName.Cancel,
      ScenarioActionName.Deploy
    )
  }

  test("getProcessState - should be finished when scenario finished and job finished on Flink") {
    val f                 = new Fixture
    val periodicProcessId = f.manager.addOnlyProcess(processName, CronScheduleProperty("0 0 0 1 1 ? 1970"))
    val deploymentId = f.manager.addOnlyDeployment(
      periodicProcessId,
      PeriodicProcessDeploymentStatus.Finished,
      LocalDateTime.ofEpochSecond(0, 0, ZoneOffset.UTC)
    )
    f.delegateDeploymentManagerStub.setStateStatus(processName, SimpleStateStatus.Finished, Some(deploymentId))
    f.periodicProcessService.deactivate(processName).futureValue

    val state =
      f.periodicDeploymentManager
        .getProcessState(idWithName, None, processVersion.versionId, None, Some(processVersion.versionId))
        .futureValue
        .value

    state.status shouldBe SimpleStateStatus.Finished
    state.allowedActions shouldBe List(ScenarioActionName.Deploy, ScenarioActionName.Archive, ScenarioActionName.Rename)
  }

  test("getProcessState - should be running when scenario deployed and job running on Flink") {
    val f            = new Fixture
    val deploymentId = f.manager.addActiveProcess(processName, PeriodicProcessDeploymentStatus.Deployed)
    f.delegateDeploymentManagerStub.setStateStatus(processName, SimpleStateStatus.Running, Some(deploymentId))

    val statusDetails = f.getMergedStatusDetails
    statusDetails.status shouldBe SimpleStateStatus.Running
    f.getAllowedActions(statusDetails, processVersion.versionId, None, Some(processVersion.versionId)) shouldBe List(
      ScenarioActionName.Cancel
    )
  }

  test("getProcessState - should be waiting for reschedule if job finished on Flink but scenario is still deployed") {
    val f            = new Fixture
    val deploymentId = f.manager.addActiveProcess(processName, PeriodicProcessDeploymentStatus.Deployed)
    f.delegateDeploymentManagerStub.setStateStatus(processName, SimpleStateStatus.Finished, Some(deploymentId))

    val statusDetails = f.getMergedStatusDetails
    statusDetails.status shouldBe WaitingForScheduleStatus
    f.getAllowedActions(statusDetails, processVersion.versionId, None, Some(processVersion.versionId)) shouldBe List(
      ScenarioActionName.Cancel
    )
  }

  test("getProcessState - should be failed after unsuccessful deployment") {
    val f = new Fixture
    f.manager.addActiveProcess(processName, PeriodicProcessDeploymentStatus.Failed)

    val statusDetails = f.getMergedStatusDetails
    statusDetails.status shouldBe ProblemStateStatus.Failed
    f.getAllowedActions(statusDetails, processVersion.versionId, None, Some(processVersion.versionId)) shouldBe List(
      ScenarioActionName.Cancel
    )
  }

  test("deploy - should fail for invalid periodic property") {
    val f = new Fixture

    val emptyScenario = CanonicalProcess(MetaData("fooId", StreamMetaData()), List.empty)

    val validateResult = f.periodicDeploymentManager
      .processCommand(DMValidateScenarioCommand(processVersion, DeploymentData.empty, emptyScenario, updateStrategy))
      .failed
      .futureValue
    validateResult shouldBe a[PeriodicProcessException]

    val deploymentResult = f.periodicDeploymentManager
      .processCommand(DMRunDeploymentCommand(processVersion, DeploymentData.empty, emptyScenario, updateStrategy))
      .failed
      .futureValue
    deploymentResult shouldBe a[PeriodicProcessException]
  }

  test("deploy - should schedule periodic scenario") {
    val f = new Fixture

    f.periodicDeploymentManager
      .processCommand(
        DMRunDeploymentCommand(
          processVersion,
          f.preparedDeploymentData,
          PeriodicProcessGen.buildCanonicalProcess(),
          updateStrategy
        )
      )
      .futureValue

    f.manager.processEntities.loneElement.active shouldBe true
    f.manager.deploymentEntities.loneElement.status shouldBe PeriodicProcessDeploymentStatus.Scheduled
  }

  test("deploy - should not cancel current schedule after trying to deploy with past date") {
    val f = new Fixture
    f.manager.addActiveProcess(processName, PeriodicProcessDeploymentStatus.Scheduled)

    f.periodicDeploymentManager
      .processCommand(
        DMRunDeploymentCommand(
          processVersion,
          DeploymentData.empty,
          PeriodicProcessGen.buildCanonicalProcess("0 0 0 ? * * 2000"),
          updateStrategy
        )
      )
      .failed
      .futureValue

    f.manager.processEntities.loneElement.active shouldBe true
    f.manager.deploymentEntities.loneElement.status shouldBe PeriodicProcessDeploymentStatus.Scheduled
  }

  test("deploy - should cancel existing scenario if already scheduled") {
    val f = new Fixture
    f.manager.addActiveProcess(processName, PeriodicProcessDeploymentStatus.Scheduled)

    f.periodicDeploymentManager
      .processCommand(
        DMRunDeploymentCommand(
          processVersion,
          f.preparedDeploymentData,
          PeriodicProcessGen.buildCanonicalProcess(),
          updateStrategy
        )
      )
      .futureValue

    f.manager.processEntities should have size 2
    f.manager.processEntities.map(_.active) shouldBe List(false, true)
  }

  test("should get status of failed job") {
    val f            = new Fixture
    val deploymentId = f.manager.addActiveProcess(processName, PeriodicProcessDeploymentStatus.Deployed)
    f.delegateDeploymentManagerStub.setStateStatus(processName, ProblemStateStatus.Failed, Some(deploymentId))

    val statusDetails = f.getMergedStatusDetails
    statusDetails.status shouldBe ProblemStateStatus.Failed
    f.getAllowedActions(statusDetails, processVersion.versionId, None, Some(processVersion.versionId)) shouldBe List(
      ScenarioActionName.Cancel
    )
  }

  test("should redeploy failed scenario") {
    val f            = new Fixture
    val deploymentId = f.manager.addActiveProcess(processName, PeriodicProcessDeploymentStatus.Deployed)
    f.delegateDeploymentManagerStub.setStateStatus(processName, ProblemStateStatus.Failed, Some(deploymentId))
    val statusDetailsBeforeRedeploy = f.getMergedStatusDetails
    statusDetailsBeforeRedeploy.status shouldBe ProblemStateStatus.Failed
    f.getAllowedActions(
      statusDetailsBeforeRedeploy,
      processVersion.versionId,
      None,
      Some(processVersion.versionId)
    ) shouldBe List(
      ScenarioActionName.Cancel
    ) // redeploy is blocked in GUI but API allows it

    f.periodicDeploymentManager
      .processCommand(
        DMRunDeploymentCommand(
          processVersion,
          f.preparedDeploymentData,
          PeriodicProcessGen.buildCanonicalProcess(),
          updateStrategy
        )
      )
      .futureValue

    f.manager.processEntities.map(_.active) shouldBe List(false, true)
    f.manager.deploymentEntities.map(_.status) shouldBe List(
      PeriodicProcessDeploymentStatus.Failed,
      PeriodicProcessDeploymentStatus.Scheduled
    )
    val statusDetailsAfterRedeploy = f.getMergedStatusDetails
    // Previous job is still visible as Failed.
    statusDetailsAfterRedeploy.status shouldBe a[ScheduledStatus]
    f.getAllowedActions(
      statusDetailsAfterRedeploy,
      processVersion.versionId,
      None,
      Some(processVersion.versionId)
    ) shouldBe List(
      ScenarioActionName.Cancel,
      ScenarioActionName.Deploy
    )
  }

  test("should redeploy scheduled scenario") {
    val f = new Fixture
    f.manager.addActiveProcess(processName, PeriodicProcessDeploymentStatus.Scheduled)

    f.periodicDeploymentManager
      .processCommand(
        DMRunDeploymentCommand(
          processVersion,
          f.preparedDeploymentData,
          PeriodicProcessGen.buildCanonicalProcess(),
          updateStrategy
        )
      )
      .futureValue

    f.manager.processEntities.map(_.active) shouldBe List(false, true)
    f.manager.deploymentEntities.map(_.status) shouldBe List(
      PeriodicProcessDeploymentStatus.Scheduled,
      PeriodicProcessDeploymentStatus.Scheduled
    )
  }

  test("should redeploy running scenario") {
    val f            = new Fixture
    val deploymentId = f.manager.addActiveProcess(processName, PeriodicProcessDeploymentStatus.Deployed)
    f.delegateDeploymentManagerStub.setStateStatus(processName, SimpleStateStatus.Running, Some(deploymentId))
    val statusDetails = f.getMergedStatusDetails
    f.getAllowedActions(statusDetails, processVersion.versionId, None, Some(processVersion.versionId)) shouldBe List(
      ScenarioActionName.Cancel
    ) // redeploy is blocked in GUI but API allows it

    f.periodicDeploymentManager
      .processCommand(
        DMRunDeploymentCommand(
          processVersion,
          f.preparedDeploymentData,
          PeriodicProcessGen.buildCanonicalProcess(),
          updateStrategy
        )
      )
      .futureValue

    f.manager.processEntities.map(_.active) shouldBe List(false, true)
    f.manager.deploymentEntities.map(_.status) shouldBe List(
      PeriodicProcessDeploymentStatus.Deployed,
      PeriodicProcessDeploymentStatus.Scheduled
    )
  }

  test("should redeploy finished scenario") {
    val f            = new Fixture
    val deploymentId = f.manager.addActiveProcess(processName, PeriodicProcessDeploymentStatus.Deployed)
    f.delegateDeploymentManagerStub.setStateStatus(processName, SimpleStateStatus.Finished, Some(deploymentId))
    val statusDetails = f.getMergedStatusDetails
    f.getAllowedActions(statusDetails, processVersion.versionId, None, Some(processVersion.versionId)) shouldBe List(
      ScenarioActionName.Cancel
    ) // redeploy is blocked in GUI but API allows it

    f.periodicDeploymentManager
      .processCommand(
        DMRunDeploymentCommand(
          processVersion,
          f.preparedDeploymentData,
          PeriodicProcessGen.buildCanonicalProcess(),
          updateStrategy
        )
      )
      .futureValue

    f.manager.processEntities.map(_.active) shouldBe List(false, true)
    f.manager.deploymentEntities.map(_.status) shouldBe List(
      PeriodicProcessDeploymentStatus.Finished,
      PeriodicProcessDeploymentStatus.Scheduled
    )
  }

  test("should cancel failed job after RescheduleActor handles finished") {
    val f            = new Fixture
    val deploymentId = f.manager.addActiveProcess(processName, PeriodicProcessDeploymentStatus.Deployed)
    f.delegateDeploymentManagerStub.setStateStatus(processName, ProblemStateStatus.Failed, Some(deploymentId))

    // this one is cyclically called by RescheduleActor
    f.periodicProcessService.handleFinished.futureValue

    f.getMergedStatusDetails.status shouldEqual ProblemStateStatus.Failed
    f.manager.deploymentEntities.loneElement.status shouldBe PeriodicProcessDeploymentStatus.Failed
    f.manager.processEntities.loneElement.active shouldBe true

    f.periodicDeploymentManager.processCommand(DMCancelScenarioCommand(processName, User("test", "Tester"))).futureValue

    f.manager.processEntities.loneElement.active shouldBe false
    f.manager.deploymentEntities.loneElement.status shouldBe PeriodicProcessDeploymentStatus.Failed

    f.getMergedStatusDetails.status shouldEqual SimpleStateStatus.Canceled
  }

  test("should reschedule failed job after RescheduleActor handles finished when configured") {
    val f            = new Fixture(executionConfig = PeriodicExecutionConfig(rescheduleOnFailure = true))
    val deploymentId = f.manager.addActiveProcess(processName, PeriodicProcessDeploymentStatus.Deployed)
    f.delegateDeploymentManagerStub.setStateStatus(processName, ProblemStateStatus.Failed, Some(deploymentId))

    // this one is cyclically called by RescheduleActor
    f.periodicProcessService.handleFinished.futureValue

    f.getMergedStatusDetails.status shouldBe a[ScheduledStatus]
    f.manager.deploymentEntities.map(_.status) shouldBe List(
      PeriodicProcessDeploymentStatus.Failed,
      PeriodicProcessDeploymentStatus.Scheduled
    )
    f.manager.processEntities.loneElement.active shouldBe true
  }

  test("should cancel failed job before RescheduleActor handles finished") {
    val f            = new Fixture
    val deploymentId = f.manager.addActiveProcess(processName, PeriodicProcessDeploymentStatus.Deployed)
    f.delegateDeploymentManagerStub.setStateStatus(processName, ProblemStateStatus.Failed, Some(deploymentId))

    f.periodicDeploymentManager.processCommand(DMCancelScenarioCommand(processName, User("test", "Tester"))).futureValue

    f.manager.processEntities.loneElement.active shouldBe false
    f.manager.deploymentEntities.loneElement.status shouldBe PeriodicProcessDeploymentStatus.Failed
    f.getMergedStatusDetails.status shouldEqual SimpleStateStatus.Canceled
  }

  test("should cancel failed scenario after disappeared from Flink console") {
    val f            = new Fixture
    val deploymentId = f.manager.addActiveProcess(processName, PeriodicProcessDeploymentStatus.Deployed)
    f.delegateDeploymentManagerStub.setStateStatus(processName, ProblemStateStatus.Failed, Some(deploymentId))

    // this one is cyclically called by RescheduleActor
    f.periodicProcessService.handleFinished.futureValue

    // after some time Flink stops returning job status
    f.delegateDeploymentManagerStub.jobStatus = Map.empty

    f.getMergedStatusDetails.status shouldEqual ProblemStateStatus.Failed
    f.manager.deploymentEntities.loneElement.status shouldBe PeriodicProcessDeploymentStatus.Failed
    f.manager.processEntities.loneElement.active shouldBe true

    f.periodicDeploymentManager.processCommand(DMCancelScenarioCommand(processName, User("test", "Tester"))).futureValue

    f.manager.processEntities.loneElement.active shouldBe false
    f.manager.deploymentEntities.loneElement.status shouldBe PeriodicProcessDeploymentStatus.Failed
    f.getMergedStatusDetails.status shouldBe SimpleStateStatus.Canceled
  }

  test("should take into account only latest deployments of active schedules during merged status computation") {
    val f                    = new Fixture
    val processId            = f.manager.addOnlyProcess(processName)
    val firstDeploymentRunAt = LocalDateTime.of(2023, 1, 1, 10, 0)
    f.manager.addOnlyDeployment(processId, PeriodicProcessDeploymentStatus.Failed, firstDeploymentRunAt)
    f.manager.addOnlyDeployment(
      processId,
      PeriodicProcessDeploymentStatus.Finished,
      firstDeploymentRunAt.plusHours(1)
    )

    f.getMergedStatusDetails.status shouldBe WaitingForScheduleStatus
  }

  test(
    "should take into account only latest inactive schedule request (periodic process) during merged status computation"
  ) {
    val f              = new Fixture
    val firstProcessId = f.manager.addOnlyProcess(processName)
    f.manager.addOnlyDeployment(firstProcessId, PeriodicProcessDeploymentStatus.Failed)
    f.manager.markInactive(firstProcessId)

    val secProcessId = f.manager.addOnlyProcess(processName)
    f.manager.addOnlyDeployment(secProcessId, PeriodicProcessDeploymentStatus.Finished)
    f.manager.markInactive(secProcessId)

    f.getMergedStatusDetails.status shouldBe SimpleStateStatus.Finished
  }

}
