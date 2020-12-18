package pl.touk.nussknacker.engine.management.periodic

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.management.FlinkStateStatus
import pl.touk.nussknacker.test.PatientScalaFutures

import scala.concurrent.Future

class PeriodicProcessServiceTest extends FunSuite
  with Matchers
  with ScalaFutures
  with PatientScalaFutures {

  import org.scalatest.LoneElement._

  import scala.concurrent.ExecutionContext.Implicits.global

  private val processName = ProcessName("test")

  class Fixture {
    val repository = new db.InMemPeriodicProcessesRepository
    val delegateProcessManagerStub = new ProcessManagerStub
    val jarManagerStub = new JarManagerStub
    val periodicProcessService = new PeriodicProcessService(
      delegateProcessManager = delegateProcessManagerStub,
      jarManager = jarManagerStub,
      scheduledProcessesRepository = repository
    )
  }

  // Flink job could disappear from Flink console.
  test("handleFinished - should reschedule process if Flink job is missing") {
    val f = new Fixture
    f.repository.addActiveProcess(processName, PeriodicProcessDeploymentStatus.Deployed)

    f.periodicProcessService.handleFinished.futureValue

    f.repository.processEntities.loneElement.active shouldBe true
    f.repository.deploymentEntities should have size 2
    f.repository.deploymentEntities.map(_.status) shouldBe List(PeriodicProcessDeploymentStatus.Finished, PeriodicProcessDeploymentStatus.Scheduled)
  }

  test("handleFinished - should reschedule for finished Flink job") {
    val f = new Fixture
    f.repository.addActiveProcess(processName, PeriodicProcessDeploymentStatus.Deployed)
    f.delegateProcessManagerStub.setStateStatus(FlinkStateStatus.Finished)

    f.periodicProcessService.handleFinished.futureValue

    f.repository.processEntities.loneElement.active shouldBe true
    f.repository.deploymentEntities should have size 2
    f.repository.deploymentEntities.map(_.status) shouldBe List(PeriodicProcessDeploymentStatus.Finished, PeriodicProcessDeploymentStatus.Scheduled)
  }

  test("handleFinished - should mark as failed for failed Flink job") {
    val f = new Fixture
    f.repository.addActiveProcess(processName, PeriodicProcessDeploymentStatus.Deployed)
    f.delegateProcessManagerStub.setStateStatus(FlinkStateStatus.Failed)

    f.periodicProcessService.handleFinished.futureValue

    // Process is still active and has to be manually canceled by user.
    f.repository.processEntities.loneElement.active shouldBe true
    f.repository.deploymentEntities.loneElement.status shouldBe PeriodicProcessDeploymentStatus.Failed
  }

  test("deploy - should deploy and mark as so") {
    val f = new Fixture
    f.repository.addActiveProcess(processName, PeriodicProcessDeploymentStatus.Scheduled)
    val periodicProcessDeploymentId = f.repository.deploymentEntities.loneElement.id

    f.periodicProcessService.deploy(periodicProcessDeploymentId).futureValue

    f.repository.deploymentEntities.loneElement.status shouldBe PeriodicProcessDeploymentStatus.Deployed
  }

  test("deploy - should handle failed deployment") {
    val f = new Fixture
    f.repository.addActiveProcess(processName, PeriodicProcessDeploymentStatus.Scheduled)
    f.jarManagerStub.deployWithJarFuture = Future.failed(new RuntimeException("Flink deploy error"))
    val periodicProcessDeploymentId = f.repository.deploymentEntities.loneElement.id

    f.periodicProcessService.deploy(periodicProcessDeploymentId).futureValue

    f.repository.deploymentEntities.loneElement.status shouldBe PeriodicProcessDeploymentStatus.Failed
  }
}
