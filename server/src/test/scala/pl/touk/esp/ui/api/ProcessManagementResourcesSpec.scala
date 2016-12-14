package pl.touk.esp.ui.api

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import argonaut.Argonaut._
import argonaut.Parse
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import pl.touk.esp.ui.api.helpers.EspItTest
import pl.touk.esp.ui.api.helpers.TestFactory._
import pl.touk.esp.ui.process.repository.ProcessRepository.ProcessDetails
import pl.touk.esp.ui.sample.SampleProcess
import pl.touk.esp.ui.security.Permission
import pl.touk.esp.ui.util.MultipartUtils

import scala.concurrent.duration._

class ProcessManagementResourcesSpec extends FlatSpec with ScalatestRouteTest
  with Matchers with ScalaFutures with OptionValues with BeforeAndAfterEach with BeforeAndAfterAll with EspItTest {

  implicit def default(implicit system: ActorSystem) = RouteTestTimeout(5 seconds)

  implicit override val patienceConfig = PatienceConfig(timeout = scaled(Span(2, Seconds)), interval = scaled(Span(100, Millis)))

  it should "process deployment should be visible in process history" in {
    saveProcessAndAssertSuccess(SampleProcess.process.id, SampleProcess.process)
    deploySampleProcess ~> check {
      getSampleProcess ~> check {
        val oldDeployments = getHistoryDeployments
        oldDeployments.size shouldBe 1
        updateProcessAndAssertSuccess(SampleProcess.process.id, SampleProcess.process)
        deploySampleProcess ~> check {
          getSampleProcess ~> check {
            val currentDeployments = getHistoryDeployments
            currentDeployments.size shouldBe 1
            currentDeployments.head.environment shouldBe env
            currentDeployments.head.deployedAt should not be oldDeployments.head.deployedAt
          }
        }
      }
    }
  }

  def deploySampleProcess: RouteTestResult = {
    Post(s"/processManagement/deploy/${SampleProcess.process.id}") ~> withPermissions(deployRoute, Permission.Deploy)
  }

  def getSampleProcess: RouteTestResult = {
    Get(s"/processes/${SampleProcess.process.id}") ~> withPermissions(processesRoute, Permission.Read)
  }

  it should "not authorize user with write permission to deploy" in {
    Post(s"/processManagement/deploy/${SampleProcess.process.id}") ~> withPermissions(deployRoute, Permission.Write) ~> check {
      rejection shouldBe server.AuthorizationFailedRejection
    }
  }

  it should "return test results" in {
    saveProcessAndAssertSuccess(SampleProcess.process.id, SampleProcess.process)

    val multiPart =  MultipartUtils.prepareMultiPart("ala\nbela", "testData")
    Post(s"/processManagement/test/${SampleProcess.process.id}", multiPart) ~> withPermissions(deployRoute, Permission.Deploy) ~> check {
      status shouldEqual StatusCodes.OK
      val results = Parse.parse(responseAs[String]).right.get
      for {
        invocation <- results.cursor --\ "invocationResults"
        endsuffix <- invocation --\ "endsuffix"
        firstRes <- endsuffix.first
        params <- firstRes --\ "params"
        context <- firstRes --\ "context"
        output <- params --\ "ouput"
        input <- context --\ "input"
      } yield {
        output.focus shouldBe jString("{message=message}")
        input.focus shouldBe jString("ala")
      }
    }
  }

  private def getHistoryDeployments = responseAs[String].decodeOption[ProcessDetails].get.history.flatMap(_.deployments)
}
