package pl.touk.nussknacker.engine.management.periodic

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.{Inside, OptionValues}
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.ProcessActionType
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus.ProblemStateStatus
import pl.touk.nussknacker.engine.api.deployment.{DataFreshnessPolicy, ProcessAction, ProcessActionType, StatusDetails}
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.engine.api.{MetaData, ProcessVersion, StreamMetaData}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.{DeploymentData, User}
import pl.touk.nussknacker.engine.management.periodic.PeriodicStateStatus.{ScheduledStatus, WaitingForScheduleStatus}
import pl.touk.nussknacker.engine.management.periodic.model.PeriodicProcessDeploymentStatus
import pl.touk.nussknacker.engine.management.periodic.service.{DefaultAdditionalDeploymentDataProvider, EmptyListener, ProcessConfigEnricher}
import pl.touk.nussknacker.test.PatientScalaFutures

import java.time.{Clock, Instant, LocalDateTime, ZoneOffset}

class PeriodicDeploymentManagerTest extends AnyFunSuite
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
  private val processVersion = ProcessVersion(versionId = VersionId(42L), processName = processName, processId = ProcessId(1), user = "test user", modelVersion = None)

  class Fixture(executionConfig: PeriodicExecutionConfig = PeriodicExecutionConfig()) {
    val repository = new db.InMemPeriodicProcessesRepository(processingType = "testProcessingType")
    val delegateDeploymentManagerStub = new DeploymentManagerStub
    val jarManagerStub = new JarManagerStub
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

  }

  test("getProcessState - should return none for no job") {
    val f = new Fixture

    val state = f.periodicDeploymentManager.getProcessStates(processName).futureValue.value

    state shouldBe Symbol("empty")
  }

  test("getProcessState - should return none for scenario with different processing type") {
    val f = new Fixture
    f.repository.addActiveProcess(processName, PeriodicProcessDeploymentStatus.Scheduled, processingType = "other")

    val state = f.periodicDeploymentManager.getProcessStates(processName).futureValue.value

    state shouldBe Symbol("empty")
  }

  test("getProcessState - should be scheduled when scenario scheduled and no job on Flink") {
    val f = new Fixture
    f.repository.addActiveProcess(processName, PeriodicProcessDeploymentStatus.Scheduled)

    inside(f.periodicDeploymentManager.getProcessStates(processName).futureValue.value) {
      case singleStatus :: Nil =>
        singleStatus.status shouldBe a[ScheduledStatus]
        f.getAllowedActions(singleStatus) shouldBe List(ProcessActionType.Cancel, ProcessActionType.Deploy)
    }

    val deployAction = ProcessAction(VersionId(1), Instant.ofEpochMilli(0), "fooUser", ProcessActionType.Deploy, None, None, Map.empty)
    f.periodicDeploymentManager.getProcessState(processName, Some(deployAction)).futureValue.value.status shouldBe a[ScheduledStatus]
  }

  test("getProcessState - should be scheduled when scenario scheduled and job finished on Flink") {
    val f = new Fixture
    f.repository.addActiveProcess(processName, PeriodicProcessDeploymentStatus.Scheduled)
    f.delegateDeploymentManagerStub.setStateStatus(SimpleStateStatus.Finished)

    inside(f.periodicDeploymentManager.getProcessStates(processName).futureValue.value) {
      case singleStatus :: Nil =>
        singleStatus.status shouldBe a[ScheduledStatus]
        f.getAllowedActions(singleStatus) shouldBe List(ProcessActionType.Cancel, ProcessActionType.Deploy)
    }
  }

  test("getProcessState - should be finished when scenario finished and job finished on Flink") {
    val f = new Fixture
    val processId = f.repository.addOnlyProcess(processName, CronScheduleProperty("0 0 0 1 1 ? 1970"))
    f.repository.addOnlyDeployment(processId, PeriodicProcessDeploymentStatus.Finished, LocalDateTime.ofEpochSecond(0, 0, ZoneOffset.UTC))
    f.delegateDeploymentManagerStub.setStateStatus(SimpleStateStatus.Finished)
    f.periodicProcessService.deactivate(processName).futureValue

    val deployAction = ProcessAction(VersionId(1), Instant.ofEpochMilli(0), "fooUser", ProcessActionType.Deploy, None, None, Map.empty)
    val state = f.periodicDeploymentManager.getProcessState(processName, Some(deployAction)).futureValue.value

    val status = state.status
    status shouldBe SimpleStateStatus.Finished
    state.allowedActions shouldBe List(ProcessActionType.Deploy, ProcessActionType.Archive, ProcessActionType.Rename)
  }

  test("getProcessState - should be running when scenario deployed and job running on Flink") {
    val f = new Fixture
    f.repository.addActiveProcess(processName, PeriodicProcessDeploymentStatus.Deployed)
    f.delegateDeploymentManagerStub.setStateStatus(SimpleStateStatus.Running)

    inside(f.periodicDeploymentManager.getProcessStates(processName).futureValue.value) {
      case singleStatus :: Nil =>
        singleStatus.status shouldBe SimpleStateStatus.Running
        f.getAllowedActions(singleStatus) shouldBe List(ProcessActionType.Cancel)
    }
  }

  test("getProcessState - should be waiting for reschedule if job finished on Flink but scenario is still deployed") {
    val f = new Fixture
    f.repository.addActiveProcess(processName, PeriodicProcessDeploymentStatus.Deployed)
    f.delegateDeploymentManagerStub.setStateStatus(SimpleStateStatus.Finished)

    inside(f.periodicDeploymentManager.getProcessStates(processName).futureValue.value) {
      case singleStatus :: Nil =>
        singleStatus.status shouldBe WaitingForScheduleStatus
        f.getAllowedActions(singleStatus) shouldBe List(ProcessActionType.Cancel)
    }
  }

  test("getProcessState - should be failed after unsuccessful deployment") {
    val f = new Fixture
    f.repository.addActiveProcess(processName, PeriodicProcessDeploymentStatus.Failed)

    inside(f.periodicDeploymentManager.getProcessStates(processName).futureValue.value) {
      case singleStatus :: Nil =>
        singleStatus.status shouldBe ProblemStateStatus.Failed
        f.getAllowedActions(singleStatus) shouldBe List(ProcessActionType.Cancel)
    }
  }

  test("deploy - should fail for invalid periodic property") {
    val f = new Fixture

    val emptyScenario = CanonicalProcess(MetaData("fooId", StreamMetaData()), List.empty)

    val validateResult = f.periodicDeploymentManager
          .validate(processVersion, DeploymentData.empty, emptyScenario).failed.futureValue
    validateResult shouldBe a [PeriodicProcessException]

    val deploymentResult = f.periodicDeploymentManager
      .deploy(processVersion, DeploymentData.empty, emptyScenario, None).failed.futureValue
    deploymentResult shouldBe a [PeriodicProcessException]
  }

  test("deploy - should schedule periodic scenario") {
    val f = new Fixture

    f.periodicDeploymentManager.deploy(processVersion, DeploymentData.empty, PeriodicProcessGen.buildCanonicalProcess(), None).futureValue

    f.repository.processEntities.loneElement.active shouldBe true
    f.repository.deploymentEntities.loneElement.status shouldBe PeriodicProcessDeploymentStatus.Scheduled
  }

  test("deploy - should not cancel current schedule after trying to deploy with past date") {
    val f = new Fixture
    f.repository.addActiveProcess(processName, PeriodicProcessDeploymentStatus.Scheduled)

    f.periodicDeploymentManager.deploy(processVersion, DeploymentData.empty, PeriodicProcessGen.buildCanonicalProcess("0 0 0 ? * * 2000"), None)
      .failed.futureValue

    f.repository.processEntities.loneElement.active shouldBe true
    f.repository.deploymentEntities.loneElement.status shouldBe PeriodicProcessDeploymentStatus.Scheduled
  }

  test("deploy - should cancel existing scenario if already scheduled") {
    val f = new Fixture
    f.repository.addActiveProcess(processName, PeriodicProcessDeploymentStatus.Scheduled)

    f.periodicDeploymentManager.deploy(processVersion, DeploymentData.empty, PeriodicProcessGen.buildCanonicalProcess(), None).futureValue

    f.repository.processEntities should have size 2
    f.repository.processEntities.map(_.active) shouldBe List(false, true)
  }

  test("should get status of failed job") {
    val f = new Fixture
    f.repository.addActiveProcess(processName, PeriodicProcessDeploymentStatus.Deployed)
    f.delegateDeploymentManagerStub.setStateStatus(ProblemStateStatus.Failed)

    inside(f.periodicDeploymentManager.getProcessStates(processName).futureValue.value) {
      case singleStatus :: Nil =>
        singleStatus.status shouldBe ProblemStateStatus.Failed
        f.getAllowedActions(singleStatus) shouldBe List(ProcessActionType.Cancel)
    }
  }

  test("should redeploy failed scenario") {
    val f = new Fixture
    f.repository.addActiveProcess(processName, PeriodicProcessDeploymentStatus.Deployed)
    f.delegateDeploymentManagerStub.setStateStatus(ProblemStateStatus.Failed)
    inside(f.periodicDeploymentManager.getProcessStates(processName).futureValue.value) {
      case singleStatus :: Nil =>
        singleStatus.status shouldBe ProblemStateStatus.Failed
        f.getAllowedActions(singleStatus) shouldBe List(ProcessActionType.Cancel) // redeploy is blocked in GUI but API allows it
    }

    f.periodicDeploymentManager.deploy(processVersion, DeploymentData.empty, PeriodicProcessGen.buildCanonicalProcess(), None).futureValue

    f.repository.processEntities.map(_.active) shouldBe List(false, true)
    f.repository.deploymentEntities.map(_.status) shouldBe List(PeriodicProcessDeploymentStatus.Failed, PeriodicProcessDeploymentStatus.Scheduled)
    inside(f.periodicDeploymentManager.getProcessStates(processName).futureValue.value) {
      case singleStatus :: Nil =>
        // Previous job is still visible as Failed.
        singleStatus.status shouldBe a[ScheduledStatus]
        f.getAllowedActions(singleStatus) shouldBe List(ProcessActionType.Cancel, ProcessActionType.Deploy)
    }
  }

  test("should redeploy scheduled scenario") {
    val f = new Fixture
    f.repository.addActiveProcess(processName, PeriodicProcessDeploymentStatus.Scheduled)
//    f.getAllowedActions shouldBe List(ProcessActionType.Cancel, ProcessActionType.Deploy)

    f.periodicDeploymentManager.deploy(processVersion, DeploymentData.empty, PeriodicProcessGen.buildCanonicalProcess(), None).futureValue

    f.repository.processEntities.map(_.active) shouldBe List(false, true)
    f.repository.deploymentEntities.map(_.status) shouldBe List(PeriodicProcessDeploymentStatus.Scheduled, PeriodicProcessDeploymentStatus.Scheduled)
  }

  test("should redeploy running scenario") {
    val f = new Fixture
    f.repository.addActiveProcess(processName, PeriodicProcessDeploymentStatus.Deployed)
    f.delegateDeploymentManagerStub.setStateStatus(SimpleStateStatus.Running)
    inside(f.periodicDeploymentManager.getProcessStates(processName).futureValue.value) {
      case singleStatus :: Nil =>
        f.getAllowedActions(singleStatus) shouldBe List(ProcessActionType.Cancel) // redeploy is blocked in GUI but API allows it
    }

    f.periodicDeploymentManager.deploy(processVersion, DeploymentData.empty, PeriodicProcessGen.buildCanonicalProcess(), None).futureValue

    f.repository.processEntities.map(_.active) shouldBe List(false, true)
    f.repository.deploymentEntities.map(_.status) shouldBe List(PeriodicProcessDeploymentStatus.Deployed, PeriodicProcessDeploymentStatus.Scheduled)
  }

  test("should redeploy finished scenario") {
    val f = new Fixture
    f.repository.addActiveProcess(processName, PeriodicProcessDeploymentStatus.Deployed)
    f.delegateDeploymentManagerStub.setStateStatus(SimpleStateStatus.Finished)
    inside(f.periodicDeploymentManager.getProcessStates(processName).futureValue.value) {
      case singleStatus :: Nil =>
        f.getAllowedActions(singleStatus) shouldBe List(ProcessActionType.Cancel) // redeploy is blocked in GUI but API allows it
    }

    f.periodicDeploymentManager.deploy(processVersion, DeploymentData.empty, PeriodicProcessGen.buildCanonicalProcess(), None).futureValue

    f.repository.processEntities.map(_.active) shouldBe List(false, true)
    f.repository.deploymentEntities.map(_.status) shouldBe List(PeriodicProcessDeploymentStatus.Finished, PeriodicProcessDeploymentStatus.Scheduled)
  }

  test("should cancel failed job after RescheduleActor handles finished") {
    val f = new Fixture
    f.repository.addActiveProcess(processName, PeriodicProcessDeploymentStatus.Deployed)
    f.delegateDeploymentManagerStub.setStateStatus(ProblemStateStatus.Failed)

    //this one is cyclically called by RescheduleActor
    f.periodicProcessService.handleFinished.futureValue

    f.periodicDeploymentManager.getProcessStates(processName).futureValue.value.map(_.status) should matchPattern {
      case ProblemStateStatus.Failed :: Nil =>
    }
    f.repository.deploymentEntities.loneElement.status shouldBe PeriodicProcessDeploymentStatus.Failed
    f.repository.processEntities.loneElement.active shouldBe true

    f.periodicDeploymentManager.cancel(processName, User("test", "Tester")).futureValue

    f.repository.processEntities.loneElement.active shouldBe false
    f.repository.deploymentEntities.loneElement.status shouldBe PeriodicProcessDeploymentStatus.Failed

    f.periodicDeploymentManager.getProcessStates(processName).futureValue.value.map(_.status) should matchPattern {
      case SimpleStateStatus.Canceled :: Nil =>
    }
  }

  test("should reschedule failed job after RescheduleActor handles finished when configured") {
    val f = new Fixture(executionConfig = PeriodicExecutionConfig(rescheduleOnFailure = true))
    f.repository.addActiveProcess(processName, PeriodicProcessDeploymentStatus.Deployed)
    f.delegateDeploymentManagerStub.setStateStatus(ProblemStateStatus.Failed)

    //this one is cyclically called by RescheduleActor
    f.periodicProcessService.handleFinished.futureValue

    f.periodicDeploymentManager.getProcessStates(processName).futureValue.value.map(_.status) should matchPattern {
      case (_: ScheduledStatus) :: Nil =>
    }
    f.repository.deploymentEntities.map(_.status) shouldBe List(PeriodicProcessDeploymentStatus.Failed, PeriodicProcessDeploymentStatus.Scheduled)
    f.repository.processEntities.loneElement.active shouldBe true
  }

  test("should cancel failed job before RescheduleActor handles finished") {
    val f = new Fixture
    f.repository.addActiveProcess(processName, PeriodicProcessDeploymentStatus.Deployed)
    f.delegateDeploymentManagerStub.setStateStatus(ProblemStateStatus.Failed)

    f.periodicDeploymentManager.cancel(processName, User("test", "Tester")).futureValue

    f.repository.processEntities.loneElement.active shouldBe false
    f.repository.deploymentEntities.loneElement.status shouldBe PeriodicProcessDeploymentStatus.Failed
    f.periodicDeploymentManager.getProcessStates(processName).futureValue.value.map(_.status) should matchPattern {
      case SimpleStateStatus.Canceled :: Nil =>
    }
  }

  test("should cancel failed scenario after disappeared from Flink console") {
    val f = new Fixture
    f.repository.addActiveProcess(processName, PeriodicProcessDeploymentStatus.Deployed)
    f.delegateDeploymentManagerStub.setStateStatus(ProblemStateStatus.Failed)

    //this one is cyclically called by RescheduleActor
    f.periodicProcessService.handleFinished.futureValue

    //after some time Flink stops returning job status
    f.delegateDeploymentManagerStub.jobStatus = None

    f.periodicDeploymentManager.getProcessStates(processName).futureValue.value.map(_.status) should matchPattern {
      case ProblemStateStatus.Failed :: Nil =>
    }
    f.repository.deploymentEntities.loneElement.status shouldBe PeriodicProcessDeploymentStatus.Failed
    f.repository.processEntities.loneElement.active shouldBe true

    f.periodicDeploymentManager.cancel(processName, User("test", "Tester")).futureValue

    f.repository.processEntities.loneElement.active shouldBe false
    f.repository.deploymentEntities.loneElement.status shouldBe PeriodicProcessDeploymentStatus.Failed
    f.periodicDeploymentManager.getProcessStates(processName).futureValue.value shouldBe List.empty
  }
}
