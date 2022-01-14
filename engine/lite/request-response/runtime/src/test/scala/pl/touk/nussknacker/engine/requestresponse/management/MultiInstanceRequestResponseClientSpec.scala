package pl.touk.nussknacker.engine.requestresponse.management

import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.StateStatus
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.simple.{SimpleProcessState, SimpleStateStatus}
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.engine.requestresponse.api.RequestResponseDeploymentData
import pl.touk.nussknacker.test.PatientScalaFutures

import scala.concurrent.Future

class MultiInstanceRequestResponseClientSpec extends FunSuite with Matchers with PatientScalaFutures {

  import scala.concurrent.ExecutionContext.Implicits._

  val failClient: RequestResponseClient = new RequestResponseClient {

    override def cancel(name: ProcessName): Future[Unit] = {
      name shouldBe id
      Future.failed(failure)
    }

    override def deploy(deploymentData: RequestResponseDeploymentData): Future[Unit] = {
      deploymentData.processVersion.processName shouldBe id
      Future.failed(failure)
    }

    override def findStatus(name: ProcessName): Future[Option[ProcessState]] = {
      name shouldBe id
      Future.failed(failure)
    }

    def close(): Unit = {}
  }
  private val failure = new Exception("Fail")

  def processVersion(versionId: Option[Long]): Option[ProcessVersion] = versionId.map(id => ProcessVersion(VersionId(id), ProcessName(""), ProcessId(1), "", None))

  def processState(deploymentId: ExternalDeploymentId, status: StateStatus, client: RequestResponseClient, versionId: Option[Long] = Option.empty, startTime: Option[Long] = Option.empty, errors: List[String] = List.empty): ProcessState =
    SimpleProcessState(deploymentId, status, processVersion(versionId), startTime = startTime, errors = errors)

  test("Deployment should complete when all parts are successful") {
    val multiClient = new MultiInstanceRequestResponseClient(List(okClient(), okClient()))
    multiClient.deploy(RequestResponseDeploymentData(GraphProcess.empty, 1000, ProcessVersion.empty.copy(processName=id), DeploymentData.empty)).futureValue shouldBe (())
  }

  test("Deployment should fail when one part fails") {
    val multiClient = new MultiInstanceRequestResponseClient(List(okClient(), failClient))
    multiClient.deploy(RequestResponseDeploymentData(GraphProcess.empty, 1000, ProcessVersion.empty.copy(processName=id), DeploymentData.empty)).failed.futureValue shouldBe failure
  }

  test("Status should be none if no client returns status") {
    val multiClient = new MultiInstanceRequestResponseClient(List(okClient(), okClient()))
    multiClient.findStatus(id).futureValue shouldBe None
  }

  test("Status should be RUNNING if all clients running") {
    val consistentState = processState(jobId, SimpleStateStatus.Running, okClient(), Some(1), Some(10000L))
    val multiClient = new MultiInstanceRequestResponseClient(List(
      okClient(Some(consistentState)),
      okClient(Some(consistentState))
    ))

    multiClient.findStatus(id).futureValue shouldBe Some(consistentState)
  }

  test("Status should be INCONSISTENT if one status unknown") {
    val multiClient = new MultiInstanceRequestResponseClient(List(
      okClient(),
      okClient(Some(processState(jobId, SimpleStateStatus.Running, okClient(), Some(1))))
    ))

    val excepted = processState(jobId, SimpleStateStatus.Failed, multiClient, errors = List("Inconsistent states between servers: empty; state: RUNNING, startTime: None."))
    multiClient.findStatus(id).futureValue shouldBe Some(excepted)
  }

  test("Status should be INCONSISTENT if status differ") {
    val multiClient = new MultiInstanceRequestResponseClient(List(
      okClient(Some(processState(jobId, SimpleStateStatus.Running, okClient(), Some(1), Some(5000L)))),
      okClient(Some(processState(jobId, SimpleStateStatus.Running, okClient(), Some(1))))
    ))

    val excepted = processState(jobId, SimpleStateStatus.Failed, multiClient, errors = List("Inconsistent states between servers: state: RUNNING, startTime: 5000; state: RUNNING, startTime: None."))
    multiClient.findStatus(id).futureValue shouldBe Some(excepted)
  }

  test("Status should be FAIL if one status fails") {
    val multiClient = new MultiInstanceRequestResponseClient(List(okClient(), failClient))

    multiClient.findStatus(id).failed.futureValue shouldBe failure
  }

  private val id = ProcessName("id")
  private val jobId = ExternalDeploymentId("id")

  def okClient(status: Option[ProcessState] = None, expectedTime: Long = 1000): RequestResponseClient = new RequestResponseClient {

    override def cancel(name: ProcessName): Future[Unit] = {
      name shouldBe id
      Future.successful(())
    }

    override def deploy(deploymentData: RequestResponseDeploymentData): Future[Unit] = {
      deploymentData.processVersion.processName shouldBe id
      deploymentData.deploymentTime shouldBe expectedTime
      Future.successful(())
    }

    override def findStatus(name: ProcessName): Future[Option[ProcessState]] = {
      name shouldBe id
      Future.successful(status)
    }
    
    override def close(): Unit = {}
  }
}
