package pl.touk.nussknacker.ui.process.periodic

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.{Inside, OptionValues}
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.process.periodic.flink.{DeploymentManagerStub, ScheduledExecutionPerformerStub}
import pl.touk.nussknacker.ui.process.periodic.flink.db.InMemPeriodicProcessesRepository
import pl.touk.nussknacker.ui.process.periodic.flink.db.InMemPeriodicProcessesRepository.getLatestDeploymentQueryCount
import pl.touk.nussknacker.ui.process.periodic.model.PeriodicProcessDeploymentStatus
import pl.touk.nussknacker.engine.api.deployment.scheduler.services.{EmptyListener, ProcessConfigEnricher}
import pl.touk.nussknacker.ui.process.periodic.cron.CronSchedulePropertyExtractor
import pl.touk.nussknacker.ui.process.repository.{FetchingProcessRepository, PeriodicProcessesRepository}

import java.time.Clock
import java.util.UUID
import scala.concurrent.Future

class PeriodicProcessesFetchingTest
    extends AnyFunSuite
    with Matchers
    with ScalaFutures
    with OptionValues
    with Inside
    with TableDrivenPropertyChecks
    with PatientScalaFutures {

  protected implicit val freshnessPolicy: DataFreshnessPolicy = DataFreshnessPolicy.Fresh

  import scala.concurrent.ExecutionContext.Implicits.global

  private def processName(n: Int) = ProcessName(s"test$n")

  class Fixture(executionConfig: PeriodicExecutionConfig = PeriodicExecutionConfig()) {
    val processingType                  = "testProcessingType"
    val repository                      = new InMemPeriodicProcessesRepository(processingType)
    val delegateDeploymentManagerStub   = new DeploymentManagerStub
    val scheduledExecutionPerformerStub = new ScheduledExecutionPerformerStub
    val preparedDeploymentData          = DeploymentData.withDeploymentId(UUID.randomUUID().toString)

    val periodicProcessService = new PeriodicProcessService(
      delegateDeploymentManager = delegateDeploymentManagerStub,
      scheduledExecutionPerformer = scheduledExecutionPerformerStub,
      periodicProcessesRepository = repository,
      periodicProcessListener = EmptyListener,
      additionalDeploymentDataProvider = DefaultAdditionalDeploymentDataProvider,
      deploymentRetryConfig = DeploymentRetryConfig(),
      executionConfig = executionConfig,
      maxFetchedPeriodicScenarioActivities = Some(200),
      processConfigEnricher = ProcessConfigEnricher.identity,
      clock = Clock.systemDefaultZone(),
      new ProcessingTypeActionServiceStub,
      Map.empty
    )

    val periodicDeploymentManager = new PeriodicDeploymentManager(
      delegate = delegateDeploymentManagerStub,
      service = periodicProcessService,
      periodicProcessesRepository = repository,
      schedulePropertyExtractor = CronSchedulePropertyExtractor(),
      toClose = () => ()
    )

  }

  test(
    "getStatusDetails - should perform 2*N db queries for N periodic processes when fetching statuses individually"
  ) {
    val f = new Fixture
    val n = 10

    f.delegateDeploymentManagerStub.setEmptyStateStatus()

    for (i <- 1 to n) {
      val deploymentId =
        f.repository.addActiveProcess(processName(i), PeriodicProcessDeploymentStatus.Deployed)
      f.delegateDeploymentManagerStub.addStateStatus(processName(i), SimpleStateStatus.Running, Some(deploymentId))
    }

    getLatestDeploymentQueryCount.set(0)

    implicit val freshnessPolicy: DataFreshnessPolicy = DataFreshnessPolicy.CanBeCached

    for (i <- 1 to n) {
      f.periodicProcessService.getMergedStatusDetails(processName(i)).futureValue
    }

    getLatestDeploymentQueryCount.get() shouldEqual 2 * n
  }

  test(
    "getAllProcessesStates - should perform 2 db queries for N periodic processes when fetching all at once"
  ) {
    val f = new Fixture
    val n = 10

    f.delegateDeploymentManagerStub.setEmptyStateStatus()

    for (i <- 1 to n) {
      val deploymentId =
        f.repository.addActiveProcess(processName(i), PeriodicProcessDeploymentStatus.Deployed)
      f.delegateDeploymentManagerStub.addStateStatus(processName(i), SimpleStateStatus.Running, Some(deploymentId))
    }

    getLatestDeploymentQueryCount.set(0)

    implicit val freshnessPolicy: DataFreshnessPolicy = DataFreshnessPolicy.Fresh

    val statuses = f.periodicProcessService.stateQueryForAllScenariosSupport
      .asInstanceOf[StateQueryForAllScenariosSupported]
      .getAllProcessesStates()
      .futureValue
      .value

    statuses.size shouldEqual n

    getLatestDeploymentQueryCount.get() shouldEqual 2
  }

}
