package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest._
import org.scalatest.tags.Slow
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.api.helpers.{EspItTest, SampleProcess}

import scala.jdk.CollectionConverters.iterableAsScalaIterableConverter

@Slow
class ManagementResourcesConcurrentSpec extends FunSuite with ScalatestRouteTest
  with Matchers with PatientScalaFutures with OptionValues with BeforeAndAfterEach with BeforeAndAfterAll with EspItTest {

  private val processId = SampleProcess.process.id

  test("not allow concurrent deployment of same process") {

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
    saveProcessAndAssertSuccess(SampleProcess.process.id, SampleProcess.process)

    withWaitForDeployFinish(processId) {
      eventually {
        cancelProcess(processId) ~> check {
          status shouldBe StatusCodes.Conflict
        }
      }
    }
    cancelProcess(SampleProcess.process.id) ~> check {
      status shouldBe StatusCodes.OK
    }
  }

  test("not allow concurrent deployment of different processes") {
    saveProcessAndAssertSuccess(processId, SampleProcess.process)

    val secondId = SampleProcess.process.id + "-2"
    saveProcessAndAssertSuccess(secondId, SampleProcess.process)

    withWaitForDeployFinish(processId) {
      eventually {
        deployProcess(secondId) ~> check {
          status shouldBe StatusCodes.Conflict
        }
      }
    }

    deployProcess(secondId) ~> runRoute ~> check {
      status shouldBe StatusCodes.OK
    }

  }

  private def withWaitForDeployFinish(name: String)(action: => Unit): Unit = {
    val firstRun = deploymentManager.withWaitForDeployFinish {
      val firstRun = deployProcess(name) ~> runRoute
      //We want to be sure deployment was invoked, to avoid flakiness
      eventually {
        deploymentManager.deploys.asScala.filter(_.processName == ProcessName(name)) should not be 'empty
      }
      action
      firstRun
    }
    firstRun ~> check {
      status shouldBe StatusCodes.OK
    }
  }



}
