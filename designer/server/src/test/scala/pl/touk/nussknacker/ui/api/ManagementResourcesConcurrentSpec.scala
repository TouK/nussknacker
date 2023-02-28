package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.tags.Slow
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, OptionValues}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.restmodel.processdetails.ProcessDetails
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.api.helpers.{EspItTest, SampleProcess}
import pl.touk.nussknacker.ui.listener.ProcessChangeEvent.OnDeployActionSuccess

import scala.jdk.CollectionConverters._

@Slow
class ManagementResourcesConcurrentSpec extends AnyFunSuite with ScalatestRouteTest with FailFastCirceSupport
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

  test("allow concurrent deployment and cancel of same process") {
    val processName = ProcessName("concurrentDeployAndCancel")

    saveProcessAndAssertSuccess(processName.value, SampleProcess.process)
    getProcess(processName) ~> check {
      val processId = responseAs[ProcessDetails].processId

      withWaitForDeployFinish(processName.value) {
        cancelProcess(processName.value) ~> check {
          status shouldBe StatusCodes.OK
        }
      }
      // we have to wait for deploys, because otherwise insert into actions table can end up with constraint violated (process will be removed before insert)
      eventually {
        val successDeploys = processChangeListener.events.toArray.collect {
          case success@OnDeployActionSuccess(`processId`, _, _, _, _) => success
        }
        successDeploys should have length 1
      }
    }
  }

  private def withWaitForDeployFinish(name: String)(action: => Unit): Unit = {
    val firstRun = deploymentManager.withWaitForDeployFinish(ProcessName(name)) {
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
