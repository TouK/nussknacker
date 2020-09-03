package pl.touk.nussknacker.engine.management

import java.net.NoRouteToHostException
import java.util.concurrent.TimeoutException

import com.typesafe.config.ConfigFactory
import io.circe.Json
import io.circe.Json.fromString
import org.apache.flink.api.common.JobStatus
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.{CustomProcess, DeploymentId, ProcessState, SavepointResult, StateStatus, User}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.management.flinkRestModel.{ExecutionConfig, GetSavepointStatusResponse, JobConfig, JobOverview, JobsResponse, SavepointOperation, SavepointStatus, SavepointTriggerResponse, UploadJarResponse}
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.util.process.EmptyProcessConfigCreator
import pl.touk.nussknacker.test.PatientScalaFutures
import sttp.client.testing.SttpBackendStub
import sttp.client.{NothingT, Response, SttpBackend, SttpClientException}
import sttp.model.{Method, StatusCode}

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

class FlinkRestManagerSpec extends FunSuite with Matchers with PatientScalaFutures {

  private val config = FlinkConfig(10 minute, None, None, None, "http://test.pl", None)

  private var statuses: List[JobOverview] = List()

  private var configs: Map[String, Map[String, Json]] = Map()

  private val uploadedJarPath = "file"

  private val savepointRequestId = "123-savepoint"
  private val savepointPath = "savepointPath"

  private def createManager(statuses: List[JobOverview] = List(),
                            acceptSavepoint: Boolean = false,
                            acceptDeploy: Boolean = false,
                            acceptStop: Boolean = false,
                            statusCode: StatusCode = StatusCode.Ok, exceptionOnDeploy: Option[Exception] = None)
    = createManagerWithBackend(SttpBackendStub.asynchronousFuture.whenRequestMatchesPartial { case req =>
    val toReturn = (req.uri.path, req.method) match {
      case (List("jobs", "overview"), Method.GET) =>
        JobsResponse(statuses)
      case (List("jobs", jobId, "config"), Method.GET) =>
        JobConfig(jobId, ExecutionConfig(configs.getOrElse(jobId, Map())))
      case (List("jobs", _), Method.PATCH) =>
        ()
      case (List("jobs", _, "savepoints"), Method.POST) if acceptSavepoint  =>
        SavepointTriggerResponse(`request-id` = savepointRequestId)
      case (List("jobs", _, "savepoints", `savepointRequestId`), Method.GET) if acceptSavepoint || acceptStop=>
        buildFinishedSavepointResponse(savepointPath)
      case (List("jobs", _, "stop"), Method.POST) if acceptStop=>
        SavepointTriggerResponse(`request-id` = savepointRequestId)

      case (List("jars", `uploadedJarPath`, "run"), Method.POST) if acceptDeploy  =>
        exceptionOnDeploy
          //see e.g. AsyncHttpClientBackend.adjustExceptions.adjustExceptions
          //TODO: can be make behaviour more robust?
          .flatMap(SttpClientException.defaultExceptionToSttpClientException)
          .foreach(throw _)
        ()
      case (List("jars", "upload"), Method.POST) if acceptDeploy =>
        UploadJarResponse(uploadedJarPath)
    }
    Response(Right(toReturn), statusCode)
  })

  def processState(manager: FlinkProcessManager,
                   deploymentId: DeploymentId,
                   status: StateStatus,
                   version: Option[ProcessVersion] = Option.empty,
                   startTime: Option[Long] = Option.empty,
                   errors: List[String] = List.empty): ProcessState =
    ProcessState(
      deploymentId,
      status,
      version,
      manager.processStateDefinitionManager,
      startTime = startTime,
      attributes = Option.empty,
      errors = errors
    )

  test("continue on timeout exception") {
    statuses = List(JobOverview("2343", "p1", 10L, 10L, JobStatus.FAILED.name()))

    createManager(statuses, acceptDeploy = true, exceptionOnDeploy = Some(new TimeoutException("tooo looong")))
      .deploy(
        ProcessVersion(1, ProcessName("p1"), "user", None),
        CustomProcess("nothing"),
        None,
        user = User("user1", "User 1")
      ).futureValue shouldBe (())
  }

  test("not continue on random exception exception") {
    statuses = List(JobOverview("2343", "p1", 10L, 10L, JobStatus.FAILED.name()))
    val manager = createManager(statuses, acceptDeploy = true, exceptionOnDeploy = Some(new NoRouteToHostException("heeelo?")))

    Await.ready(manager.deploy(
        ProcessVersion(1, ProcessName("p1"), "user", None),
        CustomProcess("nothing"),
        None,
        user = User("user1", "User 1")
      ), 1 second).eitherValue.flatMap(_.left.toOption) shouldBe 'defined
  }

  test("refuse to deploy if process is failing") {
    statuses = List(JobOverview("2343", "p1", 10L, 10L, JobStatus.RESTARTING.name()))

    createManager(statuses).deploy(ProcessVersion(1, ProcessName("p1"), "user", None),
      CustomProcess("nothing"), None, user = User("user1", "User 1")).failed.futureValue.getMessage shouldBe "Job p1 cannot be deployed, status: Restarting"
  }

  test("allow deploy if process is failed") {
    statuses = List(JobOverview("2343", "p1", 10L, 10L, JobStatus.FAILED.name()))

    createManager(statuses, acceptDeploy = true).deploy(ProcessVersion(1, ProcessName("p1"), "user", None),
      CustomProcess("nothing"), None, user = User("user1", "User 1")).futureValue shouldBe (())
  }

  test("allow deploy and make savepoint if process is running") {
    statuses = List(JobOverview("2343", "p1", 10L, 10L, JobStatus.RUNNING.name()))

    createManager(statuses, acceptDeploy = true, acceptSavepoint = true).deploy(ProcessVersion(1, ProcessName("p1"), "user", None),
      CustomProcess("nothing"), None, user = User("user1", "User 1")).futureValue shouldBe (())
  }


  test("should make savepoint") {

    val processName = ProcessName("p1")
    val manager = createManager(List(buildRunningJobOverview(processName)), acceptSavepoint = true)

    manager.savepoint(processName, savepointDir = None).futureValue shouldBe SavepointResult(path = savepointPath)
  }

  test("should stop") {
    val processName = ProcessName("p1")
    val manager = createManager(List(buildRunningJobOverview(processName)), acceptStop = true)

    manager.stop(processName, savepointDir = None, user = User("user1", "user")).futureValue shouldBe SavepointResult(path = savepointPath)
  }

  test("return failed status if two jobs running") {
    statuses = List(JobOverview("2343", "p1", 10L, 10L, JobStatus.RUNNING.name()), JobOverview("1111", "p1", 30L, 30L, JobStatus.RUNNING.name()))

    val manager = createManager(statuses)
    manager.findJobStatus(ProcessName("p1")).futureValue shouldBe Some(processState(
      manager, DeploymentId("1111"), FlinkStateStatus.MultipleJobsRunning, startTime = Some(30L), errors = List("Expected one job, instead: 1111 - RUNNING, 2343 - RUNNING")
    ))
  }

  test("return failed status if two in non-terminal state") {
    statuses = List(JobOverview("2343", "p1", 10L, 10L, JobStatus.RUNNING.name()), JobOverview("1111", "p1", 30L, 30L, JobStatus.RESTARTING.name()))

    val manager = createManager(statuses)
    manager.findJobStatus(ProcessName("p1")).futureValue shouldBe Some(processState(
      manager, DeploymentId("1111"), FlinkStateStatus.MultipleJobsRunning, startTime = Some(30L), errors = List("Expected one job, instead: 1111 - RESTARTING, 2343 - RUNNING")
    ))
  }

  test("return running status if cancelled job has last-modification date later then running job") {
    statuses = List(JobOverview("2343", "p1", 20L, 10L, JobStatus.RUNNING.name()), JobOverview("1111", "p1", 30L, 5L, JobStatus.CANCELED.name()))

    val manager = createManager(statuses)
    manager.findJobStatus(ProcessName("p1")).futureValue shouldBe Some(processState(
      manager, DeploymentId("2343"), FlinkStateStatus.Running, startTime = Some(10L)
    ))
  }

  test("return last terminal state if not running") {
    statuses = List(JobOverview("2343", "p1", 40L, 10L, JobStatus.FINISHED.name()), JobOverview("1111", "p1", 35L, 30L, JobStatus.FINISHED.name()))

    val manager = createManager(statuses)
    manager.findJobStatus(ProcessName("p1")).futureValue shouldBe Some(processState(
      manager, DeploymentId("2343"), FlinkStateStatus.Finished, startTime = Some(10L)
    ))

  }

  test("return non-terminal state if not running") {
    statuses = List(JobOverview("2343", "p1", 40L, 10L, JobStatus.FINISHED.name()), JobOverview("1111", "p1", 35L, 30L, JobStatus.RESTARTING.name()))

    val manager = createManager(statuses)
    manager.findJobStatus(ProcessName("p1")).futureValue shouldBe Some(processState(
      manager, DeploymentId("1111"), FlinkStateStatus.Restarting, startTime = Some(30L)
    ))
  }

  test("return process version if in config") {
    val jid = "2343"
    val processName = ProcessName("p1")
    val version = 15L
    val user = "user1"

    statuses = List(JobOverview(jid, processName.value, 40L, 10L, JobStatus.FINISHED.name()),
      JobOverview("1111", "p1", 35L, 30L, JobStatus.FINISHED.name()))
    //Flink seems to be using strings also for Configuration.setLong
    configs = Map(jid -> Map("versionId" -> fromString(version.toString), "user" -> fromString(user)))

    val manager = createManager(statuses)
    manager.findJobStatus(processName).futureValue shouldBe Some(processState(
      manager, DeploymentId("2343"), FlinkStateStatus.Finished, Some(ProcessVersion(version, processName, user, None)), Some(10L)
    ))
  }

  private def createManagerWithBackend(backend: SttpBackend[Future, Nothing, NothingT]): FlinkRestManager = {
    implicit val b: SttpBackend[Future, Nothing, NothingT] = backend
    new FlinkRestManager(
      config = config,
      modelData = LocalModelData(ConfigFactory.empty, new EmptyProcessConfigCreator()), mainClassName = "UNUSED"
    )
  }

  private def buildRunningJobOverview(processName: ProcessName): JobOverview = {
    JobOverview(jid = "1111", name = processName.value, `last-modification` = System.currentTimeMillis(), `start-time` = System.currentTimeMillis(), state = JobStatus.RUNNING.name())
  }

  private def buildFinishedSavepointResponse(savepointPath: String): GetSavepointStatusResponse = {
    GetSavepointStatusResponse(status = SavepointStatus("COMPLETED"), operation = Some(SavepointOperation(location = Some(savepointPath), `failure-cause` = None)))
  }
}
