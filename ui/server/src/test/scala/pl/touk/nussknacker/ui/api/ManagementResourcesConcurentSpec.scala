package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest._
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.api.helpers.{EspItTest, SampleProcess}

class ManagementResourcesConcurentSpec extends FunSuite with ScalatestRouteTest
  with Matchers with PatientScalaFutures with OptionValues with BeforeAndAfterEach with BeforeAndAfterAll with EspItTest {
  //FIXME: can't test that. I give up for now

  ignore("not allow concurrent deployment of same process") {

    saveProcessAndAssertSuccess(SampleProcess.process.id, SampleProcess.process)

    def invokeDeploy =
      deployProcess(SampleProcess.process.id) ~> runRoute

    val firstRun = invokeDeploy
    invokeDeploy ~> check {
      status shouldBe StatusCodes.Conflict
    }
    firstRun ~> check {
      status shouldBe StatusCodes.OK
    }
    invokeDeploy ~> check {
      status shouldBe StatusCodes.OK
    }
  }

  ignore("not allow concurrent deployment and cancel of same process") {
    saveProcessAndAssertSuccess(SampleProcess.process.id, SampleProcess.process)

      val firstRun = deployProcess(SampleProcess.process.id) ~> runRoute
      cancelProcess(SampleProcess.process.id) ~> check {
        status shouldBe StatusCodes.Conflict
      }
      firstRun ~> check {
        status shouldBe StatusCodes.OK
      }
      cancelProcess(SampleProcess.process.id) ~> check {
        status shouldBe StatusCodes.OK
      }

  }

  ignore("not allow concurrent deployment of different processes") {
    saveProcessAndAssertSuccess(SampleProcess.process.id, SampleProcess.process)

    val secondId = SampleProcess.process.id + "-2"
    saveProcessAndAssertSuccess(secondId, SampleProcess.process)

      val firstRun = deployProcess(SampleProcess.process.id) ~> runRoute
      deployProcess(secondId) ~> check {
        status shouldBe StatusCodes.Conflict
      }
      runRoute ~> check {
        status shouldBe StatusCodes.OK
      }
  }



}
