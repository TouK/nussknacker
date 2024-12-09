package pl.touk.nussknacker.engine.management.periodic

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.{Inside, OptionValues}
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.management.periodic.db.InMemPeriodicProcessesRepository.getLatestDeploymentQueryCount
import pl.touk.nussknacker.engine.management.periodic.db.PeriodicProcessesRepositoryCachingDecorator
import pl.touk.nussknacker.engine.management.periodic.model.PeriodicProcessDeploymentStatus
import pl.touk.nussknacker.engine.management.periodic.service.{
  DefaultAdditionalDeploymentDataProvider,
  EmptyListener,
  ProcessConfigEnricher
}
import pl.touk.nussknacker.test.PatientScalaFutures

import java.time.Clock
import java.util.UUID
import scala.concurrent.duration.DurationInt

class PeriodicProcessesCachingRepositoryTest
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
    val repository                    = new db.InMemPeriodicProcessesRepository(processingType = "testProcessingType")
    val delegateDeploymentManagerStub = new DeploymentManagerStub
    val jarManagerStub                = new JarManagerStub
    val preparedDeploymentData        = DeploymentData.withDeploymentId(UUID.randomUUID().toString)

    val periodicProcessService = new PeriodicProcessService(
      delegateDeploymentManager = delegateDeploymentManagerStub,
      jarManager = jarManagerStub,
      scheduledProcessesRepository = new PeriodicProcessesRepositoryCachingDecorator(repository, 10 seconds),
      periodicProcessListener = EmptyListener,
      additionalDeploymentDataProvider = DefaultAdditionalDeploymentDataProvider,
      deploymentRetryConfig = DeploymentRetryConfig(),
      executionConfig = executionConfig,
      processConfigEnricher = ProcessConfigEnricher.identity,
      clock = Clock.systemDefaultZone(),
      new ProcessingTypeActionServiceStub,
      Map.empty
    )

    val periodicDeploymentManager = new PeriodicDeploymentManager(
      delegate = delegateDeploymentManagerStub,
      service = periodicProcessService,
      repository = repository,
      schedulePropertyExtractor = CronSchedulePropertyExtractor(),
      toClose = () => ()
    )

  }

  test("getStatusDetails - should perform 2 db queries for N periodic processes when using cache") {
    val f = new Fixture
    val n = 10

    for (i <- 1 to n) {
      val deploymentId = f.repository.addActiveProcess(processName(i), PeriodicProcessDeploymentStatus.Deployed)
      f.delegateDeploymentManagerStub.setStateStatus(SimpleStateStatus.Running, Some(deploymentId))
    }

    getLatestDeploymentQueryCount.set(0)

    implicit val freshnessPolicy: DataFreshnessPolicy = DataFreshnessPolicy.CanBeCached

    for (i <- 1 to n) {
      f.periodicProcessService.getStatusDetails(processName(i)).futureValue
    }

    getLatestDeploymentQueryCount.get() shouldEqual 2
  }

  test("getStatusDetails - should perform 2*N db queries for N periodic processes when not using cache") {
    val f = new Fixture
    val n = 10

    for (i <- 1 to n) {
      val deploymentId = f.repository.addActiveProcess(processName(i), PeriodicProcessDeploymentStatus.Deployed)
      f.delegateDeploymentManagerStub.setStateStatus(SimpleStateStatus.Running, Some(deploymentId))
    }

    getLatestDeploymentQueryCount.set(0)

    implicit val freshnessPolicy: DataFreshnessPolicy = DataFreshnessPolicy.Fresh

    for (i <- 1 to n) {
      f.periodicProcessService.getStatusDetails(processName(i)).futureValue
    }

    getLatestDeploymentQueryCount.get() shouldEqual 2 * n
  }

}
