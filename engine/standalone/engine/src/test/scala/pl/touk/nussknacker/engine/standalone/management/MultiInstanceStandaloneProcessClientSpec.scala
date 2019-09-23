package pl.touk.nussknacker.engine.standalone.management

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.{DeploymentId, ProcessState, RunningState}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.standalone.api.DeploymentData

import scala.concurrent.Future

class MultiInstanceStandaloneProcessClientSpec extends FunSuite with Matchers with ScalaFutures {

  implicit override val patienceConfig = PatienceConfig(timeout = scaled(Span(1, Seconds)), interval = scaled(Span(20, Millis)))

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
  }
  private val failure = new Exception("Fail")

  test("Deployment should complete when all parts are successful") {

    val multiClient = new MultiInstanceStandaloneProcessClient(List(okClient(), okClient()))

    multiClient.deploy(DeploymentData("json", 1000, ProcessVersion.empty.copy(processName=id))).futureValue shouldBe (())

  }

  test("Deployment should fail when one part fails") {
    val multiClient = new MultiInstanceStandaloneProcessClient(List(okClient(), failClient))

    multiClient.deploy(DeploymentData("json", 1000, ProcessVersion.empty.copy(processName=id))).failed.futureValue shouldBe failure

  }

  test("Status should be none if no client returns status") {
    val multiClient = new MultiInstanceStandaloneProcessClient(List(
      okClient(),
      okClient()))

    multiClient.findStatus(id).futureValue shouldBe None

  }

  test("Status should be RUNNING if all clients running") {

    val consistentState = ProcessState(jobId, RunningState.Running, "RUNNING", 10000L, None)
    val multiClient = new MultiInstanceStandaloneProcessClient(List(
      okClient(Some(consistentState)),
      okClient(Some(consistentState))
    ))

    multiClient.findStatus(id).futureValue shouldBe Some(consistentState)
  }

  test("Status should be INCONSISTENT if one status unknown") {
    val multiClient = new MultiInstanceStandaloneProcessClient(List(
      okClient(),
      okClient(Some(ProcessState(jobId, RunningState.Running, "RUNNING", 0L, None))
      )))

    multiClient.findStatus(id).futureValue shouldBe Some(ProcessState(jobId, RunningState.Error, "INCONSISTENT", 0L, None,
      Some("Inconsistent states between servers: empty; state: RUNNING, startTime: 0")))
  }


  test("Status should be INCONSISTENT if status differ") {
    val multiClient = new MultiInstanceStandaloneProcessClient(List(
      okClient(Some(ProcessState(jobId, RunningState.Running, "RUNNING", 5000L, None))),
      okClient(Some(ProcessState(jobId, RunningState.Running, "RUNNING", 0L, None)))
    ))

    multiClient.findStatus(id).futureValue shouldBe Some(ProcessState(jobId, RunningState.Error, "INCONSISTENT", 0L, None, 
      Some("Inconsistent states between servers: state: RUNNING, startTime: 5000; state: RUNNING, startTime: 0")))
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
  }
}
