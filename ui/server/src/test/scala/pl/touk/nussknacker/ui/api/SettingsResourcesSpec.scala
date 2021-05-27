package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import org.scalatest._
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.api.helpers.EspItTest
import pl.touk.nussknacker.ui.config.processtoolbars.{ProcessToolbars, ProcessToolbarsConfigWithId, ToolbarButton, ToolbarButtonType, ToolbarButtonVariant}

class SettingsResourcesSpec extends FunSpec with ScalatestRouteTest with FailFastCirceSupport
  with Matchers with PatientScalaFutures with EitherValues with BeforeAndAfterEach with BeforeAndAfterAll with EspItTest {

  //Values are exists at test/resources/application.conf
  val intervalTimeProcesses = 20000
  val intervalTimeHealthCheck = 30000

  it("should return base intervalSettings") {
    getSettings ~> check {
      status shouldBe StatusCodes.OK
      val responseSettings = responseAs[UISettings]
      val data = responseSettings.features

      data.intervalTimeSettings.processes shouldBe intervalTimeProcesses
      data.intervalTimeSettings.healthCheck shouldBe intervalTimeHealthCheck
    }
  }

  it("should return default process config") {
    val processName = "test"
    createProcess(ProcessName(processName))

    getProcessSettingsToolbars(processName) ~> check {
      status shouldBe StatusCodes.OK

      val toolbarsConfig = responseAs[ProcessToolbarsConfigWithId]

      val expectedTopRight = List(
        ProcessToolbars(
          id = "PROCESS-INFO",
          title = None,
          buttonsVariant = None,
          buttons = Some(List(
            ToolbarButton(
              `type` = ToolbarButtonType.ProcessSave,
              icon = Some("/assets/buttons/save.svg"),
              title = Some("save"),
              templateHref = None),
            ToolbarButton(
              `type` = ToolbarButtonType.CustomLink,
              title = Some("metrics"),
              icon = Some("/assets/buttons/metrics.svg"),
              templateHref = Some("/metrics/{{processId}}"))
          ))
        ),
        ProcessToolbars(
          id = "VIEW-PANEL",
          title = Some("view"),
          buttonsVariant = None,
          buttons = Some(List(
            ToolbarButton(
              `type` = ToolbarButtonType.ViewBusinessView,
              title = None,
              icon = None,
              templateHref = None)
          ))
        )
      )

      val expectedTopLeft = List(
        ProcessToolbars(
          id = "TIPS-PANEL",
          title = None,
          buttonsVariant = None,
          buttons = None)
      )

      toolbarsConfig.topRight shouldBe Some(expectedTopRight)
      toolbarsConfig.topLeft shouldBe Some(expectedTopLeft)

      toolbarsConfig.bottomRight shouldBe None
      toolbarsConfig.bottomLeft shouldBe None
      toolbarsConfig.hidden shouldBe None
    }
  }

  it("should return default subprocess config") {
    val subprocessName = "test"
    createProcess(ProcessName(subprocessName), isSubprocess = true)

    getProcessSettingsToolbars(subprocessName) ~> check {
      status shouldBe StatusCodes.OK

      val toolbarsConfig = responseAs[ProcessToolbarsConfigWithId]

      val expectedTopRight = List(
        ProcessToolbars(
          id = "VIEW-PANEL",
          title = Some("view"),
          buttonsVariant = None,
          buttons = Some(List(
            ToolbarButton(
              `type` = ToolbarButtonType.ViewBusinessView,
              title = None,
              icon = None,
              templateHref = None)
          ))
        )
      )

      val expectedTopLeft = List(
        ProcessToolbars(
          id = "TIPS-PANEL",
          title = None,
          buttonsVariant = None,
          buttons = None)
      )

      toolbarsConfig.topRight shouldBe Some(expectedTopRight)
      toolbarsConfig.topLeft shouldBe Some(expectedTopLeft)

      toolbarsConfig.bottomRight shouldBe None
      toolbarsConfig.bottomLeft shouldBe None
      toolbarsConfig.hidden shouldBe None
    }
  }

  it("should return merged process category config") {
    val subprocessName = "test"
    createProcess(ProcessName(subprocessName), "Category1", isSubprocess = false)

    getProcessSettingsToolbars(subprocessName) ~> check {
      status shouldBe StatusCodes.OK

      val toolbarsConfig = responseAs[ProcessToolbarsConfigWithId]

      val expectedTopRight = List(
        ProcessToolbars(
          id = "PROCESS-INFO",
          title = None,
          buttonsVariant = None,
          buttons = Some(List(
            ToolbarButton(
              `type` = ToolbarButtonType.ProcessSave,
              icon = Some("/assets/buttons/save.svg"),
              title = Some("save"),
              templateHref = None),
            ToolbarButton(
              `type` = ToolbarButtonType.Deploy,
              icon = None,
              title = None,
              templateHref = None),
            ToolbarButton(
              `type` = ToolbarButtonType.CustomLink,
              title = Some("metrics"),
              icon = Some("/assets/buttons/metrics.svg"),
              templateHref = Some("/metrics/{{processId}}"))
          ))
        )
      )

      val expectedTopLeft = List(
        ProcessToolbars(
          id = "TIPS-PANEL",
          title = None,
          buttonsVariant = None,
          buttons = None)
      )

      toolbarsConfig.topRight shouldBe Some(expectedTopRight)
      toolbarsConfig.topLeft shouldBe Some(expectedTopLeft)

      toolbarsConfig.bottomRight shouldBe None
      toolbarsConfig.bottomLeft shouldBe None
      toolbarsConfig.hidden shouldBe None
    }
  }

  it("should return merged subprocess category config") {
    val subprocessName = "test"
    createProcess(ProcessName(subprocessName), "Category1", isSubprocess = true)

    getProcessSettingsToolbars(subprocessName) ~> check {
      status shouldBe StatusCodes.OK

      val toolbarsConfig = responseAs[ProcessToolbarsConfigWithId]

      val expectedTopRight = List(
        ProcessToolbars(
          id = "PROCESS-INFO",
          title = None,
          buttonsVariant = None,
          buttons = Some(List(
            ToolbarButton(
              `type` = ToolbarButtonType.ProcessSave,
              title = Some("save"),
              icon = Some("/assets/buttons/save.svg"),
              templateHref = None),
            ToolbarButton(
              `type` = ToolbarButtonType.CustomLink,
              title = Some("metrics"),
              icon = Some("/assets/buttons/metrics.svg"),
              templateHref = Some("/metrics/{{processId}}"))
          ))
        )
      )

      val expectedTopLeft = List(
        ProcessToolbars(
          id = "TIPS-PANEL",
          title = None,
          buttonsVariant = None,
          buttons = None)
      )

      toolbarsConfig.topRight shouldBe Some(expectedTopRight)
      toolbarsConfig.topLeft shouldBe Some(expectedTopLeft)

      toolbarsConfig.bottomRight shouldBe None
      toolbarsConfig.bottomLeft shouldBe None
      toolbarsConfig.hidden shouldBe None
    }
  }
}
