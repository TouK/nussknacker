package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, OptionValues}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.tags.Slow
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.api.helpers.{EspItTest, SampleProcess}

import scala.jdk.CollectionConverters._

@Slow
class ManagementResourcesConcurrentSpec extends AnyFunSuite with ScalatestRouteTest
  with Matchers with PatientScalaFutures with OptionValues with BeforeAndAfterEach with BeforeAndAfterAll with EspItTest {

  test("not allow concurrent deployment of same process") {

    val processId = "sameConcurrentDeployments"

    saveProcessAndAssertSuccess(processId, SampleProcess.process)

    withWaitForDeployFinish(processId) {
      eventually {
        deployProcess(processId) ~> runRoute ~> check {
          status shouldBe StatusCodes.Conflict
        }
      }
    }
    deployProcess(processId) ~> runRoute ~> check {
      status shouldBe StatusCodes.OK
    }
  }

  test("not allow concurrent deployment and cancel of same process") {
    val processId = "concurrentDeployAndCancel"

    saveProcessAndAssertSuccess(processId, SampleProcess.process)

    withWaitForDeployFinish(processId) {
      eventually {
        cancelProcess(processId) ~> check {
          status shouldBe StatusCodes.Conflict
        }
      }
    }
    cancelProcess(processId) ~> check {
      status shouldBe StatusCodes.OK
    }
  }

  test("not allow concurrent deployment of different processes") {
    val processId = "differentScenarios1"
    val processId2 = "differentScenarios2"

    saveProcessAndAssertSuccess(processId, SampleProcess.process)
    saveProcessAndAssertSuccess(processId2, SampleProcess.process)

    withWaitForDeployFinish(processId) {
      eventually {
        deployProcess(processId2) ~> check {
          status shouldBe StatusCodes.Conflict
        }
      }
    }

    deployProcess(processId2) ~> runRoute ~> check {
      status shouldBe StatusCodes.OK
    }

  }

  private def withWaitForDeployFinish(name: String)(action: => Unit): Unit = {
    val firstRun = deploymentManager.withWaitForDeployFinish {
      val firstRun = deployProcess(name) ~> runRoute
      firstRun.handled shouldBe false
      //We want to be sure deployment was invoked, to avoid flakiness
      eventually {
        deploymentManager.deploys.asScala.filter(_.processName == ProcessName(name)) should not be Symbol("empty")
      }
      action
      firstRun
    }
    firstRun ~> check {
      status shouldBe StatusCodes.OK
    }
  }



}
