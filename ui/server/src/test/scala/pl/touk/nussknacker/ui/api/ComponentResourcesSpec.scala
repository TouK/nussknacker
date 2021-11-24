package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import org.scalatest._
import pl.touk.nussknacker.engine.api.component.{ComponentId, ComponentType}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.restmodel.component.{ComponentListElement, ComponentUsagesInScenario}
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.api.helpers.{EspItTest, TestCategories, TestProcessingTypes}
import pl.touk.nussknacker.ui.component.{ComponentIdProvider, DefaultComponentIdProvider, DefaultComponentService}

class ComponentResourcesSpec extends FunSpec with ScalatestRouteTest with FailFastCirceSupport
  with Matchers with PatientScalaFutures with EitherValues with BeforeAndAfterEach with BeforeAndAfterAll with EspItTest {

  //These should be defined as lazy val's because of racing, there are some missing tables in db..
  private lazy val componentService = DefaultComponentService(testConfig, testProcessingTypeDataProvider, processService, subprocessRepository, processCategoryService)
  private lazy val componentRoute = new ComponentResource(componentService)
  private val defaultComponentIdProvider: ComponentIdProvider = new DefaultComponentIdProvider(Map.empty)

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

  it("should return component usages") {
    val processName = ProcessName("someTest")
    val sourceComponentName = "real-kafka-avro" //it's real component name from DevProcessConfigCreator
    val process = EspProcessBuilder
      .id(processName.value)
      .exceptionHandler()
      .source("source", sourceComponentName)
      .emptySink("sink", "kafka-avro")

    val processId = createProcess(process, TestCategories.Category1, TestProcessingTypes.Streaming)
    val componentId = defaultComponentIdProvider.createComponentId(TestProcessingTypes.Streaming, sourceComponentName, ComponentType.Source)

    getComponentUsages(componentId, isAdmin = true) ~> check {
      status shouldBe StatusCodes.OK
      val processes = responseAs[List[ComponentUsagesInScenario]]
      processes.size shouldBe 1

      val process = processes.head
      process.processId shouldBe processId
      process.name shouldBe processName
      process.processCategory shouldBe TestCategories.Category1
      process.isSubprocess shouldBe false
    }
  }

  it("should return 404 when component not exist") {
    val componentId = ComponentId("not-exist-component")

    getComponentUsages(componentId, isAdmin = true) ~> check {
      status shouldBe StatusCodes.NotFound
    }
  }

  private def getComponents(isAdmin: Boolean = false): RouteTestResult =
    Get(s"/components") ~> routeWithPermissions(componentRoute, isAdmin)

  private def getComponentUsages(componentId: ComponentId, isAdmin: Boolean = false): RouteTestResult =
    Get(s"/components/$componentId/usages") ~> routeWithPermissions(componentRoute, isAdmin)
}
