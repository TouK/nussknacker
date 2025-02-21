package pl.touk.nussknacker.ui.process.periodic.flink

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.exceptions.TestFailedException
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.{Inside, OptionValues}
import pl.touk.nussknacker.engine.api.component.NodesDeploymentData
import pl.touk.nussknacker.engine.api.deployment.DeploymentUpdateStrategy.StateRestoringStrategy
import pl.touk.nussknacker.engine.api.deployment.DeploymentUpdateStrategy.StateRestoringStrategy.RestoreStateFromReplacedJobSavepoint
import pl.touk.nussknacker.engine.api.deployment.ProcessStateDefinitionManager.ScenarioStatusWithScenarioContext
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.scheduler.services.{EmptyListener, ProcessConfigEnricher}
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus.ProblemStateStatus
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessIdWithName, ProcessName, VersionId}
import pl.touk.nussknacker.engine.api.{MetaData, ProcessVersion, StreamMetaData}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.{DeploymentData, User}
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.test.base.db.WithHsqlDbTesting
import pl.touk.nussknacker.test.utils.domain.TestFactory
import pl.touk.nussknacker.test.utils.domain.TestFactory.newWriteProcessRepository
import pl.touk.nussknacker.test.utils.scalas.DBIOActionValues
import pl.touk.nussknacker.ui.process.deployment.{CommonCommandData, RunDeploymentCommand, TestDeploymentServiceFactory}
import pl.touk.nussknacker.ui.process.periodic.PeriodicProcessService.PeriodicScenarioStatus
import pl.touk.nussknacker.ui.process.periodic.PeriodicStateStatus.{ScheduledStatus, WaitingForScheduleStatus}
import pl.touk.nussknacker.ui.process.periodic._
import pl.touk.nussknacker.ui.process.periodic.cron.CronSchedulePropertyExtractor
import pl.touk.nussknacker.ui.process.periodic.flink.db.InMemPeriodicProcessesRepository
import pl.touk.nussknacker.ui.process.periodic.model.{PeriodicProcessDeploymentId, PeriodicProcessDeploymentStatus}
import pl.touk.nussknacker.ui.process.repository.DBIOActionRunner
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.{CreateProcessAction, ProcessCreated}
import pl.touk.nussknacker.ui.security.api.LoggedUser

import java.time.{Clock, LocalDateTime, ZoneOffset}
import java.util.UUID

class PeriodicDeploymentManagerTest
    extends AnyFunSuite
    with Matchers
    with ScalaFutures
    with OptionValues
    with Inside
    with TableDrivenPropertyChecks
    with PatientScalaFutures
    with DBIOActionValues
    with WithHsqlDbTesting {

  protected implicit val freshnessPolicy: DataFreshnessPolicy = DataFreshnessPolicy.Fresh

  private implicit val user: LoggedUser = TestFactory.adminUser("user")

  import TestDeploymentServiceFactory._
  import org.scalatest.LoneElement._

  override protected def dbioRunner: DBIOActionRunner = DBIOActionRunner(testDbRef)

  private val writeProcessRepository = newWriteProcessRepository(testDbRef, clock)

  private val processName = ProcessName("test")

  private val updateStrategy = DeploymentUpdateStrategy.ReplaceDeploymentWithSameScenarioName(
    StateRestoringStrategy.RestoreStateFromReplacedJobSavepoint
  )

  class Fixture(executionConfig: PeriodicExecutionConfig = PeriodicExecutionConfig()) {
    val repository =
      new InMemPeriodicProcessesRepository(processingType = TestDeploymentServiceFactory.processingType.stringify)
    val delegateDeploymentManagerStub          = new DeploymentManagerStub
    val scheduledExecutionPerformerStub        = new ScheduledExecutionPerformerStub
    val preparedDeploymentData: DeploymentData = DeploymentData.withDeploymentId(UUID.randomUUID().toString)

    val periodicProcessService = new PeriodicProcessService(
      delegateDeploymentManager = delegateDeploymentManagerStub,
      scheduledExecutionPerformer = scheduledExecutionPerformerStub,
      periodicProcessesRepository = repository,
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

    def saveScenario(cronProperty: String = "0 0 * * * ?"): ProcessVersion = {
      val scenario = PeriodicProcessGen.buildCanonicalProcess(cronProperty)
      saveScenario(scenario)
    }

    def saveScenario(scenario: CanonicalProcess): ProcessVersion = {
      val action = CreateProcessAction(
        processName = processName,
        category = "Category1",
        canonicalProcess = scenario,
        processingType = TestDeploymentServiceFactory.processingType.stringify,
        isFragment = false,
        forwardedUserName = None
      )
      val ProcessCreated(processId, versionId) =
        writeProcessRepository.saveNewProcess(action).dbioActionValues.value

      ProcessVersion(
        versionId = versionId,
        processName = processName,
        processId = processId,
        labels = List.empty,
        user = "test user",
        modelVersion = None
      )
    }

    // TODO: Instead of using PeriodicDeploymentManager directly, me should use DeploymentService/ScenarioStateProvider
    //       Thanks to that, we will see the process from user perspective - real scenarios statuses etc/
    val periodicDeploymentManager = new PeriodicDeploymentManager(
      delegate = delegateDeploymentManagerStub,
      service = periodicProcessService,
      periodicProcessesRepository = repository,
      schedulePropertyExtractor = CronSchedulePropertyExtractor(),
      toClose = () => (),
    )

    private val deploymentServiceFactory = new TestDeploymentServiceFactory(testDbRef)
    private val services                 = deploymentServiceFactory.create(periodicDeploymentManager)

    def schedule(id: ProcessId): Unit = services.deploymentService
      .processCommand(
        RunDeploymentCommand(
          CommonCommandData(ProcessIdWithName(id, processName), None, user),
          RestoreStateFromReplacedJobSavepoint,
          NodesDeploymentData.empty
        )
      )
      .futureValue
      .futureValue

    def getSingleActiveDeploymentId(id: ProcessId): PeriodicProcessDeploymentId = inside(getScenarioStatus(id)) {
      case periodic: PeriodicScenarioStatus =>
        periodic.activeDeploymentsStatuses.loneElement.deploymentId
    }

    def getAllowedActions(
        scenarioStatus: StateStatus
    ): Set[ScenarioActionName] = {
      periodicDeploymentManager.processStateDefinitionManager
        .statusActions(
          ScenarioStatusWithScenarioContext(
            scenarioStatus = scenarioStatus,
            deployedVersionId = None,
            currentlyPresentedVersionId = Some(VersionId.initialVersionId)
          )
        )
    }

    def getScenarioStatus(id: ProcessId): StateStatus =
      services.scenarioStatusProvider.getScenarioStatus(ProcessIdWithName(id, processName)).futureValue

  }

  implicit class ScenarioStatusOps(scenarioStatus: StateStatus) {

    // We simulate logic that is available in PeriodicProcessStateDefinitionManager where we extract PeriodicProcessStatusWithMergedStatus.mergedStatus
    // or fallback to no extraction to handle both statuses returned by DM and by core (ScenarioStatusProvider)
    // to get a scenario status domain representation that is a SimpleStateStatus or ScheduledStatus
    def mergedStatus: StateStatus =
      inside(scenarioStatus) {
        case periodic: PeriodicScenarioStatus =>
          periodic.mergedStatus
        case other: StateStatus =>
          other
      }

  }

  test("getScenarioStatus - should return not deployed for no job") {
    val f       = new Fixture
    val version = f.saveScenario()

    val state = f.getScenarioStatus(version.processId).mergedStatus

    state shouldEqual SimpleStateStatus.NotDeployed
  }

  test("getScenarioStatus - should return not deployed for scenario with different processing type") {
    val f       = new Fixture
    val version = f.saveScenario()
    f.repository.addActiveProcess(processName, PeriodicProcessDeploymentStatus.Scheduled, processingType = "other")

    val state = f.getScenarioStatus(version.processId).mergedStatus

    state shouldEqual SimpleStateStatus.NotDeployed
  }

  test("getScenarioStatuss - should be scheduled when scenario scheduled and no job on Flink") {
    val f = new Fixture

    val version = f.saveScenario()
    f.schedule(version.processId)

    val scenarioStatus = f.getScenarioStatus(version.processId)
    scenarioStatus.mergedStatus shouldBe a[ScheduledStatus]
    f.getAllowedActions(scenarioStatus) shouldBe Set(
      ScenarioActionName.Cancel,
      ScenarioActionName.Deploy,
    )
    f.getScenarioStatus(version.processId).mergedStatus shouldBe a[ScheduledStatus]
  }

  test("getScenarioStatus - should be scheduled when scenario scheduled and job finished on Flink") {
    val f       = new Fixture
    val version = f.saveScenario()
    f.schedule(version.processId)
    val deploymentId = f.getSingleActiveDeploymentId(version.processId)
    f.delegateDeploymentManagerStub.setDeploymentStatus(processName, SimpleStateStatus.Finished, Some(deploymentId))

    val scenarioStatus = f.getScenarioStatus(version.processId)
    scenarioStatus.mergedStatus shouldBe a[ScheduledStatus]
    f.getAllowedActions(scenarioStatus) shouldBe Set(
      ScenarioActionName.Cancel,
      ScenarioActionName.Deploy
    )
  }

  test("getScenarioStatus - should be finished when scenario finished and job finished on Flink") {
    val f = new Fixture
    // We use repository/periodicDeploymentManager directly because run deployment won't accept date in past
    val periodicProcessId = f.repository.addOnlyProcess(processName, CronScheduleProperty("0 0 0 1 1 ? 1970"))
    val deploymentId = f.repository.addOnlyDeployment(
      periodicProcessId,
      PeriodicProcessDeploymentStatus.Finished,
      LocalDateTime.ofEpochSecond(0, 0, ZoneOffset.UTC)
    )
    f.delegateDeploymentManagerStub.setDeploymentStatus(processName, SimpleStateStatus.Finished, Some(deploymentId))
    f.periodicProcessService.deactivate(processName).futureValue

    val scenarioStatus =
      f.periodicDeploymentManager
        .getScenarioDeploymentsStatuses(processName)
        .futureValue
        .value
        .loneElement
        .status

    scenarioStatus.mergedStatus shouldBe SimpleStateStatus.Finished
    val allowedActions = f.getAllowedActions(scenarioStatus)
    allowedActions shouldBe Set(ScenarioActionName.Deploy, ScenarioActionName.Archive, ScenarioActionName.Rename)
  }

  test("getScenarioStatus - should be running when scenario deployed and job running on Flink") {
    val f       = new Fixture
    val version = f.saveScenario()
    f.schedule(version.processId)
    val deploymentId = f.getSingleActiveDeploymentId(version.processId)
    f.repository.markDeployed(deploymentId)
    f.delegateDeploymentManagerStub.setDeploymentStatus(processName, SimpleStateStatus.Running, Some(deploymentId))

    val scenarioStatus = f.getScenarioStatus(version.processId)
    scenarioStatus.mergedStatus shouldBe SimpleStateStatus.Running
    f.getAllowedActions(scenarioStatus) shouldBe Set(
      ScenarioActionName.Cancel
    )
  }

  test("getScenarioStatus - should be waiting for reschedule if job finished on Flink but scenario is still deployed") {
    val f       = new Fixture
    val version = f.saveScenario()
    f.schedule(version.processId)
    val deploymentId = f.getSingleActiveDeploymentId(version.processId)
    f.repository.markDeployed(deploymentId)
    f.delegateDeploymentManagerStub.setDeploymentStatus(processName, SimpleStateStatus.Finished, Some(deploymentId))

    val scenarioStatus = f.getScenarioStatus(version.processId)
    scenarioStatus.mergedStatus shouldBe WaitingForScheduleStatus
    f.getAllowedActions(scenarioStatus) shouldBe Set(
      ScenarioActionName.Cancel
    )
  }

  test("getScenarioStatus - should be failed after unsuccessful deployment") {
    val f       = new Fixture
    val version = f.saveScenario()
    f.schedule(version.processId)
    val deploymentId = f.getSingleActiveDeploymentId(version.processId)
    f.repository.markFailed(deploymentId)

    val scenarioStatus = f.getScenarioStatus(version.processId)
    scenarioStatus.mergedStatus shouldBe ProblemStateStatus.Failed
    f.getAllowedActions(scenarioStatus) shouldBe Set(
      ScenarioActionName.Cancel
    )
  }

  test("deploy - should fail for invalid periodic property") {
    val f = new Fixture

    val emptyScenario = CanonicalProcess(MetaData("fooId", StreamMetaData()), List.empty)
    val version       = f.saveScenario(emptyScenario)

    val validateResult = f.periodicDeploymentManager
      .processCommand(DMValidateScenarioCommand(version, DeploymentData.empty, emptyScenario, updateStrategy))
      .failed
      .futureValue
    validateResult shouldBe a[PeriodicProcessException]

    val deploymentResult = f.periodicDeploymentManager
      .processCommand(DMRunDeploymentCommand(version, DeploymentData.empty, emptyScenario, updateStrategy))
      .failed
      .futureValue
    deploymentResult shouldBe a[PeriodicProcessException]
  }

  test("deploy - should schedule periodic scenario") {
    val f       = new Fixture
    val version = f.saveScenario()
    f.schedule(version.processId)

    f.repository.processEntities.loneElement.active shouldBe true
    f.repository.deploymentEntities.loneElement.status shouldBe PeriodicProcessDeploymentStatus.Scheduled
  }

  test("deploy - should not cancel current schedule after trying to deploy with past date") {
    val f       = new Fixture
    val version = f.saveScenario()
    f.repository.addActiveProcess(processName, PeriodicProcessDeploymentStatus.Scheduled)

    f.periodicDeploymentManager
      .processCommand(
        DMRunDeploymentCommand(
          version,
          DeploymentData.empty,
          PeriodicProcessGen.buildCanonicalProcess("0 0 0 ? * * 2000"),
          updateStrategy
        )
      )
      .failed
      .futureValue

    f.repository.processEntities.loneElement.active shouldBe true
    f.repository.deploymentEntities.loneElement.status shouldBe PeriodicProcessDeploymentStatus.Scheduled
  }

  test("deploy - should cancel existing scenario if already scheduled") {
    val f       = new Fixture
    val version = f.saveScenario()
    f.schedule(version.processId)

    f.schedule(version.processId)

    f.repository.processEntities should have size 2
    f.repository.processEntities.map(_.active) shouldBe List(false, true)
  }

  test("should get status of failed job") {
    val f       = new Fixture
    val version = f.saveScenario()
    f.schedule(version.processId)
    val deploymentId = f.getSingleActiveDeploymentId(version.processId)
    f.repository.markDeployed(deploymentId)
    f.delegateDeploymentManagerStub.setDeploymentStatus(processName, ProblemStateStatus.Failed, Some(deploymentId))

    val scenarioStatus = f.getScenarioStatus(version.processId)
    scenarioStatus.mergedStatus shouldBe ProblemStateStatus.Failed
    f.getAllowedActions(scenarioStatus) shouldBe Set(
      ScenarioActionName.Cancel
    )
  }

  test("should redeploy failed scenario") {
    val f       = new Fixture
    val version = f.saveScenario()
    f.schedule(version.processId)
    val deploymentId = f.getSingleActiveDeploymentId(version.processId)
    f.repository.markDeployed(deploymentId)
    f.delegateDeploymentManagerStub.setDeploymentStatus(processName, ProblemStateStatus.Failed, Some(deploymentId))
    val statusBeforeRedeploy = f.getScenarioStatus(version.processId)
    statusBeforeRedeploy.mergedStatus shouldBe ProblemStateStatus.Failed
    f.getAllowedActions(statusBeforeRedeploy) shouldBe Set(
      ScenarioActionName.Cancel
    ) // redeploy is blocked in GUI but API allows it

    f.periodicDeploymentManager
      .processCommand(
        DMRunDeploymentCommand(
          version,
          f.preparedDeploymentData,
          PeriodicProcessGen.buildCanonicalProcess(),
          updateStrategy
        )
      )
      .futureValue

    f.repository.processEntities.map(_.active) shouldBe List(false, true)
    f.repository.deploymentEntities.map(_.status) shouldBe List(
      PeriodicProcessDeploymentStatus.Failed,
      PeriodicProcessDeploymentStatus.Scheduled
    )
    val scenarioStatusAfterRedeploy = f.getScenarioStatus(version.processId)
    // Previous job is still visible as Failed.
    scenarioStatusAfterRedeploy.mergedStatus shouldBe a[ScheduledStatus]
    f.getAllowedActions(scenarioStatusAfterRedeploy) shouldBe Set(
      ScenarioActionName.Cancel,
      ScenarioActionName.Deploy
    )
  }

  test("should redeploy scheduled scenario") {
    val f       = new Fixture
    val version = f.saveScenario()
    f.schedule(version.processId)

    f.periodicDeploymentManager
      .processCommand(
        DMRunDeploymentCommand(
          version,
          f.preparedDeploymentData,
          PeriodicProcessGen.buildCanonicalProcess(),
          updateStrategy
        )
      )
      .futureValue

    f.repository.processEntities.map(_.active) shouldBe List(false, true)
    f.repository.deploymentEntities.map(_.status) shouldBe List(
      PeriodicProcessDeploymentStatus.Scheduled,
      PeriodicProcessDeploymentStatus.Scheduled
    )
  }

  test("should redeploy running scenario") {
    val f       = new Fixture
    val version = f.saveScenario()
    f.schedule(version.processId)
    val deploymentId = f.getSingleActiveDeploymentId(version.processId)
    f.repository.markDeployed(deploymentId)
    f.delegateDeploymentManagerStub.setDeploymentStatus(processName, SimpleStateStatus.Running, Some(deploymentId))
    val scenarioStatus = f.getScenarioStatus(version.processId)
    f.getAllowedActions(scenarioStatus) shouldBe Set(
      ScenarioActionName.Cancel
    ) // redeploy is blocked in GUI but API allows it

    f.periodicDeploymentManager
      .processCommand(
        DMRunDeploymentCommand(
          version,
          f.preparedDeploymentData,
          PeriodicProcessGen.buildCanonicalProcess(),
          updateStrategy
        )
      )
      .futureValue

    f.repository.processEntities.map(_.active) shouldBe List(false, true)
    f.repository.deploymentEntities.map(_.status) shouldBe List(
      PeriodicProcessDeploymentStatus.Deployed,
      PeriodicProcessDeploymentStatus.Scheduled
    )
  }

  test("should redeploy finished scenario") {
    val f       = new Fixture
    val version = f.saveScenario()
    f.schedule(version.processId)
    val deploymentId = f.getSingleActiveDeploymentId(version.processId)
    f.repository.markDeployed(deploymentId)
    f.delegateDeploymentManagerStub.setDeploymentStatus(processName, SimpleStateStatus.Finished, Some(deploymentId))
    val scenarioStatus = f.getScenarioStatus(version.processId)
    f.getAllowedActions(scenarioStatus) shouldBe Set(
      ScenarioActionName.Cancel
    ) // redeploy is blocked in GUI but API allows it

    f.periodicDeploymentManager
      .processCommand(
        DMRunDeploymentCommand(
          version,
          f.preparedDeploymentData,
          PeriodicProcessGen.buildCanonicalProcess(),
          updateStrategy
        )
      )
      .futureValue

    f.repository.processEntities.map(_.active) shouldBe List(false, true)
    f.repository.deploymentEntities.map(_.status) shouldBe List(
      PeriodicProcessDeploymentStatus.Finished,
      PeriodicProcessDeploymentStatus.Scheduled
    )
  }

  test("should cancel failed job after RescheduleActor handles finished") {
    val f       = new Fixture
    val version = f.saveScenario()
    f.schedule(version.processId)
    val deploymentId = f.getSingleActiveDeploymentId(version.processId)
    f.repository.markDeployed(deploymentId)
    f.delegateDeploymentManagerStub.setDeploymentStatus(processName, ProblemStateStatus.Failed, Some(deploymentId))

    // this one is cyclically called by RescheduleActor
    f.periodicProcessService.handleFinished.futureValue

    f.getScenarioStatus(version.processId).mergedStatus shouldEqual ProblemStateStatus.Failed
    f.repository.deploymentEntities.loneElement.status shouldBe PeriodicProcessDeploymentStatus.Failed
    f.repository.processEntities.loneElement.active shouldBe true

    f.periodicDeploymentManager.processCommand(DMCancelScenarioCommand(processName, User("test", "Tester"))).futureValue

    f.repository.processEntities.loneElement.active shouldBe false
    f.repository.deploymentEntities.loneElement.status shouldBe PeriodicProcessDeploymentStatus.Failed

    f.getScenarioStatus(version.processId).mergedStatus shouldEqual SimpleStateStatus.Canceled
  }

  test("should reschedule failed job after RescheduleActor handles finished when configured") {
    val f       = new Fixture(executionConfig = PeriodicExecutionConfig(rescheduleOnFailure = true))
    val version = f.saveScenario()
    f.schedule(version.processId)
    val deploymentId = f.getSingleActiveDeploymentId(version.processId)
    f.repository.markDeployed(deploymentId)
    f.delegateDeploymentManagerStub.setDeploymentStatus(processName, ProblemStateStatus.Failed, Some(deploymentId))

    // this one is cyclically called by RescheduleActor
    f.periodicProcessService.handleFinished.futureValue

    f.getScenarioStatus(version.processId).mergedStatus shouldBe a[ScheduledStatus]
    f.repository.deploymentEntities.map(_.status) shouldBe List(
      PeriodicProcessDeploymentStatus.Failed,
      PeriodicProcessDeploymentStatus.Scheduled
    )
    f.repository.processEntities.loneElement.active shouldBe true
  }

  test("should cancel failed job before RescheduleActor handles finished") {
    val f       = new Fixture
    val version = f.saveScenario()
    f.schedule(version.processId)
    val deploymentId = f.getSingleActiveDeploymentId(version.processId)
    f.repository.markDeployed(deploymentId)
    f.delegateDeploymentManagerStub.setDeploymentStatus(processName, ProblemStateStatus.Failed, Some(deploymentId))

    f.periodicDeploymentManager.processCommand(DMCancelScenarioCommand(processName, User("test", "Tester"))).futureValue

    f.repository.processEntities.loneElement.active shouldBe false
    f.repository.deploymentEntities.loneElement.status shouldBe PeriodicProcessDeploymentStatus.Failed
    f.getScenarioStatus(version.processId).mergedStatus shouldEqual SimpleStateStatus.Canceled
  }

  test("should cancel failed scenario after disappeared from Flink console") {
    val f       = new Fixture
    val version = f.saveScenario()
    f.schedule(version.processId)
    val deploymentId = f.getSingleActiveDeploymentId(version.processId)
    f.repository.markDeployed(deploymentId)
    f.delegateDeploymentManagerStub.setDeploymentStatus(processName, ProblemStateStatus.Failed, Some(deploymentId))

    // this one is cyclically called by RescheduleActor
    f.periodicProcessService.handleFinished.futureValue

    // after some time Flink stops returning job status
    f.delegateDeploymentManagerStub.jobStatus.clear()

    f.getScenarioStatus(version.processId).mergedStatus shouldEqual ProblemStateStatus.Failed
    f.repository.deploymentEntities.loneElement.status shouldBe PeriodicProcessDeploymentStatus.Failed
    f.repository.processEntities.loneElement.active shouldBe true

    f.periodicDeploymentManager.processCommand(DMCancelScenarioCommand(processName, User("test", "Tester"))).futureValue

    f.repository.processEntities.loneElement.active shouldBe false
    f.repository.deploymentEntities.loneElement.status shouldBe PeriodicProcessDeploymentStatus.Failed
    f.getScenarioStatus(version.processId).mergedStatus shouldBe SimpleStateStatus.Canceled
  }

//  test("should take into account only latest deployments of active schedules during merged status computation") {
//    val f                    = new Fixture
//    val processId            = f.repository.addOnlyProcess(processName)
//    val firstDeploymentRunAt = LocalDateTime.of(2023, 1, 1, 10, 0)
//    f.repository.addOnlyDeployment(processId, PeriodicProcessDeploymentStatus.Failed, firstDeploymentRunAt)
//    f.repository.addOnlyDeployment(
//      processId,
//      PeriodicProcessDeploymentStatus.Finished,
//      firstDeploymentRunAt.plusHours(1)
//    )
//
//    f.getScenarioStatus.mergedStatus shouldBe WaitingForScheduleStatus
//  }
//
//  test(
//    "should take into account only latest inactive schedule request (periodic process) during merged status computation"
//  ) {
//    val f              = new Fixture
//    val firstProcessId = f.repository.addOnlyProcess(processName)
//    f.repository.addOnlyDeployment(firstProcessId, PeriodicProcessDeploymentStatus.Failed)
//    f.repository.markInactive(firstProcessId)
//
//    val secProcessId = f.repository.addOnlyProcess(processName)
//    f.repository.addOnlyDeployment(secProcessId, PeriodicProcessDeploymentStatus.Finished)
//    f.repository.markInactive(secProcessId)
//
//    f.getScenarioStatus.mergedStatus shouldBe SimpleStateStatus.Finished
//  }

}
