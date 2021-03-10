package pl.touk.nussknacker.engine.management.periodic

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.ProcessListener
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.management.FlinkStateStatus
import pl.touk.nussknacker.engine.management.periodic.db.ScheduledRunDetails
import pl.touk.nussknacker.engine.management.periodic.service.{AdditionalDeploymentDataProvider, DefaultAdditionalDeploymentDataProvider, DeployedEvent, EmptyListener, FailedEvent, FinishedEvent, PeriodicProcessEvent, PeriodicProcessListener, ScheduledEvent}
import pl.touk.nussknacker.test.PatientScalaFutures

import scala.collection.mutable.ArrayBuffer
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
    val events = new ArrayBuffer[PeriodicProcessEvent]()
    val additionalData = Map("testMap" -> "testValue")
    val periodicProcessService = new PeriodicProcessService(
      delegateProcessManager = delegateProcessManagerStub,
      jarManager = jarManagerStub,
      scheduledProcessesRepository = repository,
      new PeriodicProcessListener {
        override def onPeriodicProcessEvent: PartialFunction[PeriodicProcessEvent, Unit] = { case k => events.append(k) }
      },
      new AdditionalDeploymentDataProvider {
        override def prepareAdditionalData(runDetails: ScheduledRunDetails): Map[String, String] =
          additionalData + ("runId" -> runDetails.processDeploymentId.value.toString)
      }
    )
  }

  // Flink job could disappear from Flink console.
  test("handleFinished - should reschedule process if Flink job is missing") {
    val f = new Fixture
    f.repository.addActiveProcess(processName, PeriodicProcessDeploymentStatus.Deployed)

    f.periodicProcessService.handleFinished.futureValue

    val processEntity = f.repository.processEntities.loneElement
    processEntity.active shouldBe true
    f.repository.deploymentEntities should have size 2
    f.repository.deploymentEntities.map(_.status) shouldBe List(PeriodicProcessDeploymentStatus.Finished, PeriodicProcessDeploymentStatus.Scheduled)

    val expectedDetails = ScheduledRunDetails(processEntity, f.repository.deploymentEntities.head)
    f.events.toList shouldBe List(FinishedEvent(expectedDetails, None), ScheduledEvent(processEntity.id, f.repository.deploymentEntities.last.runAt))
  }

  test("handleFinished - should reschedule for finished Flink job") {
    val f = new Fixture
    f.repository.addActiveProcess(processName, PeriodicProcessDeploymentStatus.Deployed)
    f.delegateProcessManagerStub.setStateStatus(FlinkStateStatus.Finished)

    f.periodicProcessService.handleFinished.futureValue

    val processEntity = f.repository.processEntities.loneElement
    processEntity.active shouldBe true
    f.repository.deploymentEntities should have size 2
    f.repository.deploymentEntities.map(_.status) shouldBe List(PeriodicProcessDeploymentStatus.Finished, PeriodicProcessDeploymentStatus.Scheduled)

    val expectedDetails = ScheduledRunDetails(processEntity, f.repository.deploymentEntities.head)
    f.events.toList shouldBe List(FinishedEvent(expectedDetails, f.delegateProcessManagerStub.jobStatus), ScheduledEvent(processEntity.id, f.repository.deploymentEntities.last.runAt))
  }

  test("handleFinished - should mark as failed for failed Flink job") {
    val f = new Fixture
    f.repository.addActiveProcess(processName, PeriodicProcessDeploymentStatus.Deployed)
    f.delegateProcessManagerStub.setStateStatus(FlinkStateStatus.Failed)

    f.periodicProcessService.handleFinished.futureValue

    // Process is still active and has to be manually canceled by user.
    val processEntity = f.repository.processEntities.loneElement
    processEntity.active shouldBe true
    f.repository.deploymentEntities.loneElement.status shouldBe PeriodicProcessDeploymentStatus.Failed

    val expectedDetails = ScheduledRunDetails(processEntity, f.repository.deploymentEntities.head)
    f.events.toList shouldBe List(FailedEvent(expectedDetails, f.delegateProcessManagerStub.jobStatus))
  }

  test("deploy - should deploy and mark as so") {
    val f = new Fixture
    f.repository.addActiveProcess(processName, PeriodicProcessDeploymentStatus.Scheduled)
    val toSchedule = ScheduledRunDetails(f.repository.processEntities.loneElement, f.repository.deploymentEntities.loneElement)

    f.periodicProcessService.deploy(toSchedule).futureValue

    f.repository.deploymentEntities.loneElement.status shouldBe PeriodicProcessDeploymentStatus.Deployed

    val expectedDetails = ScheduledRunDetails(f.repository.processEntities.loneElement, f.repository.deploymentEntities.loneElement)
    f.events.toList shouldBe List(DeployedEvent(expectedDetails, None))

  }

  test("deploy - should handle failed deployment") {
    val f = new Fixture
    f.repository.addActiveProcess(processName, PeriodicProcessDeploymentStatus.Scheduled)
    f.jarManagerStub.deployWithJarFuture = Future.failed(new RuntimeException("Flink deploy error"))
    val toSchedule = ScheduledRunDetails(f.repository.processEntities.loneElement, f.repository.deploymentEntities.loneElement)

    f.periodicProcessService.deploy(toSchedule).futureValue

    f.repository.deploymentEntities.loneElement.status shouldBe PeriodicProcessDeploymentStatus.Failed

    val expectedDetails = ScheduledRunDetails(f.repository.processEntities.loneElement, f.repository.deploymentEntities.loneElement)
    f.events.toList shouldBe List(FailedEvent(expectedDetails, None))
  }
}
