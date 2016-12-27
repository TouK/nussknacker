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
import pl.touk.esp.ui.process.repository.ProcessRepository
import pl.touk.esp.ui.process.repository.ProcessRepository.ProcessDetails
import pl.touk.esp.ui.sample.SampleProcess
import pl.touk.esp.ui.security.Permission
import pl.touk.esp.ui.util.MultipartUtils

import scala.concurrent.duration._

class ManagementResourcesSpec extends FlatSpec with ScalatestRouteTest
  with Matchers with ScalaFutures with OptionValues with BeforeAndAfterEach with BeforeAndAfterAll with EspItTest {

  implicit override val patienceConfig = PatienceConfig(timeout = scaled(Span(2, Seconds)), interval = scaled(Span(100, Millis)))

  implicit def default(implicit system: ActorSystem) = RouteTestTimeout(5 seconds)

  it should "process deployment should be visible in process history" in {
    saveProcessAndAssertSuccess(SampleProcess.process.id, SampleProcess.process)
    deployProcess() ~> check {
      status shouldBe StatusCodes.OK
      getSampleProcess ~> check {
        val oldDeployments = getHistoryDeployments
        decodeDetails.currentlyDeployedAt shouldBe Set("test")
        oldDeployments.size shouldBe 1
        updateProcessAndAssertSuccess(SampleProcess.process.id, SampleProcess.process)
        deployProcess() ~> check {
          getSampleProcess ~> check {
            decodeDetails.currentlyDeployedAt shouldBe Set("test")

            val currentDeployments = getHistoryDeployments
            currentDeployments.size shouldBe 1
            currentDeployments.head.environment shouldBe env
            currentDeployments.head.deployedAt should not be oldDeployments.head.deployedAt
            val buildInfo = currentDeployments.head.buildInfo
            buildInfo("engine-version") should not be empty
          }
        }
      }
    }
  }

  it should "recognize process cancel in deployment list" in {
    saveProcessAndAssertSuccess(SampleProcess.process.id, SampleProcess.process)
    deployProcess() ~> check {
      status shouldBe StatusCodes.OK
      getSampleProcess ~> check {
        decodeDetails.currentlyDeployedAt shouldBe Set("test")
        cancelProcess() ~> check {
          getSampleProcess ~> check {
            decodeDetails.currentlyDeployedAt shouldBe Set()
            val currentDeployments = getHistoryDeployments
            currentDeployments shouldBe empty
          }
        }
      }
    }
  }


  it should "recognize process deploy and cancel in global process list" in {
    saveProcessAndAssertSuccess(SampleProcess.process.id, SampleProcess.process)
    deployProcess() ~> check {
      status shouldBe StatusCodes.OK
      getProcesses ~> check {
        decodeDetailsFromAll.currentlyDeployedAt shouldBe Set("test")
        cancelProcess() ~> check {
          getProcesses ~> check {
            decodeDetailsFromAll.currentlyDeployedAt shouldBe Set()
          }
        }
      }
    }
  }


  def deployProcess(id: String = SampleProcess.process.id): RouteTestResult = {
    Post(s"/processManagement/deploy/$id") ~> withPermissions(deployRoute, Permission.Deploy)
  }

  def cancelProcess(id: String = SampleProcess.process.id): RouteTestResult = {
    Post(s"/processManagement/cancel/$id") ~> withPermissions(deployRoute, Permission.Deploy)
  }

  def getSampleProcess: RouteTestResult = {
    Get(s"/processes/${SampleProcess.process.id}") ~> withPermissions(processesRoute, Permission.Read)
  }

  def getProcesses: RouteTestResult = {
    Get(s"/processes") ~> withPermissions(processesRoute, Permission.Read)
  }

  it should "not authorize user with write permission to deploy" in {
    Post(s"/processManagement/deploy/${SampleProcess.process.id}") ~> withPermissions(deployRoute, Permission.Write) ~> check {
      rejection shouldBe server.AuthorizationFailedRejection
    }
  }

  it should "not allow concurrent deployment of same process" in {
    saveProcessAndAssertSuccess(SampleProcess.process.id, SampleProcess.process)

    InMemoryMocks.withLongerSleepBeforeAnswer {
      val firstRun = deployProcess() ~> runRoute
      deployProcess() ~> check {
        status shouldBe StatusCodes.Conflict
      }
      firstRun ~> check {
        status shouldBe StatusCodes.OK
      }
      deployProcess() ~> check {
        status shouldBe StatusCodes.OK
      }
    }

  }


  it should "not allow concurrent deployment and cancel of same process" in {
    saveProcessAndAssertSuccess(SampleProcess.process.id, SampleProcess.process)

    InMemoryMocks.withLongerSleepBeforeAnswer {
      val firstRun = deployProcess() ~> runRoute
      cancelProcess() ~> check {
        status shouldBe StatusCodes.Conflict
      }
      firstRun ~> check {
        status shouldBe StatusCodes.OK
      }
      cancelProcess() ~> check {
        status shouldBe StatusCodes.OK
      }
    }

  }

  it should "allow concurrent deployment of different processes" in {
    saveProcessAndAssertSuccess(SampleProcess.process.id, SampleProcess.process)

    val secondId = SampleProcess.process.id + "-2"
    saveProcessAndAssertSuccess(secondId, SampleProcess.process)

    InMemoryMocks.withLongerSleepBeforeAnswer {
      val firstRun = deployProcess() ~> runRoute
      deployProcess(secondId) ~> check {
        status shouldBe StatusCodes.OK
      }
      runRoute ~> check {
        status shouldBe StatusCodes.OK
      }
    }
  }



  it should "return test results" in {
    saveProcessAndAssertSuccess(SampleProcess.process.id, SampleProcess.process)

    val multiPart = MultipartUtils.prepareMultiPart("ala\nbela", "testData")
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

  private def getHistoryDeployments = decodeDetails.history.flatMap(_.deployments)

  def decodeDetails: ProcessRepository.ProcessDetails = {
    responseAs[String].decodeOption[ProcessDetails].get
  }


  def decodeDetailsFromAll: ProcessRepository.ProcessDetails = {
    responseAs[String].decodeOption[List[ProcessDetails]].flatMap(_.find(_.id == SampleProcess.process.id)).get
  }
}
