package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model.{StatusCode, StatusCodes}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.tags.Slow
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, OptionValues}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.api.helpers.{NuResourcesTest, SampleProcess}

import scala.jdk.CollectionConverters._

@Slow
class ManagementResourcesConcurrentSpec
    extends AnyFunSuite
    with ScalatestRouteTest
    with FailFastCirceSupport
    with Matchers
    with PatientScalaFutures
    with OptionValues
    with BeforeAndAfterEach
    with BeforeAndAfterAll
    with NuResourcesTest {

  test("not allow concurrent deployment of same process") {
    val processName = s"sameConcurrentDeployments"
    saveProcessAndAssertSuccess(processName, SampleProcess.process)

    deploymentManager.withWaitForDeployFinish(ProcessName(processName)) {
      val firstDeployResult  = deployProcess(processName)
      val secondDeployResult = deployProcess(processName)
      eventually {
        firstDeployResult.handled shouldBe true
        secondDeployResult.handled shouldBe true
      }
      var firstStatus: StatusCode  = null
      var secondStatus: StatusCode = null
      firstDeployResult ~> check {
        firstStatus = status
      }
      secondDeployResult ~> check {
        secondStatus = status
      }
      val statuses = List(firstStatus, secondStatus)
      statuses should contain only (StatusCodes.OK, StatusCodes.Conflict)
      eventually {
        deploymentManager.deploys.asScala.count(_ == ProcessName(processName)) shouldBe 1
      }
    }
  }

  test("allow concurrent deployment and cancel of same process") {
    val processName = "concurrentDeployAndCancel"

    saveProcessAndAssertSuccess(processName, SampleProcess.process)
    deploymentManager.withWaitForDeployFinish(ProcessName(processName)) {
      val firstDeployResult = deployProcess(processName)
      // we have to check if deploy was invoke, otherwise cancel can be faster than deploy
      eventually {
        deploymentManager.deploys.asScala.count(_ == ProcessName(processName)) shouldBe 1
      }
      cancelProcess(processName) ~> check {
        status shouldBe StatusCodes.OK
      }
      firstDeployResult ~> check {
        status shouldBe StatusCodes.OK
      }
    }
  }

}
