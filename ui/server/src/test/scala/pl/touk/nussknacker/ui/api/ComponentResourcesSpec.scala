package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import org.scalatest._
import pl.touk.nussknacker.engine.api.component.{ComponentId, ComponentType}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.restmodel.component.{ComponentListElement, ComponentProcess}
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.api.helpers.ProcessTestData.existingSourceFactory
import pl.touk.nussknacker.ui.api.helpers.{EspItTest, ProcessTestData, TestCategories, TestProcessingTypes}
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

  it("should return component's processes list") {
    val processName = ProcessName("someTest")
    val process = ProcessTestData.validProcessWithId(processName.value)
    val processId = createProcess(process, TestCategories.Category1, TestProcessingTypes.Streaming)
    val componentId = ComponentId(TestProcessingTypes.Streaming, existingSourceFactory, ComponentType.Source)

    getComponentProcesses(componentId) ~> check {
      status shouldBe StatusCodes.OK
      val processes = responseAs[List[ComponentProcess]]

      processes.size shouldBe 1
      processes.head.id shouldBe processId
      processes.head.name shouldBe processName
      processes.head.processCategory shouldBe TestCategories.Category1
      processes.head.isSubprocess shouldBe false
    }
  }

  it("should return 404 when component not exist") {
    val componentId = ComponentId.create("not-exist-component")

    getComponentProcesses(componentId) ~> check {
      status shouldBe StatusCodes.NotFound
    }
  }

  private def getComponents(isAdmin: Boolean = false): RouteTestResult =
    Get(s"/components") ~> routeWithPermissions(componentRoute, isAdmin)

  private def getComponentProcesses(componentId: ComponentId, isAdmin: Boolean = false): RouteTestResult =
    Get(s"/components/$componentId/processes") ~> routeWithPermissions(componentRoute, isAdmin)
}
