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
import pl.touk.nussknacker.ui.process.periodic.flink.{DeploymentManagerStub, PeriodicDeploymentEngineHandlerStub}
import pl.touk.nussknacker.ui.process.periodic.flink.db.InMemPeriodicProcessesManager
import pl.touk.nussknacker.ui.process.periodic.flink.db.InMemPeriodicProcessesRepository.getLatestDeploymentQueryCount
import pl.touk.nussknacker.ui.process.periodic.model.PeriodicProcessDeploymentStatus
import pl.touk.nussknacker.engine.api.deployment.periodic.services.{EmptyListener, ProcessConfigEnricher}
import pl.touk.nussknacker.ui.process.periodic.cron.CronSchedulePropertyExtractor

import java.time.Clock
import java.util.UUID

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
    val processingType                = "testProcessingType"
    val periodicProcessesManager      = new InMemPeriodicProcessesManager(processingType)
    val delegateDeploymentManagerStub = new DeploymentManagerStub
    val engineHandlerStub             = new PeriodicDeploymentEngineHandlerStub
    val preparedDeploymentData        = DeploymentData.withDeploymentId(UUID.randomUUID().toString)

    val periodicProcessService = new PeriodicProcessService(
      delegateDeploymentManager = delegateDeploymentManagerStub,
      engineHandler = engineHandlerStub,
      periodicProcessesManager = periodicProcessesManager,
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
      periodicProcessesManager = periodicProcessesManager,
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
        f.periodicProcessesManager.addActiveProcess(processName(i), PeriodicProcessDeploymentStatus.Deployed)
      f.delegateDeploymentManagerStub.addStateStatus(processName(i), SimpleStateStatus.Running, Some(deploymentId))
    }

    getLatestDeploymentQueryCount.set(0)

    implicit val freshnessPolicy: DataFreshnessPolicy = DataFreshnessPolicy.CanBeCached

    for (i <- 1 to n) {
      f.periodicProcessService.getStatusDetails(processName(i)).futureValue
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
        f.periodicProcessesManager.addActiveProcess(processName(i), PeriodicProcessDeploymentStatus.Deployed)
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
