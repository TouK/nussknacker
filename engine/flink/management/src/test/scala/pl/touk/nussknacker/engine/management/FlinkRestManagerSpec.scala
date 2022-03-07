package pl.touk.nussknacker.engine.management

import com.typesafe.config.ConfigFactory
import io.circe.Json.fromString
import org.apache.flink.api.common.JobStatus
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.process.{EmptyProcessConfigCreator, ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.{DeploymentData, DeploymentId, ExternalDeploymentId, User}
import pl.touk.nussknacker.engine.management.rest.flinkRestModel._
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.test.PatientScalaFutures
import sttp.client.testing.SttpBackendStub
import sttp.client.{NothingT, Response, SttpBackend, SttpClientException}
import sttp.model.{Method, StatusCode}

import java.net.NoRouteToHostException
import java.util.concurrent.TimeoutException
import java.util.{Collections, UUID}
import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

//TODO move some tests to FlinkHttpClientTest
class FlinkRestManagerSpec extends FunSuite with Matchers with PatientScalaFutures {

  import scala.concurrent.ExecutionContext.Implicits._

  //We don't test scenario's json here
  private val config = FlinkConfig("http://test.pl", None, shouldVerifyBeforeDeploy = false, shouldCheckAvailableSlots = false)

  private var statuses: List[JobOverview] = List()

  private var configs: Map[String, ExecutionConfig] = Map()

  private val uploadedJarPath = "file"

  private val savepointRequestId = "123-savepoint"
  private val savepointPath = "savepointPath"

  private val defaultDeploymentData = DeploymentData(DeploymentId(""), User("user1", "User 1"), Map.empty)

  private val returnedJobId = "jobId"

  private val canonicalProcess: CanonicalProcess = ProcessMarshaller.fromJsonUnsafe(
    """
      |{
      |  "metaData" : {
      |    "id" : "p1",
      |    "typeSpecificData" : {
      |      "type" : "StreamMetaData"
      |    }
      |  },
      |  "nodes" : []
      |}
      |""".stripMargin)

  private def createManager(statuses: List[JobOverview] = List(),
                            acceptSavepoint: Boolean = false,
                            acceptDeploy: Boolean = false,
                            acceptStop: Boolean = false,
                            acceptCancel: Boolean = true,
                            statusCode: StatusCode = StatusCode.Ok, exceptionOnDeploy: Option[Exception] = None): FlinkRestManager =
    createManagerWithHistory(statuses, acceptSavepoint, acceptDeploy, acceptStop, acceptCancel, statusCode, exceptionOnDeploy)._1

  private case class HistoryEntry(operation: String, jobId: Option[String])


  private def createManagerWithHistory(statuses: List[JobOverview] = List(),
                                       acceptSavepoint: Boolean = false,
                                       acceptDeploy: Boolean = false,
                                       acceptStop: Boolean = false,
                                       acceptCancel: Boolean = true,
                                       statusCode: StatusCode = StatusCode.Ok,
                                       exceptionOnDeploy: Option[Exception] = None
                                      ): (FlinkRestManager, mutable.Buffer[HistoryEntry])
  = {
    import scala.collection.JavaConverters._
    val history: mutable.Buffer[HistoryEntry] = Collections.synchronizedList(new java.util.ArrayList[HistoryEntry]()).asScala
    val manager = createManagerWithBackend(SttpBackendStub.asynchronousFuture.whenRequestMatchesPartial { case req =>
      val toReturn = (req.uri.path, req.method) match {
        case (List("jobs", "overview"), Method.GET) =>
          history.append(HistoryEntry("overview", None))
          JobsResponse(statuses)
        case (List("jobs", jobId, "config"), Method.GET) =>
          history.append(HistoryEntry("config", Some(jobId)))
          JobConfig(jobId, configs.getOrElse(jobId, ExecutionConfig(`job-parallelism` = 1, `user-config` = Map.empty)))
        case (List("jobs", jobId), Method.PATCH) if acceptCancel =>
          history.append(HistoryEntry("cancel", Some(jobId)))
          ()
        case (List("jobs", jobId, "savepoints"), Method.POST) if acceptSavepoint =>
          history.append(HistoryEntry("makeSavepoint", Some(jobId)))
          SavepointTriggerResponse(`request-id` = savepointRequestId)
        case (List("jobs", jobId, "savepoints", `savepointRequestId`), Method.GET) if acceptSavepoint || acceptStop =>
          history.append(HistoryEntry("getSavepoints", Some(jobId)))
          buildFinishedSavepointResponse(savepointPath)
        case (List("jobs", jobId, "stop"), Method.POST) if acceptStop =>
          history.append(HistoryEntry("stop", Some(jobId)))
          SavepointTriggerResponse(`request-id` = savepointRequestId)
        case (List("jars"), Method.GET) =>
          history.append(HistoryEntry("getJars", None))
          JarsResponse(files = Some(Nil))
        case (List("jars", `uploadedJarPath`, "run"), Method.POST) if acceptDeploy =>
          history.append(HistoryEntry("runJar", None))
          exceptionOnDeploy
            //see e.g. AsyncHttpClientBackend.adjustExceptions.adjustExceptions
            //TODO: can be make behaviour more robust?
            .flatMap(SttpClientException.defaultExceptionToSttpClientException)
            .foreach(throw _)
          RunResponse(returnedJobId)
        case (List("jars", "upload"), Method.POST) if acceptDeploy =>
          history.append(HistoryEntry("uploadJar", None))
          UploadJarResponse(uploadedJarPath)
      }
      Response(Right(toReturn), statusCode)
    })
    (manager, history)
  }

  def processState(manager: FlinkDeploymentManager,
                   deploymentId: ExternalDeploymentId,
                   status: StateStatus,
                   version: Option[ProcessVersion] = Option.empty,
                   startTime: Option[Long] = Option.empty,
                   errors: List[String] = List.empty): ProcessState =
    manager.processStateDefinitionManager.processState(
      status,
      Some(deploymentId),
      version,
      startTime = startTime,
      attributes = Option.empty,
      errors = errors
    )

  test("continue on timeout exception") {
    statuses = List(JobOverview("2343", "p1", 10L, 10L, JobStatus.FAILED.name(), tasksOverview(failed = 1)))

    createManager(statuses, acceptDeploy = true, exceptionOnDeploy = Some(new TimeoutException("tooo looong")))
      .deploy(
        defaultVersion,
        defaultDeploymentData,
        canonicalProcess,
        None
      ).futureValue shouldBe None
  }

  test("not continue on random exception exception") {
    statuses = List(JobOverview("2343", "p1", 10L, 10L, JobStatus.FAILED.name(), tasksOverview(failed = 1)))
    val manager = createManager(statuses, acceptDeploy = true, exceptionOnDeploy = Some(new NoRouteToHostException("heeelo?")))

    Await.ready(manager.deploy(
        defaultVersion,
        defaultDeploymentData,
        canonicalProcess,
        None
      ), 1 second).eitherValue.flatMap(_.left.toOption) shouldBe 'defined
  }

  private val defaultVersion = ProcessVersion(VersionId.initialVersionId, ProcessName("p1"), ProcessId(1), "user", None)

  test("refuse to deploy if process is failing") {
    statuses = List(JobOverview("2343", "p1", 10L, 10L, JobStatus.RESTARTING.name(), tasksOverview()))

    createManager(statuses)
      .deploy(defaultVersion, defaultDeploymentData, canonicalProcess, None)
      .failed.futureValue.getMessage shouldBe "Job p1 cannot be deployed, status: Restarting"
  }

  test("allow deploy if process is failed") {
    statuses = List(JobOverview("2343", "p1", 10L, 10L, JobStatus.FAILED.name(), tasksOverview(failed = 1)))

    createManager(statuses, acceptDeploy = true)
      .deploy(defaultVersion, defaultDeploymentData, canonicalProcess, None)
      .futureValue shouldBe Some(ExternalDeploymentId(returnedJobId))
  }

  test("allow deploy and make savepoint if process is running") {
    statuses = List(JobOverview("2343", "p1", 10L, 10L, JobStatus.RUNNING.name(), tasksOverview(running = 1)))

    createManager(statuses, acceptDeploy = true, acceptSavepoint = true)
      .deploy(defaultVersion, defaultDeploymentData, canonicalProcess, None)
      .futureValue shouldBe Some(ExternalDeploymentId(returnedJobId))
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

  test("allow cancel if process is in non terminal status") {
    // JobStatus.CANCELLING & FAILING is skipped here, cause we do not allow Cancel action in ProcessStateDefinitionManager for this status
    val cancellableStatuses = List(
        JobStatus.CREATED.name(),
        JobStatus.RUNNING.name(),
        JobStatus.RESTARTING.name(),
        JobStatus.SUSPENDED.name(),
        JobStatus.RECONCILING.name()
    )
    statuses = cancellableStatuses.map(status => JobOverview(UUID.randomUUID().toString, s"process_$status", 10L, 10L, status, tasksOverview()))

    val (manager, history)  = createManagerWithHistory(statuses)

    cancellableStatuses
      .map(status => ProcessName(s"process_$status"))
      .map(manager.cancel(_, User("test_id", "Jack")))
      .foreach(_.futureValue shouldBe (()))

    statuses.map(_.jid).foreach(id => history should contain(HistoryEntry("cancel", Some(id))))
  }

  test("cancel duplicate processes which are in non terminal state") {
    val jobStatuses = List(
      JobStatus.RUNNING.name(),
      JobStatus.RUNNING.name(),
      JobStatus.FAILED.name()
    )
    statuses = jobStatuses.map(status => JobOverview(UUID.randomUUID().toString, "test", 10L, 10L, status, tasksOverview()))

    val (manager, history)  = createManagerWithHistory(statuses)

    manager.cancel(ProcessName("test"), User("test_id", "Jack")).futureValue shouldBe (())

    history.filter(_.operation == "cancel").map(_.jobId.get) should contain theSameElementsAs
      statuses.filter(_.state == JobStatus.RUNNING.name()).map(_.jid)
  }


  test("allow cancel but do not sent cancel request if process is failed") {
    statuses = List(JobOverview("2343", "p1", 10L, 10L, JobStatus.FAILED.name(), tasksOverview(failed = 1)))
    val (manager, history) = createManagerWithHistory(statuses, acceptCancel = false)

    manager.cancel(ProcessName("p1"), User("test_id", "Jack")).futureValue shouldBe (())
    history.filter(_.operation == "cancel") shouldBe Nil
  }

  test("return failed status if two jobs running") {
    statuses = List(JobOverview("2343", "p1", 10L, 10L, JobStatus.RUNNING.name(), tasksOverview(running = 1)), JobOverview("1111", "p1", 30L, 30L, JobStatus.RUNNING.name(), tasksOverview(running = 1)))

    val manager = createManager(statuses)
    manager.findJobStatus(ProcessName("p1")).futureValue shouldBe Some(processState(
      manager, ExternalDeploymentId("1111"), FlinkStateStatus.MultipleJobsRunning, startTime = Some(30L), errors = List("Expected one job, instead: 1111 - RUNNING, 2343 - RUNNING")
    ))
  }

  test("return failed status if two in non-terminal state") {
    statuses = List(JobOverview("2343", "p1", 10L, 10L, JobStatus.RUNNING.name(), tasksOverview(running = 1)), JobOverview("1111", "p1", 30L, 30L, JobStatus.RESTARTING.name(), tasksOverview()))

    val manager = createManager(statuses)
    manager.findJobStatus(ProcessName("p1")).futureValue shouldBe Some(processState(
      manager, ExternalDeploymentId("1111"), FlinkStateStatus.MultipleJobsRunning, startTime = Some(30L), errors = List("Expected one job, instead: 1111 - RESTARTING, 2343 - RUNNING")
    ))
  }

  test("return running status if cancelled job has last-modification date later then running job") {
    statuses = List(JobOverview("2343", "p1", 20L, 10L, JobStatus.RUNNING.name(), tasksOverview(running = 1)), JobOverview("1111", "p1", 30L, 5L, JobStatus.CANCELED.name(), tasksOverview(canceled = 1)))

    val manager = createManager(statuses)
    manager.findJobStatus(ProcessName("p1")).futureValue shouldBe Some(processState(
      manager, ExternalDeploymentId("2343"), FlinkStateStatus.Running, startTime = Some(10L)
    ))
  }

  test("return last terminal state if not running") {
    statuses = List(JobOverview("2343", "p1", 40L, 10L, JobStatus.FINISHED.name(), tasksOverview(finished = 1)), JobOverview("1111", "p1", 35L, 30L, JobStatus.FINISHED.name(), tasksOverview(finished = 1)))

    val manager = createManager(statuses)
    manager.findJobStatus(ProcessName("p1")).futureValue shouldBe Some(processState(
      manager, ExternalDeploymentId("2343"), FlinkStateStatus.Finished, startTime = Some(10L)
    ))

  }

  test("return non-terminal state if not running") {
    statuses = List(JobOverview("2343", "p1", 40L, 10L, JobStatus.FINISHED.name(), tasksOverview(finished = 1)), JobOverview("1111", "p1", 35L, 30L, JobStatus.RESTARTING.name(), tasksOverview()))

    val manager = createManager(statuses)
    manager.findJobStatus(ProcessName("p1")).futureValue shouldBe Some(processState(
      manager, ExternalDeploymentId("1111"), FlinkStateStatus.Restarting, startTime = Some(30L)
    ))
  }

  test("return process version if in config") {
    val jid = "2343"
    val processName = ProcessName("p1")
    val version = 15L
    val user = "user1"
    val processId = ProcessId(6565L)

    statuses = List(JobOverview(jid, processName.value, 40L, 10L, JobStatus.FINISHED.name(), tasksOverview(finished = 1)),
      JobOverview("1111", "p1", 35L, 30L, JobStatus.FINISHED.name(), tasksOverview(finished = 1)))
    //Flink seems to be using strings also for Configuration.setLong
    configs = Map(jid -> ExecutionConfig(1, Map("processId" -> fromString(processId.value.toString),
                                                "versionId" -> fromString(version.toString),
                                                "user" -> fromString(user))))

    val manager = createManager(statuses)
    manager.findJobStatus(processName).futureValue shouldBe Some(processState(
      manager, ExternalDeploymentId("2343"), FlinkStateStatus.Finished, Some(ProcessVersion(VersionId(version), processName, processId, user, None)), Some(10L)
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
    JobOverview(jid = "1111", name = processName.value, `last-modification` = System.currentTimeMillis(), `start-time` = System.currentTimeMillis(), state = JobStatus.RUNNING.name(), tasksOverview(running = 1))
  }
  private def tasksOverview(total: Int = 1, created: Int = 0, scheduled: Int = 0, deploying: Int = 0, running: Int = 0, finished: Int = 0,
                            canceling: Int = 0, canceled: Int = 0, failed: Int = 0, reconciling: Int = 0, initializing: Int = 0): JobTasksOverview =
    JobTasksOverview(total, created = created, scheduled = scheduled, deploying = deploying, running = running, finished = finished,
      canceling = canceling, canceled = canceled, failed = failed, reconciling = reconciling, initializing = Some(initializing))

  private def buildFinishedSavepointResponse(savepointPath: String): GetSavepointStatusResponse = {
    GetSavepointStatusResponse(status = SavepointStatus("COMPLETED"), operation = Some(SavepointOperation(location = Some(savepointPath), `failure-cause` = None)))
  }
}
