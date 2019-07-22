package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import argonaut.PrettyParams
import org.scalatest._
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time.{Millis, Seconds, Span}
import pl.touk.nussknacker.engine.api.{ProcessAdditionalFields, StreamMetaData}
import pl.touk.nussknacker.ui.api.helpers.{EspItTest, ProcessTestData}
import pl.touk.nussknacker.ui.api.helpers.TestFactory._
import pl.touk.nussknacker.ui.codec.UiCodecs._
import pl.touk.nussknacker.restmodel.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.ui.process.marshall.UiProcessMarshaller
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.util.{FileUploadUtils, MultipartUtils}

import scala.concurrent.duration._
import scala.language.higherKinds

class ProcessesExportImportResourcesSpec extends FlatSpec with ScalatestRouteTest with Matchers with Inside
  with ScalaFutures with OptionValues with Eventually with BeforeAndAfterEach with BeforeAndAfterAll with EspItTest {

  implicit override val patienceConfig = PatienceConfig(timeout = scaled(Span(1, Seconds)), interval = scaled(Span(100, Millis)))

  import akka.http.scaladsl.server.RouteConcatenation._
  val routWithAllPermissions = withAllPermissions(processesExportResources) ~ withAllPermissions(processesRoute)
  implicit val loggedUser = LoggedUser("lu", testPermissionEmpty)

  it should "export process and import it" in {
    val processToSave = ProcessTestData.sampleDisplayableProcess
    saveProcess(processToSave) {
      status shouldEqual StatusCodes.OK
    }

    Get(s"/processesExport/${processToSave.id}/2") ~> routWithAllPermissions ~> check {
      val processDetails = UiProcessMarshaller.fromJson(responseAs[String]).toOption.get
      val modified = processDetails.copy(metaData = processDetails.metaData.copy(typeSpecificData = StreamMetaData(Some(987))))

      val multipartForm =
        MultipartUtils.prepareMultiPart(jsonMarshaller.marshallToString(UiProcessMarshaller.toJson(modified)), "process")

      Post(s"/processes/import/${processToSave.id}", multipartForm) ~> routWithAllPermissions ~> check {
        status shouldEqual StatusCodes.OK
        val imported = responseAs[String].decodeOption[DisplayableProcess].get
        imported.properties.typeSpecificProperties.asInstanceOf[StreamMetaData].parallelism shouldBe Some(987)
        imported.id shouldBe processToSave.id
        imported.nodes shouldBe processToSave.nodes
      }


    }
  }

  it should "export process in new version" in {
    val description = "alamakota"
    val processToSave = ProcessTestData.sampleDisplayableProcess
    val processWithDescription = processToSave.copy(properties = processToSave.properties.copy(additionalFields = Some(ProcessAdditionalFields(Some(description), Set.empty, Map.empty))))

    saveProcess(processToSave) {
      status shouldEqual StatusCodes.OK
    }
    updateProcess(processWithDescription) {
      status shouldEqual StatusCodes.OK
    }

    Get(s"/processesExport/${processToSave.id}/2") ~> routWithAllPermissions ~> check {
      responseAs[String] shouldNot include(description)
    }

    Get(s"/processesExport/${processToSave.id}/3") ~> routWithAllPermissions ~> check {
      val latestProcessVersion = responseAs[String]
      latestProcessVersion should include(description)

      Get(s"/processesExport/${processToSave.id}") ~> routWithAllPermissions ~> check {
        responseAs[String] shouldBe latestProcessVersion
      }

    }

  }

  it should "fail to import process with different id" in {
    val processToSave = ProcessTestData.sampleDisplayableProcess
    saveProcess(processToSave) {
      status shouldEqual StatusCodes.OK
    }

    Get(s"/processesExport/${processToSave.id}/2") ~> routWithAllPermissions ~> check {
      val processDetails = UiProcessMarshaller.fromJson(responseAs[String]).toOption.get
      val modified = processDetails.copy(metaData = processDetails.metaData.copy(id = "SOMEVERYFAKEID"))

      val multipartForm =
        FileUploadUtils.prepareMultiPart(jsonMarshaller.marshallToString(UiProcessMarshaller.toJson(modified)), "process")

      Post(s"/processes/import/${processToSave.id}", multipartForm) ~> routWithAllPermissions ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
    }
  }

}