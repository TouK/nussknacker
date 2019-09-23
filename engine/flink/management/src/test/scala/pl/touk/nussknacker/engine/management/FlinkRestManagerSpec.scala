package pl.touk.nussknacker.engine.management

import argonaut.Json
import argonaut.Argonaut._
import com.typesafe.config.ConfigFactory
import dispatch.FunctionHandler
import org.apache.flink.runtime.jobgraph.JobStatus
import org.asynchttpclient.Request
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.{CustomProcess, DeploymentId, ProcessState, RunningState}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.management.flinkRestModel.{ExecutionConfig, JobConfig, JobOverview, JobsResponse}
import pl.touk.nussknacker.engine.testing.{EmptyProcessConfigCreator, LocalModelData}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class FlinkRestManagerSpec extends FunSuite with Matchers with ScalaFutures {

  private val config = FlinkConfig(10 minute, None, None, None, "http://test.pl", None)

  private val manager = new FlinkRestManager(config, LocalModelData(ConfigFactory.empty, new EmptyProcessConfigCreator()), new HttpSender {
    override def send[T](pair: (Request, FunctionHandler[T]))(implicit executor: ExecutionContext): Future[T] = {
      val jobConfig = "/jobs/(.*)/config".r
      val res = pair._1.getUrl.replace(config.restUrl, "") match {
        case "/jobs/overview" =>
          JobsResponse(statuses)
        case "/jars/upload" =>
          Json.jObjectFields("filename" -> Json.jString("file"))
        case "/jars/file/run" =>
          ()
        case jobConfig(jobId) => JobConfig(jobId, ExecutionConfig(configs.getOrElse(jobId, Map())))

      }
      Future.successful(res).asInstanceOf[Future[T]]
    }
  })

  private var statuses: List[JobOverview] = List()

  private var configs: Map[String, Map[String, Json]] = Map()

  test("refuse to deploy if process is failing") {

    statuses = List(JobOverview("2343", "p1", 10L, 10L, JobStatus.RESTARTING))

    manager.deploy(ProcessVersion(1, ProcessName("p1"), "user", None),
      CustomProcess("nothing"), None).failed.futureValue.getMessage shouldBe "Job p1 is not running, status: RESTARTING"
  }

  test("return failed status if two jobs running") {

    statuses = List(JobOverview("2343", "p1", 10L, 10L, JobStatus.RUNNING), JobOverview("1111", "p1", 30L, 30L, JobStatus.RUNNING))

    manager.findJobStatus(ProcessName("p1")).futureValue shouldBe Some(ProcessState(DeploymentId("1111"), RunningState.Error,
      "INCONSISTENT", 30L, None, Some("Expected one job, instead: 1111 - RUNNING, 2343 - RUNNING")))
  }

  test("return last terminal state if not running") {
    statuses = List(JobOverview("2343", "p1", 40L, 10L, JobStatus.FINISHED),
      JobOverview("1111", "p1", 35L, 30L, JobStatus.FINISHED))

    manager.findJobStatus(ProcessName("p1")).futureValue shouldBe Some(ProcessState(DeploymentId("2343"), RunningState.Finished,
      "FINISHED", 10L, None))
  }

  test("return process version if in config") {
    val jid = "2343"
    val processName = ProcessName("p1")
    val version = 15L
    val user = "user1"

    statuses = List(JobOverview(jid, processName.value, 40L, 10L, JobStatus.FINISHED),
      JobOverview("1111", "p1", 35L, 30L, JobStatus.FINISHED))
    //Flink seems to be using strings also for Configuration.setLong
    configs = Map(jid -> Map("versionId" -> jString(version.toString), "user" -> jString(user)))

    manager.findJobStatus(processName).futureValue shouldBe Some(ProcessState(DeploymentId("2343"), RunningState.Finished,
      "FINISHED", 10L, Some(ProcessVersion(version, processName, user, None))))
  }


}
