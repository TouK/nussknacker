package pl.touk.nussknacker.engine.management

import com.typesafe.config.ConfigFactory
import io.circe.Json
import io.circe.Json.fromString
import org.apache.flink.runtime.jobgraph.JobStatus
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.StatusState.StateStatus
import pl.touk.nussknacker.engine.api.deployment.{CustomProcess, DeploymentId, ProcessState}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.customs.deployment.CustomStateStatus
import pl.touk.nussknacker.engine.management.flinkRestModel.{ExecutionConfig, JobConfig, JobOverview, JobsResponse}
import pl.touk.nussknacker.engine.testing.{EmptyProcessConfigCreator, LocalModelData}
import pl.touk.nussknacker.test.PatientScalaFutures
import sttp.client.Response
import sttp.client.testing.SttpBackendStub

import scala.concurrent.duration._

class FlinkRestManagerSpec extends FunSuite with Matchers with PatientScalaFutures {

  private val config = FlinkConfig(10 minute, None, None, None, "http://test.pl", None)

  private var statuses: List[JobOverview] = List()

  private var configs: Map[String, Map[String, Json]] = Map()

  private val manager = new FlinkRestManager(
    config = config,
    modelData = LocalModelData(ConfigFactory.empty, new EmptyProcessConfigCreator()), mainClassName = "UNUSED"
  )(SttpBackendStub.asynchronousFuture.whenRequestMatchesPartial { case req =>
    val toReturn = req.uri.path match {
      case List("jobs", "overview") =>
        JobsResponse(statuses)
      case List("jars", "upload") =>
        Json.obj("filename" -> fromString("file"))
      case List("jobs", jobId, "config") =>
        JobConfig(jobId, ExecutionConfig(configs.getOrElse(jobId, Map())))

    }
    Response.ok(Right(toReturn))
  })

  def processState(manager: FlinkProcessManager,
                   deploymentId: DeploymentId,
                   status: StateStatus,
                   version: Option[ProcessVersion] = Option.empty,
                   startTime: Option[Long] = Option.empty,
                   errorMessage: Option[String] = Option.empty): ProcessState = {

    ProcessState.custom(deploymentId, status, version, startTime = startTime, errorMessage = errorMessage)
  }

  test("refuse to deploy if process is failing") {
    statuses = List(JobOverview("2343", "p1", 10L, 10L, JobStatus.RESTARTING))

    manager.deploy(ProcessVersion(1, ProcessName("p1"), "user", None),
      CustomProcess("nothing"), None).failed.futureValue.getMessage shouldBe "Job p1 is not running, status: RESTARTING"
  }

  test("return failed status if two jobs running") {
    statuses = List(JobOverview("2343", "p1", 10L, 10L, JobStatus.RUNNING), JobOverview("1111", "p1", 30L, 30L, JobStatus.RUNNING))

    manager.findJobStatus(ProcessName("p1")).futureValue shouldBe Some(processState(
      manager, DeploymentId("1111"), CustomStateStatus.Failed, startTime = Some(30L), errorMessage = Some("Expected one job, instead: 1111 - RUNNING, 2343 - RUNNING")
    ))
  }

  test("return last terminal state if not running") {
    statuses = List(JobOverview("2343", "p1", 40L, 10L, JobStatus.FINISHED), JobOverview("1111", "p1", 35L, 30L, JobStatus.FINISHED))

    manager.findJobStatus(ProcessName("p1")).futureValue shouldBe Some(processState(
      manager, DeploymentId("2343"), CustomStateStatus.Finished, startTime = Some(10L)
    ))
  }

  test("return process version if in config") {
    val jid = "2343"
    val processName = ProcessName("p1")
    val version = 15L
    val user = "user1"

    statuses = List(JobOverview(jid, processName.value, 40L, 10L, JobStatus.FINISHED),
      JobOverview("1111", "p1", 35L, 30L, JobStatus.FINISHED))
    //Flink seems to be using strings also for Configuration.setLong
    configs = Map(jid -> Map("versionId" -> fromString(version.toString), "user" -> fromString(user)))

    manager.findJobStatus(processName).futureValue shouldBe Some(processState(
      manager, DeploymentId("2343"), CustomStateStatus.Finished, Some(ProcessVersion(version, processName, user, None)), Some(10L)
    ))
  }
}
