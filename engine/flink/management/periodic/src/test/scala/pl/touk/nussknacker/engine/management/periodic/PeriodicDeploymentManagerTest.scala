package pl.touk.nussknacker.engine.management.periodic

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.{Inside, OptionValues}
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.ProcessActionType
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus.ProblemStateStatus
import pl.touk.nussknacker.engine.api.deployment.{
  DataFreshnessPolicy,
  ProcessAction,
  ProcessActionId,
  ProcessActionState,
  ProcessActionType,
  ProcessingTypeDeploymentService,
  ProcessingTypeDeploymentServiceStub,
  StatusDetails
}
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessIdWithName, ProcessName, VersionId}
import pl.touk.nussknacker.engine.api.{MetaData, ProcessVersion, StreamMetaData}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.{DeploymentData, User}
import pl.touk.nussknacker.engine.management.periodic.PeriodicProcessService.PeriodicProcessStatus
import pl.touk.nussknacker.engine.management.periodic.PeriodicStateStatus.{ScheduledStatus, WaitingForScheduleStatus}
import pl.touk.nussknacker.engine.management.periodic.model.PeriodicProcessDeploymentStatus
import pl.touk.nussknacker.engine.management.periodic.service.{
  DefaultAdditionalDeploymentDataProvider,
  EmptyListener,
  ProcessConfigEnricher
}
import pl.touk.nussknacker.test.PatientScalaFutures

import java.time.{Clock, Instant, LocalDateTime, ZoneOffset}
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

  private val processVersion = ProcessVersion(
    versionId = VersionId(42L),
    processName = processName,
    processId = processId,
    user = "test user",
    modelVersion = None
  )

  class Fixture(executionConfig: PeriodicExecutionConfig = PeriodicExecutionConfig()) {
    val repository                    = new db.InMemPeriodicProcessesRepository(processingType = "testProcessingType")
    val delegateDeploymentManagerStub = new DeploymentManagerStub
    val jarManagerStub                = new JarManagerStub
    val preparedDeploymentData        = DeploymentData.withDeploymentId(UUID.randomUUID().toString)

    implicit val deploymentService: ProcessingTypeDeploymentService = new ProcessingTypeDeploymentServiceStub(
      List.empty
    )

    val periodicProcessService = new PeriodicProcessService(
      delegateDeploymentManager = delegateDeploymentManagerStub,
      jarManager = jarManagerStub,
      scheduledProcessesRepository = repository,
      periodicProcessListener = EmptyListener,
      additionalDeploymentDataProvider = DefaultAdditionalDeploymentDataProvider,
      deploymentRetryConfig = DeploymentRetryConfig(),
      executionConfig = executionConfig,
      processConfigEnricher = ProcessConfigEnricher.identity,
      clock = Clock.systemDefaultZone()
    )

    val periodicDeploymentManager = new PeriodicDeploymentManager(
      delegate = delegateDeploymentManagerStub,
      service = periodicProcessService,
      schedulePropertyExtractor = CronSchedulePropertyExtractor(),
      EmptyPeriodicCustomActionsProvider,
      toClose = () => ()
    )

    def getAllowedActions(statusDetails: StatusDetails): List[ProcessActionType] = {
      periodicDeploymentManager.processStateDefinitionManager.processState(statusDetails).allowedActions
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
    f.repository.addActiveProcess(processName, PeriodicProcessDeploymentStatus.Scheduled, processingType = "other")

    val state = f.getMergedStatusDetails.status

    state shouldEqual SimpleStateStatus.NotDeployed
  }

  test("getProcessState - should be scheduled when scenario scheduled and no job on Flink") {
    val f = new Fixture
    f.repository.addActiveProcess(processName, PeriodicProcessDeploymentStatus.Scheduled)

    val statusDetails = f.getMergedStatusDetails
    statusDetails.status shouldBe a[ScheduledStatus]
    f.getAllowedActions(statusDetails) shouldBe List(ProcessActionType.Cancel, ProcessActionType.Deploy)

    val deployAction = ProcessAction(
      id = ProcessActionId(UUID.randomUUID()),
      processId = processId,
      processVersionId = VersionId(1),
      user = "fooUser",
      createdAt = Instant.ofEpochMilli(0),
      performedAt = Instant.ofEpochMilli(0),
      actionType = ProcessActionType.Deploy,
      state = ProcessActionState.Finished,
      failureMessage = None,
      commentId = None,
      comment = None,
      buildInfo = Map.empty
    )
    f.periodicDeploymentManager
      .getProcessState(idWithName, Some(deployAction))
      .futureValue
      .value
      .status shouldBe a[ScheduledStatus]
  }

  test("getProcessState - should be scheduled when scenario scheduled and job finished on Flink") {
    val f            = new Fixture
    val deploymentId = f.repository.addActiveProcess(processName, PeriodicProcessDeploymentStatus.Scheduled)
    f.delegateDeploymentManagerStub.setStateStatus(SimpleStateStatus.Finished, Some(deploymentId))

    val statusDetails = f.getMergedStatusDetails
    statusDetails.status shouldBe a[ScheduledStatus]
    f.getAllowedActions(statusDetails) shouldBe List(ProcessActionType.Cancel, ProcessActionType.Deploy)
  }

  test("getProcessState - should be finished when scenario finished and job finished on Flink") {
    val f                 = new Fixture
    val periodicProcessId = f.repository.addOnlyProcess(processName, CronScheduleProperty("0 0 0 1 1 ? 1970"))
    val deploymentId = f.repository.addOnlyDeployment(
      periodicProcessId,
      PeriodicProcessDeploymentStatus.Finished,
      LocalDateTime.ofEpochSecond(0, 0, ZoneOffset.UTC)
    )
    f.delegateDeploymentManagerStub.setStateStatus(SimpleStateStatus.Finished, Some(deploymentId))
    f.periodicProcessService.deactivate(processName).futureValue

    // Warning: don't remove type declaration because it causes compilation error in scala 2.12
    val deployAction: ProcessAction = ProcessAction(
      id = ProcessActionId(UUID.randomUUID()),
      processId = processId,
      processVersionId = VersionId(1),
      user = "fooUser",
      createdAt = Instant.ofEpochMilli(0),
      performedAt = Instant.ofEpochMilli(0),
      actionType = ProcessActionType.Deploy,
      state = ProcessActionState.Finished,
      failureMessage = None,
      commentId = None,
      comment = None,
      buildInfo = Map.empty
    )
    val state = f.periodicDeploymentManager.getProcessState(idWithName, Some(deployAction)).futureValue.value

    val status = state.status
    status shouldBe SimpleStateStatus.Finished
    state.allowedActions shouldBe List(ProcessActionType.Deploy, ProcessActionType.Archive, ProcessActionType.Rename)
  }

  test("getProcessState - should be running when scenario deployed and job running on Flink") {
    val f            = new Fixture
    val deploymentId = f.repository.addActiveProcess(processName, PeriodicProcessDeploymentStatus.Deployed)
    f.delegateDeploymentManagerStub.setStateStatus(SimpleStateStatus.Running, Some(deploymentId))

    val statusDetails = f.getMergedStatusDetails
    statusDetails.status shouldBe SimpleStateStatus.Running
    f.getAllowedActions(statusDetails) shouldBe List(ProcessActionType.Cancel)
  }

  test("getProcessState - should be waiting for reschedule if job finished on Flink but scenario is still deployed") {
    val f            = new Fixture
    val deploymentId = f.repository.addActiveProcess(processName, PeriodicProcessDeploymentStatus.Deployed)
    f.delegateDeploymentManagerStub.setStateStatus(SimpleStateStatus.Finished, Some(deploymentId))

    val statusDetails = f.getMergedStatusDetails
    statusDetails.status shouldBe WaitingForScheduleStatus
    f.getAllowedActions(statusDetails) shouldBe List(ProcessActionType.Cancel)
  }

  test("getProcessState - should be failed after unsuccessful deployment") {
    val f = new Fixture
    f.repository.addActiveProcess(processName, PeriodicProcessDeploymentStatus.Failed)

    val statusDetails = f.getMergedStatusDetails
    statusDetails.status shouldBe ProblemStateStatus.Failed
    f.getAllowedActions(statusDetails) shouldBe List(ProcessActionType.Cancel)
  }

  test("deploy - should fail for invalid periodic property") {
    val f = new Fixture

    val emptyScenario = CanonicalProcess(MetaData("fooId", StreamMetaData()), List.empty)

    val validateResult = f.periodicDeploymentManager
      .validate(processVersion, DeploymentData.empty, emptyScenario)
      .failed
      .futureValue
    validateResult shouldBe a[PeriodicProcessException]

    val deploymentResult = f.periodicDeploymentManager
      .deploy(processVersion, DeploymentData.empty, emptyScenario, None)
      .failed
      .futureValue
    deploymentResult shouldBe a[PeriodicProcessException]
  }

  test("deploy - should schedule periodic scenario") {
    val f = new Fixture

    f.periodicDeploymentManager
      .deploy(processVersion, f.preparedDeploymentData, PeriodicProcessGen.buildCanonicalProcess(), None)
      .futureValue

    f.repository.processEntities.loneElement.active shouldBe true
    f.repository.deploymentEntities.loneElement.status shouldBe PeriodicProcessDeploymentStatus.Scheduled
  }

  test("deploy - should not cancel current schedule after trying to deploy with past date") {
    val f = new Fixture
    f.repository.addActiveProcess(processName, PeriodicProcessDeploymentStatus.Scheduled)

    f.periodicDeploymentManager
      .deploy(processVersion, DeploymentData.empty, PeriodicProcessGen.buildCanonicalProcess("0 0 0 ? * * 2000"), None)
      .failed
      .futureValue

    f.repository.processEntities.loneElement.active shouldBe true
    f.repository.deploymentEntities.loneElement.status shouldBe PeriodicProcessDeploymentStatus.Scheduled
  }

  test("deploy - should cancel existing scenario if already scheduled") {
    val f = new Fixture
    f.repository.addActiveProcess(processName, PeriodicProcessDeploymentStatus.Scheduled)

    f.periodicDeploymentManager
      .deploy(processVersion, f.preparedDeploymentData, PeriodicProcessGen.buildCanonicalProcess(), None)
      .futureValue

    f.repository.processEntities should have size 2
    f.repository.processEntities.map(_.active) shouldBe List(false, true)
  }

  test("should get status of failed job") {
    val f            = new Fixture
    val deploymentId = f.repository.addActiveProcess(processName, PeriodicProcessDeploymentStatus.Deployed)
    f.delegateDeploymentManagerStub.setStateStatus(ProblemStateStatus.Failed, Some(deploymentId))

    val statusDetails = f.getMergedStatusDetails
    statusDetails.status shouldBe ProblemStateStatus.Failed
    f.getAllowedActions(statusDetails) shouldBe List(ProcessActionType.Cancel)
  }

  test("should redeploy failed scenario") {
    val f            = new Fixture
    val deploymentId = f.repository.addActiveProcess(processName, PeriodicProcessDeploymentStatus.Deployed)
    f.delegateDeploymentManagerStub.setStateStatus(ProblemStateStatus.Failed, Some(deploymentId))
    val statusDetailsBeforeRedeploy = f.getMergedStatusDetails
    statusDetailsBeforeRedeploy.status shouldBe ProblemStateStatus.Failed
    f.getAllowedActions(statusDetailsBeforeRedeploy) shouldBe List(
      ProcessActionType.Cancel
    ) // redeploy is blocked in GUI but API allows it

    f.periodicDeploymentManager
      .deploy(processVersion, f.preparedDeploymentData, PeriodicProcessGen.buildCanonicalProcess(), None)
      .futureValue

    f.repository.processEntities.map(_.active) shouldBe List(false, true)
    f.repository.deploymentEntities.map(_.status) shouldBe List(
      PeriodicProcessDeploymentStatus.Failed,
      PeriodicProcessDeploymentStatus.Scheduled
    )
    val statusDetailsAfterRedeploy = f.getMergedStatusDetails
    // Previous job is still visible as Failed.
    statusDetailsAfterRedeploy.status shouldBe a[ScheduledStatus]
    f.getAllowedActions(statusDetailsAfterRedeploy) shouldBe List(ProcessActionType.Cancel, ProcessActionType.Deploy)
  }

  test("should redeploy scheduled scenario") {
    val f = new Fixture
    f.repository.addActiveProcess(processName, PeriodicProcessDeploymentStatus.Scheduled)
//    f.getAllowedActions shouldBe List(ProcessActionType.Cancel, ProcessActionType.Deploy)

    f.periodicDeploymentManager
      .deploy(processVersion, f.preparedDeploymentData, PeriodicProcessGen.buildCanonicalProcess(), None)
      .futureValue

    f.repository.processEntities.map(_.active) shouldBe List(false, true)
    f.repository.deploymentEntities.map(_.status) shouldBe List(
      PeriodicProcessDeploymentStatus.Scheduled,
      PeriodicProcessDeploymentStatus.Scheduled
    )
  }

  test("should redeploy running scenario") {
    val f            = new Fixture
    val deploymentId = f.repository.addActiveProcess(processName, PeriodicProcessDeploymentStatus.Deployed)
    f.delegateDeploymentManagerStub.setStateStatus(SimpleStateStatus.Running, Some(deploymentId))
    val statusDetails = f.getMergedStatusDetails
    f.getAllowedActions(statusDetails) shouldBe List(
      ProcessActionType.Cancel
    ) // redeploy is blocked in GUI but API allows it

    f.periodicDeploymentManager
      .deploy(processVersion, f.preparedDeploymentData, PeriodicProcessGen.buildCanonicalProcess(), None)
      .futureValue

    f.repository.processEntities.map(_.active) shouldBe List(false, true)
    f.repository.deploymentEntities.map(_.status) shouldBe List(
      PeriodicProcessDeploymentStatus.Deployed,
      PeriodicProcessDeploymentStatus.Scheduled
    )
  }

  test("should redeploy finished scenario") {
    val f            = new Fixture
    val deploymentId = f.repository.addActiveProcess(processName, PeriodicProcessDeploymentStatus.Deployed)
    f.delegateDeploymentManagerStub.setStateStatus(SimpleStateStatus.Finished, Some(deploymentId))
    val statusDetails = f.getMergedStatusDetails
    f.getAllowedActions(statusDetails) shouldBe List(
      ProcessActionType.Cancel
    ) // redeploy is blocked in GUI but API allows it

    f.periodicDeploymentManager
      .deploy(processVersion, f.preparedDeploymentData, PeriodicProcessGen.buildCanonicalProcess(), None)
      .futureValue

    f.repository.processEntities.map(_.active) shouldBe List(false, true)
    f.repository.deploymentEntities.map(_.status) shouldBe List(
      PeriodicProcessDeploymentStatus.Finished,
      PeriodicProcessDeploymentStatus.Scheduled
    )
  }

  test("should cancel failed job after RescheduleActor handles finished") {
    val f            = new Fixture
    val deploymentId = f.repository.addActiveProcess(processName, PeriodicProcessDeploymentStatus.Deployed)
    f.delegateDeploymentManagerStub.setStateStatus(ProblemStateStatus.Failed, Some(deploymentId))

    // this one is cyclically called by RescheduleActor
    f.periodicProcessService.handleFinished.futureValue

    f.getMergedStatusDetails.status shouldEqual ProblemStateStatus.Failed
    f.repository.deploymentEntities.loneElement.status shouldBe PeriodicProcessDeploymentStatus.Failed
    f.repository.processEntities.loneElement.active shouldBe true

    f.periodicDeploymentManager.cancel(processName, User("test", "Tester")).futureValue

    f.repository.processEntities.loneElement.active shouldBe false
    f.repository.deploymentEntities.loneElement.status shouldBe PeriodicProcessDeploymentStatus.Failed

    f.getMergedStatusDetails.status shouldEqual SimpleStateStatus.Canceled
  }

  test("should reschedule failed job after RescheduleActor handles finished when configured") {
    val f            = new Fixture(executionConfig = PeriodicExecutionConfig(rescheduleOnFailure = true))
    val deploymentId = f.repository.addActiveProcess(processName, PeriodicProcessDeploymentStatus.Deployed)
    f.delegateDeploymentManagerStub.setStateStatus(ProblemStateStatus.Failed, Some(deploymentId))

    // this one is cyclically called by RescheduleActor
    f.periodicProcessService.handleFinished.futureValue

    f.getMergedStatusDetails.status shouldBe a[ScheduledStatus]
    f.repository.deploymentEntities.map(_.status) shouldBe List(
      PeriodicProcessDeploymentStatus.Failed,
      PeriodicProcessDeploymentStatus.Scheduled
    )
    f.repository.processEntities.loneElement.active shouldBe true
  }

  test("should cancel failed job before RescheduleActor handles finished") {
    val f            = new Fixture
    val deploymentId = f.repository.addActiveProcess(processName, PeriodicProcessDeploymentStatus.Deployed)
    f.delegateDeploymentManagerStub.setStateStatus(ProblemStateStatus.Failed, Some(deploymentId))

    f.periodicDeploymentManager.cancel(processName, User("test", "Tester")).futureValue

    f.repository.processEntities.loneElement.active shouldBe false
    f.repository.deploymentEntities.loneElement.status shouldBe PeriodicProcessDeploymentStatus.Failed
    f.getMergedStatusDetails.status shouldEqual SimpleStateStatus.Canceled
  }

  test("should cancel failed scenario after disappeared from Flink console") {
    val f            = new Fixture
    val deploymentId = f.repository.addActiveProcess(processName, PeriodicProcessDeploymentStatus.Deployed)
    f.delegateDeploymentManagerStub.setStateStatus(ProblemStateStatus.Failed, Some(deploymentId))

    // this one is cyclically called by RescheduleActor
    f.periodicProcessService.handleFinished.futureValue

    // after some time Flink stops returning job status
    f.delegateDeploymentManagerStub.jobStatus = None

    f.getMergedStatusDetails.status shouldEqual ProblemStateStatus.Failed
    f.repository.deploymentEntities.loneElement.status shouldBe PeriodicProcessDeploymentStatus.Failed
    f.repository.processEntities.loneElement.active shouldBe true

    f.periodicDeploymentManager.cancel(processName, User("test", "Tester")).futureValue

    f.repository.processEntities.loneElement.active shouldBe false
    f.repository.deploymentEntities.loneElement.status shouldBe PeriodicProcessDeploymentStatus.Failed
    f.getMergedStatusDetails.status shouldBe SimpleStateStatus.Canceled
  }

  test("should take into account only latest deployments of active schedules during merged status computation") {
    val f                    = new Fixture
    val processId            = f.repository.addOnlyProcess(processName)
    val firstDeploymentRunAt = LocalDateTime.of(2023, 1, 1, 10, 0)
    f.repository.addOnlyDeployment(processId, PeriodicProcessDeploymentStatus.Failed, firstDeploymentRunAt)
    f.repository.addOnlyDeployment(
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
    val firstProcessId = f.repository.addOnlyProcess(processName)
    f.repository.addOnlyDeployment(firstProcessId, PeriodicProcessDeploymentStatus.Failed)
    f.repository.markInactive(firstProcessId)

    val secProcessId = f.repository.addOnlyProcess(processName)
    f.repository.addOnlyDeployment(secProcessId, PeriodicProcessDeploymentStatus.Finished)
    f.repository.markInactive(secProcessId)

    f.getMergedStatusDetails.status shouldBe SimpleStateStatus.Finished
  }

}
