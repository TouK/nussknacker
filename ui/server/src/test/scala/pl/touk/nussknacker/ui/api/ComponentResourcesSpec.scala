package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, EitherValues, FunSpec, Matchers}
import pl.touk.nussknacker.restmodel.component.ComponentListElement
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.api.helpers.EspItTest
import pl.touk.nussknacker.ui.component.DefaultComponentService

class ComponentResourcesSpec extends FunSpec with ScalatestRouteTest with FailFastCirceSupport
  with Matchers with PatientScalaFutures with EitherValues with BeforeAndAfterEach with BeforeAndAfterAll with EspItTest {

  private val componentService = DefaultComponentService(testConfig, testProcessingTypeDataProvider, fetchingProcessRepository, subprocessRepository, processCategoryService)
  private val componentRoute = new ComponentResource(componentService)

  //Here we test only response, logic is tested in DefaultComponentServiceSpec
  it("should return users(test, admin) components list") {
    getComponents() ~> check {
      status shouldBe StatusCodes.OK
      val testCatComponents = responseAs[List[ComponentListElement]]
      testCatComponents.nonEmpty shouldBe true

      getComponents(true) ~> check {
        status shouldBe StatusCodes.OK
        val adminComponents = responseAs[List[ComponentListElement]]
        adminComponents.nonEmpty shouldBe true

        adminComponents.size > testCatComponents.size shouldBe true
      }
    }
  }

  protected def getComponents(isAdmin: Boolean = false): RouteTestResult =
    Get(s"/components") ~> routeWithPermissions(componentRoute, isAdmin)
}

