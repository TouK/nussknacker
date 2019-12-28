package pl.touk.nussknacker.engine.standalone.management

import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.StatusState.StateStatus
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.customs.deployment.{CustomStateStatus, ProcessStateCustomConfigurator}
import pl.touk.nussknacker.engine.standalone.api.DeploymentData
import pl.touk.nussknacker.test.PatientScalaFutures

import scala.concurrent.Future

class MultiInstanceStandaloneProcessClientSpec extends FunSuite with Matchers with PatientScalaFutures {

  val failClient = new StandaloneProcessClient {

    override def cancel(name: ProcessName): Future[Unit] = {
      name shouldBe id
      Future.failed(failure)
    }

    override def deploy(deploymentData: DeploymentData): Future[Unit] = {
      deploymentData.processVersion.processName shouldBe id
      Future.failed(failure)
    }

    override def findStatus(name: ProcessName): Future[Option[ProcessState]] = {
      name shouldBe id
      Future.failed(failure)
    }

    override def processStateConfigurator: ProcessStateConfigurator = ProcessStateCustomConfigurator
  }
  private val failure = new Exception("Fail")

  def processVersion(versionId: Option[Long]): Option[ProcessVersion] = versionId.map(id => ProcessVersion(id, ProcessName(""), "", None))

  def processState(deploymentId: DeploymentId, status: StateStatus, client: StandaloneProcessClient, versionId: Option[Long] = Option.empty, startTime: Option[Long] = Option.empty, errorMessage: Option[String] = Option.empty): ProcessState =
    ProcessState.custom(deploymentId, status, processVersion(versionId), startTime = startTime, errorMessage = errorMessage)

  test("Deployment should complete when all parts are successful") {
    val multiClient = new MultiInstanceStandaloneProcessClient(List(okClient(), okClient()))
    multiClient.deploy(DeploymentData("json", 1000, ProcessVersion.empty.copy(processName=id))).futureValue shouldBe (())
  }

  test("Deployment should fail when one part fails") {
    val multiClient = new MultiInstanceStandaloneProcessClient(List(okClient(), failClient))
    multiClient.deploy(DeploymentData("json", 1000, ProcessVersion.empty.copy(processName=id))).failed.futureValue shouldBe failure
  }

  test("Status should be none if no client returns status") {
    val multiClient = new MultiInstanceStandaloneProcessClient(List(okClient(), okClient()))
    multiClient.findStatus(id).futureValue shouldBe None
  }

  test("Status should be RUNNING if all clients running") {
    val consistentState = processState(jobId, CustomStateStatus.Running, okClient(), Some(1), Some(10000L))
    val multiClient = new MultiInstanceStandaloneProcessClient(List(
      okClient(Some(consistentState)),
      okClient(Some(consistentState))
    ))

    multiClient.findStatus(id).futureValue shouldBe Some(consistentState)
  }

  test("Status should be INCONSISTENT if one status unknown") {
    val multiClient = new MultiInstanceStandaloneProcessClient(List(
      okClient(),
      okClient(Some(processState(jobId, CustomStateStatus.Running, okClient(), Some(1))))
    ))

    val excepted = processState(jobId, CustomStateStatus.Failed, multiClient, errorMessage = Some("Inconsistent states between servers: empty; state: RUNNING, startTime: None"))
    multiClient.findStatus(id).futureValue shouldBe Some(excepted)
  }

  test("Status should be INCONSISTENT if status differ") {
    val multiClient = new MultiInstanceStandaloneProcessClient(List(
      okClient(Some(processState(jobId, CustomStateStatus.Running, okClient(), Some(1), Some(5000L)))),
      okClient(Some(processState(jobId, CustomStateStatus.Running, okClient(), Some(1))))
    ))

    val excepted = processState(jobId, CustomStateStatus.Failed, multiClient, errorMessage = Some("Inconsistent states between servers: state: RUNNING, startTime: 5000; state: RUNNING, startTime: None"))
    multiClient.findStatus(id).futureValue shouldBe Some(excepted)
  }

  test("Status should be FAIL if one status fails") {
    val multiClient = new MultiInstanceStandaloneProcessClient(List(okClient(), failClient))

    multiClient.findStatus(id).failed.futureValue shouldBe failure
  }

  private val id = ProcessName("id")
  private val jobId = DeploymentId("id")

  def okClient(status: Option[ProcessState] = None, expectedTime: Long = 1000) = new StandaloneProcessClient {

    override def cancel(name: ProcessName): Future[Unit] = {
      name shouldBe id
      Future.successful(())
    }

    override def deploy(deploymentData: DeploymentData): Future[Unit] = {
      deploymentData.processVersion.processName shouldBe id
      deploymentData.deploymentTime shouldBe expectedTime
      Future.successful(())
    }

    override def findStatus(name: ProcessName): Future[Option[ProcessState]] = {
      name shouldBe id
      Future.successful(status)
    }

    override def processStateConfigurator: ProcessStateConfigurator = ProcessStateCustomConfigurator
  }
}
