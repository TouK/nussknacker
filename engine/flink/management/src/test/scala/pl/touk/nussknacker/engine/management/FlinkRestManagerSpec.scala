package pl.touk.nussknacker.engine.management

import argonaut.Json
import com.ning.http.client.Request
import com.typesafe.config.ConfigFactory
import dispatch.FunctionHandler
import org.apache.flink.runtime.jobgraph.JobStatus
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.{CustomProcess, DeploymentId, ProcessState}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.management.flinkRestModel.{JobOverview, JobsResponse}
import pl.touk.nussknacker.engine.testing.{EmptyProcessConfigCreator, LocalModelData}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class FlinkRestManagerSpec extends FunSuite with Matchers with ScalaFutures {

  private val config = FlinkConfig(10 minute, None, None, None, "http://test.pl", None)

  private val manager = new FlinkRestManager(config, LocalModelData(ConfigFactory.empty, new EmptyProcessConfigCreator()), new HttpSender {
    override def send[T](pair: (Request, FunctionHandler[T]))(implicit executor: ExecutionContext): Future[T] = {
      val res = pair._1.getUrl.replace(config.restUrl, "") match {
        case "/jobs/overview" =>
          JobsResponse(statuses)
        case "/jars/upload" =>
          Json.jObjectFields("filename" -> Json.jString("file"))
        case "/jars/file/run" =>
          ()
      }
      Future.successful(res).asInstanceOf[Future[T]]
    }
  })

  private var statuses: List[JobOverview] = List()

  test("refuse to deploy if process is failing") {

    statuses = List(JobOverview("2343", "p1", 10L, 10L, JobStatus.RESTARTING))

    manager.deploy(ProcessVersion(1, ProcessName("p1"), "user", None),
      CustomProcess("nothing"), None).failed.futureValue.getMessage shouldBe "Job p1 is not running, status: RESTARTING"
  }

  test("return failed status if two jobs running") {

    statuses = List(JobOverview("2343", "p1", 10L, 10L, JobStatus.RUNNING), JobOverview("1111", "p1", 30L, 30L, JobStatus.RUNNING))

    manager.findJobStatus(ProcessName("p1")).futureValue shouldBe Some(ProcessState(DeploymentId("2343"), false,
      "INCONSISTENT", 10L, Some("Expected one job, instead: 2343, 1111")))
  }


}
