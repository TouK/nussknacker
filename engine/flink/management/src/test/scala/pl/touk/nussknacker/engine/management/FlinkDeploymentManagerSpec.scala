package pl.touk.nussknacker.engine.management

import akka.actor.ActorSystem
import cats.effect.unsafe.IORuntime
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import com.typesafe.config.ConfigFactory
import io.circe.Json.{fromString, fromValues}
import org.apache.flink.api.common.{JobID, JobStatus}
import org.scalatest.LoneElement
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.DeploymentManagerDependencies
import pl.touk.nussknacker.engine.api.{MetaData, ProcessVersion, StreamMetaData}
import pl.touk.nussknacker.engine.api.component.NodesDeploymentData
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.DeploymentUpdateStrategy.StateRestoringStrategy
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment._
import pl.touk.nussknacker.engine.flink.minicluster.scenariotesting.ScenarioStateVerificationConfig
import pl.touk.nussknacker.engine.management.rest.flinkRestModel._
import pl.touk.nussknacker.engine.management.utils.JobIdGenerator.generateJobId
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.test.{AvailablePortFinder, PatientScalaFutures}
import sttp.client3.{Response, StringBody, SttpBackend, SttpClientException}
import sttp.client3.asynchttpclient.future.AsyncHttpClientFutureBackend
import sttp.client3.testing.SttpBackendStub
import sttp.model.{Method, StatusCode}

import java.net.NoRouteToHostException
import java.util.Collections
import java.util.concurrent.TimeoutException
import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.duration._

//TODO move some tests to FlinkHttpClientTest
class FlinkDeploymentManagerSpec extends AnyFunSuite with Matchers with PatientScalaFutures with LoneElement {

  private implicit val freshnessPolicy: DataFreshnessPolicy = DataFreshnessPolicy.Fresh

  // We don't test scenario's json here
  private val defaultConfig =
    FlinkConfig(Some("http://test.pl"), scenarioStateVerification = ScenarioStateVerificationConfig(enabled = false))

  private var statuses: List[JobOverview] = List()

  private var configs: Map[JobID, ExecutionConfig] = Map()

  private val uploadedJarPath = "file"

  private val savepointRequestId = "123-savepoint"
  private val savepointPath      = "savepointPath"

  private val defaultDeploymentData = DeploymentData(
    DeploymentId(""),
    User("user1", "User 1"),
    Map.empty,
    NodesDeploymentData.empty,
    AdditionalModelConfigs.empty
  )

  private val sampleJobId = generateJobId

  private val returnedJobId = generateJobId

  private val canonicalProcess: CanonicalProcess = CanonicalProcess(MetaData("p1", StreamMetaData(Some(1))), Nil, Nil)

  private def createManager(
      statuses: List[JobOverview] = List(),
      acceptSavepoint: Boolean = false,
      acceptDeploy: Boolean = false,
      acceptStop: Boolean = false,
      acceptCancel: Boolean = true,
      statusCode: StatusCode = StatusCode.Ok,
      exceptionOnDeploy: Option[Exception] = None,
      freeSlots: Int = 1
  ): DeploymentManager =
    createManagerWithHistory(
      statuses,
      acceptSavepoint,
      acceptDeploy,
      acceptStop,
      acceptCancel,
      statusCode,
      exceptionOnDeploy,
      freeSlots
    )._1

  private case class HistoryEntry(operation: String, jobId: Option[JobID])

  private def createManagerWithHistory(
      statuses: List[JobOverview] = List(),
      acceptSavepoint: Boolean = false,
      acceptDeploy: Boolean = false,
      acceptStop: Boolean = false,
      acceptCancel: Boolean = true,
      statusCode: StatusCode = StatusCode.Ok,
      exceptionOnDeploy: Option[Exception] = None,
      freeSlots: Int = 1
  ): (DeploymentManager, mutable.Buffer[HistoryEntry]) = {
    import scala.jdk.CollectionConverters._
    val history: mutable.Buffer[HistoryEntry] =
      Collections.synchronizedList(new java.util.ArrayList[HistoryEntry]()).asScala
    val manager = createDeploymentManager(sttpBackend = SttpBackendStub.asynchronousFuture.whenRequestMatchesPartial {
      case req =>
        val toReturn = (req.uri.path, req.method) match {
          case (List("jobs", "overview"), Method.GET) =>
            history.append(HistoryEntry("overview", None))
            JobsResponse(statuses)
          case (List("jobs", jobIdString, "config"), Method.GET) =>
            val jobId = JobID.fromHexString(jobIdString)
            history.append(HistoryEntry("config", Some(jobId)))
            JobConfig(
              jobId,
              configs.getOrElse(
                JobID.fromHexString(jobIdString),
                ExecutionConfig(
                  `job-parallelism` = 1,
                  `user-config` = Map(
                    "processId"    -> fromString("123"),
                    "versionId"    -> fromString("1"),
                    "deploymentId" -> fromString(jobIdString),
                    "user"         -> fromString("user1"),
                    "labels"       -> fromValues(List.empty)
                  )
                )
              )
            )
          case (List("jobs", jobIdString), Method.PATCH) if acceptCancel =>
            history.append(HistoryEntry("cancel", Some(JobID.fromHexString(jobIdString))))
            ()
          case (List("jobs", jobIdString, "savepoints"), Method.POST) if acceptSavepoint || acceptStop =>
            val operation = req.body match {
              case StringBody(s, _, _) if s.contains(""""cancel-job":true""") => "stop"
              case _                                                          => "makeSavepoint"
            }
            history.append(HistoryEntry(operation, Some(JobID.fromHexString(jobIdString))))
            SavepointTriggerResponse(`request-id` = savepointRequestId)
          case (List("jobs", jobIdString, "savepoints", `savepointRequestId`), Method.GET)
              if acceptSavepoint || acceptStop =>
            history.append(HistoryEntry("getSavepoints", Some(JobID.fromHexString(jobIdString))))
            buildFinishedSavepointResponse(savepointPath)
          case (List("jars"), Method.GET) =>
            history.append(HistoryEntry("getJars", None))
            JarsResponse(files = Some(Nil))
          case (List("jars", `uploadedJarPath`, "run"), Method.POST) if acceptDeploy =>
            history.append(HistoryEntry("runJar", None))
            exceptionOnDeploy
              // see e.g. AsyncHttpClientBackend.adjustExceptions.adjustExceptions
              // TODO: can be make behaviour more robust?
              .flatMap { ex => SttpClientException.defaultExceptionToSttpClientException(req, ex) }
              .foreach(throw _)
            RunResponse(returnedJobId)
          case (List("jars", "upload"), Method.POST) if acceptDeploy =>
            history.append(HistoryEntry("uploadJar", None))
            UploadJarResponse(uploadedJarPath)
          case (List("overview"), Method.GET) =>
            ClusterOverview(1, `slots-available` = freeSlots)
          case (List("jobmanager", "config"), Method.GET) =>
            List()
          case (unsupportedPath, unsupportedMethod) =>
            throw new IllegalStateException(s"Unsupported method ${unsupportedMethod} for ${unsupportedPath}")
        }
        Response(Right(toReturn), statusCode)
    })
    (manager, history)
  }

  test("continue on timeout exception") {
    statuses = List(JobOverview(sampleJobId, "p1", 10L, 10L, JobStatus.FAILED.name(), tasksOverview(failed = 1)))

    createManager(statuses, acceptDeploy = true, exceptionOnDeploy = Some(new TimeoutException("tooo looong")))
      .processCommand(
        DMRunDeploymentCommand(
          defaultVersion,
          defaultDeploymentData,
          canonicalProcess,
          DeploymentUpdateStrategy.ReplaceDeploymentWithSameScenarioName(
            StateRestoringStrategy.RestoreStateFromReplacedJobSavepoint
          )
        )
      )
      .futureValue shouldBe None
  }

  test("not continue on random network exception") {
    statuses = List(JobOverview(sampleJobId, "p1", 10L, 10L, JobStatus.FAILED.name(), tasksOverview(failed = 1)))
    val manager =
      createManager(statuses, acceptDeploy = true, exceptionOnDeploy = Some(new NoRouteToHostException("heeelo?")))

    val result = manager.processCommand(
      DMRunDeploymentCommand(
        defaultVersion,
        defaultDeploymentData,
        canonicalProcess,
        DeploymentUpdateStrategy.ReplaceDeploymentWithSameScenarioName(
          StateRestoringStrategy.RestoreStateFromReplacedJobSavepoint
        )
      )
    )
    expectException(result, "Exception when sending request: POST http://test.pl/jars/file/run")
    result.failed.futureValue.getCause.getMessage shouldBe "heeelo?"
  }

  private val defaultVersion =
    ProcessVersion(VersionId.initialVersionId, ProcessName("p1"), ProcessId(1), List.empty, "user", None)

  test("refuse to deploy if slots exceeded") {
    statuses = Nil
    val manager = createManager(statuses, freeSlots = 0)

    val message =
      "Not enough free slots on Flink cluster. Available slots: 0, requested: 1. Extend resources of Flink cluster resources"
    expectException(
      manager.processCommand(
        DMValidateScenarioCommand(
          defaultVersion,
          defaultDeploymentData,
          canonicalProcess,
          DeploymentUpdateStrategy.ReplaceDeploymentWithSameScenarioName(
            StateRestoringStrategy.RestoreStateFromReplacedJobSavepoint
          )
        )
      ),
      message
    )
    expectException(
      manager.processCommand(
        DMRunDeploymentCommand(
          defaultVersion,
          defaultDeploymentData,
          canonicalProcess,
          DeploymentUpdateStrategy.ReplaceDeploymentWithSameScenarioName(
            StateRestoringStrategy.RestoreStateFromReplacedJobSavepoint
          )
        )
      ),
      message
    )
  }

  test("allow deploy if process is failed") {
    statuses = List(JobOverview(sampleJobId, "p1", 10L, 10L, JobStatus.FAILED.name(), tasksOverview(failed = 1)))

    createManager(statuses, acceptDeploy = true)
      .processCommand(
        DMRunDeploymentCommand(
          defaultVersion,
          defaultDeploymentData,
          canonicalProcess,
          DeploymentUpdateStrategy.ReplaceDeploymentWithSameScenarioName(
            StateRestoringStrategy.RestoreStateFromReplacedJobSavepoint
          )
        )
      )
      .futureValue shouldBe Some(ExternalDeploymentId(returnedJobId.toHexString))
  }

  test("allow deploy and make savepoint if process is running") {
    statuses = List(JobOverview(sampleJobId, "p1", 10L, 10L, JobStatus.RUNNING.name(), tasksOverview(running = 1)))

    createManager(statuses, acceptDeploy = true, acceptSavepoint = true)
      .processCommand(
        DMRunDeploymentCommand(
          defaultVersion,
          defaultDeploymentData,
          canonicalProcess,
          DeploymentUpdateStrategy.ReplaceDeploymentWithSameScenarioName(
            StateRestoringStrategy.RestoreStateFromReplacedJobSavepoint
          )
        )
      )
      .futureValue shouldBe Some(ExternalDeploymentId(returnedJobId.toHexString))
  }

  test("should make savepoint") {
    val processName = ProcessName("p1")
    val manager     = createManager(List(buildRunningJobOverview(processName)), acceptSavepoint = true)

    manager
      .processCommand(DMMakeScenarioSavepointCommand(processName, savepointDir = None))
      .futureValue shouldBe SavepointResult(path = savepointPath)
  }

  test("should stop") {
    val processName = ProcessName("p1")
    val manager     = createManager(List(buildRunningJobOverview(processName)), acceptStop = true)

    manager
      .processCommand(DMStopScenarioCommand(processName, savepointDir = None, user = User("user1", "user")))
      .futureValue shouldBe SavepointResult(path = savepointPath)
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
    statuses =
      cancellableStatuses.map(status => JobOverview(sampleJobId, s"process_$status", 10L, 10L, status, tasksOverview()))

    val (manager, history) = createManagerWithHistory(statuses)

    cancellableStatuses
      .map(status => ProcessName(s"process_$status"))
      .map(deploymentId => manager.processCommand(DMCancelScenarioCommand(deploymentId, User("test_id", "Jack"))))
      .foreach(_.futureValue shouldBe (()))

    statuses.map(_.jid).foreach(id => history should contain(HistoryEntry("cancel", Some(id))))
  }

  test("allow cancel specific deployment") {
    val processName = ProcessName("process1")
    val fooJobId    = generateJobId
    val barJobId    = generateJobId

    val jobIds = List(fooJobId, barJobId)
    statuses =
      jobIds.map(jobId => JobOverview(jobId, processName.value, 10L, 10L, JobStatus.RUNNING.name(), tasksOverview()))
    configs = jobIds
      .map(jobId =>
        jobId -> ExecutionConfig(
          1,
          Map(
            "processId"    -> fromString("123"),
            "versionId"    -> fromString("1"),
            "deploymentId" -> fromString(jobId.toHexString),
            "user"         -> fromString("user1"),
            "labels"       -> fromValues(List.empty)
          )
        )
      )
      .toMap

    val (manager, history) = createManagerWithHistory(statuses)

    manager
      .processCommand(
        DMCancelDeploymentCommand(processName, DeploymentId(fooJobId.toHexString), User("user1", "user1"))
      )
      .futureValue shouldBe (())

    history should contain(HistoryEntry("cancel", Some(fooJobId)))
    history should not contain HistoryEntry("cancel", Some(barJobId))
  }

  test("cancel duplicate processes which are in non terminal state") {
    val jobStatuses = List(
      JobStatus.RUNNING.name(),
      JobStatus.RUNNING.name(),
      JobStatus.FAILED.name()
    )
    statuses = jobStatuses.map(status => JobOverview(generateJobId, "test", 10L, 10L, status, tasksOverview()))

    val (manager, history) = createManagerWithHistory(statuses)

    manager.processCommand(DMCancelScenarioCommand(ProcessName("test"), User("test_id", "Jack"))).futureValue shouldBe (
      ()
    )

    history.filter(_.operation == "cancel").map(_.jobId.get) should contain theSameElementsAs
      statuses.filter(_.state == JobStatus.RUNNING.name()).map(_.jid)
  }

  test("allow cancel but do not sent cancel request if process is failed") {
    statuses = List(JobOverview(sampleJobId, "p1", 10L, 10L, JobStatus.FAILED.name(), tasksOverview(failed = 1)))
    val (manager, history) = createManagerWithHistory(statuses, acceptCancel = false)

    manager.processCommand(DMCancelScenarioCommand(ProcessName("p1"), User("test_id", "Jack"))).futureValue shouldBe (
      ()
    )
    history.filter(_.operation == "cancel") shouldBe Nil
  }

  test("return process version the same as configured") {
    val jid          = sampleJobId
    val processName  = ProcessName("p1")
    val version      = 15L
    val deploymentId = "789"

    statuses =
      List(JobOverview(jid, processName.value, 40L, 10L, JobStatus.FINISHED.name(), tasksOverview(finished = 1)))
    // Flink seems to be using strings also for Configuration.setLong
    configs = Map(
      jid -> ExecutionConfig(
        1,
        Map(
          "versionId"    -> fromString(version.toString),
          "deploymentId" -> fromString(deploymentId),
        )
      )
    )

    val manager = createManager(statuses)
    manager.getScenarioDeploymentsStatuses(processName).map(_.value).futureValue shouldBe List(
      DeploymentStatusDetails(
        SimpleStateStatus.Finished,
        Some(DeploymentId(deploymentId)),
        Some(VersionId(version)),
      )
    )
  }

  test("return process state respecting a short timeout for this operation") {
    val wireMockServer = AvailablePortFinder.withAvailablePortsBlocked(1)(l => new WireMockServer(l.head))
    wireMockServer.start()
    val clientRequestTimeout = 1.seconds
    try {
      def stubWithFixedDelay(delay: FiniteDuration): Unit = {
        wireMockServer.stubFor(
          get(urlPathEqualTo("/jobs/overview")).willReturn(
            aResponse()
              .withBody("""{
                  |  "jobs": []
                  |}""".stripMargin)
              .withFixedDelay(delay.toMillis.toInt)
          )
        )
      }
      val manager = createDeploymentManager(
        config = defaultConfig
          .copy(restUrl = Some(wireMockServer.baseUrl()), scenarioStateRequestTimeout = clientRequestTimeout),
      )

      val durationLongerThanClientTimeout = clientRequestTimeout.plus(patienceConfig.timeout)
      stubWithFixedDelay(durationLongerThanClientTimeout)
      a[SttpClientException.TimeoutException] shouldBe thrownBy {
        manager
          .getScenarioDeploymentsStatuses(ProcessName("p1"))
          .futureValueEnsuringInnerException(durationLongerThanClientTimeout)
      }

      stubWithFixedDelay(0.seconds)
      val resultWithoutDelay = manager
        .getScenarioDeploymentsStatuses(ProcessName("p1"))
        .map(_.value)
        .futureValue(Timeout(durationLongerThanClientTimeout.plus(1 second)))
      resultWithoutDelay shouldEqual List.empty
    } finally {
      wireMockServer.stop()
    }
  }

  private def createDeploymentManager(
      config: FlinkConfig = defaultConfig,
      sttpBackend: SttpBackend[Future, Any] = AsyncHttpClientFutureBackend()
  ): DeploymentManager = {
    val deploymentManagerDependencies = DeploymentManagerDependencies(
      new ProcessingTypeDeployedScenariosProviderStub(List.empty),
      new ProcessingTypeActionServiceStub,
      NoOpScenarioActivityManager,
      ExecutionContext.global,
      IORuntime.global,
      ActorSystem(getClass.getSimpleName),
      sttpBackend
    )
    FlinkDeploymentManagerProvider
      .createDeploymentManager(
        LocalModelData(ConfigFactory.empty, List.empty),
        deploymentManagerDependencies,
        config,
        scenarioStateCacheTTL = None
      )
      .valueOr(message => throw new IllegalStateException(message))
  }

  private def buildRunningJobOverview(processName: ProcessName): JobOverview = {
    JobOverview(
      jid = sampleJobId,
      name = processName.value,
      `last-modification` = System.currentTimeMillis(),
      `start-time` = System.currentTimeMillis(),
      state = JobStatus.RUNNING.name(),
      tasksOverview(running = 1)
    )
  }

  private def tasksOverview(
      total: Int = 1,
      created: Int = 0,
      scheduled: Int = 0,
      deploying: Int = 0,
      running: Int = 0,
      finished: Int = 0,
      canceling: Int = 0,
      canceled: Int = 0,
      failed: Int = 0,
      reconciling: Int = 0,
      initializing: Int = 0
  ): JobTasksOverview =
    JobTasksOverview(
      total,
      created = created,
      scheduled = scheduled,
      deploying = deploying,
      running = running,
      finished = finished,
      canceling = canceling,
      canceled = canceled,
      failed = failed,
      reconciling = reconciling,
      initializing = Some(initializing)
    )

  private def buildFinishedSavepointResponse(savepointPath: String): GetSavepointStatusResponse = {
    GetSavepointStatusResponse(
      status = SavepointStatus("COMPLETED"),
      operation = Some(SavepointOperation(location = Some(savepointPath), `failure-cause` = None))
    )
  }

  private def expectException(future: Future[_], message: String) =
    future.failed.futureValue.getMessage shouldBe message

}
