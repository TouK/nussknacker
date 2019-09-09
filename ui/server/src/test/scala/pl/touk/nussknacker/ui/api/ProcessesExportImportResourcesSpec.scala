package pl.touk.nussknacker.ui.api

import java.util.regex.Pattern

import akka.http.scaladsl.model.MediaTypes.`application/json`
import akka.http.scaladsl.model.{ContentTypeRange, StatusCodes}
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Json
import org.scalatest._
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time.{Millis, Seconds, Span}
import pl.touk.nussknacker.engine.api.{ProcessAdditionalFields, StreamMetaData}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.ui.api.helpers.{EspItTest, ProcessTestData}
import pl.touk.nussknacker.ui.api.helpers.TestFactory._
import pl.touk.nussknacker.restmodel.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.ui.process.marshall.{ProcessConverter, UiProcessMarshaller}
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.util.{FileUploadUtils, MultipartUtils}
import pl.touk.nussknacker.restmodel.CirceRestCodecs.displayableDecoder

import scala.language.higherKinds

class ProcessesExportImportResourcesSpec extends FlatSpec with ScalatestRouteTest with Matchers with Inside with FailFastCirceSupport
  with ScalaFutures with OptionValues with Eventually with BeforeAndAfterEach with BeforeAndAfterAll with EspItTest {

  implicit override val patienceConfig = PatienceConfig(timeout = scaled(Span(1, Seconds)), interval = scaled(Span(100, Millis)))

  private implicit final val string: FromEntityUnmarshaller[String] = Unmarshaller.stringUnmarshaller.forContentTypes(ContentTypeRange.*)

  import akka.http.scaladsl.server.RouteConcatenation._
  val routeWithAllPermissions = withAllPermissions(processesExportResources) ~ withAllPermissions(processesRoute)
  implicit val loggedUser = LoggedUser("lu", testPermissionEmpty)

  it should "export process and import it" in {
    val processToSave = ProcessTestData.sampleDisplayableProcess
    saveProcess(processToSave) {
      status shouldEqual StatusCodes.OK
    }

    Get(s"/processesExport/${processToSave.id}/2") ~> routeWithAllPermissions ~> check {
      val response = responseAs[String]
      val processDetails = UiProcessMarshaller.fromJson(response).toOption.get
      assertProcessPrettyPrinted(response, processDetails)

      val modified = processDetails.copy(metaData = processDetails.metaData.copy(typeSpecificData = StreamMetaData(Some(987))))
      val multipartForm =
        MultipartUtils.prepareMultiPart(UiProcessMarshaller.toJson(modified).spaces2, "process")
      Post(s"/processes/import/${processToSave.id}", multipartForm) ~> routeWithAllPermissions ~> check {
        status shouldEqual StatusCodes.OK
        val imported = responseAs[DisplayableProcess]
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

    Get(s"/processesExport/${processToSave.id}/2") ~> routeWithAllPermissions ~> check {
      val response = responseAs[String]
      response shouldNot include(description)
      assertProcessPrettyPrinted(response, processToSave)
    }

    Get(s"/processesExport/${processToSave.id}/3") ~> routeWithAllPermissions ~> check {
      val latestProcessVersion = io.circe.parser.parse(responseAs[String])
      latestProcessVersion.right.get.spaces2 should include(description)

      Get(s"/processesExport/${processToSave.id}") ~> routeWithAllPermissions ~> check {
        io.circe.parser.parse(responseAs[String]) shouldBe latestProcessVersion
      }

    }

  }

  it should "fail to import process with different id" in {
    val processToSave = ProcessTestData.sampleDisplayableProcess
    saveProcess(processToSave) {
      status shouldEqual StatusCodes.OK
    }

    Get(s"/processesExport/${processToSave.id}/2") ~> routeWithAllPermissions ~> check {
      val processDetails = UiProcessMarshaller.fromJson(responseAs[String]).toOption.get
      val modified = processDetails.copy(metaData = processDetails.metaData.copy(id = "SOMEVERYFAKEID"))

      val multipartForm =
        FileUploadUtils.prepareMultiPart(UiProcessMarshaller.toJson(modified).spaces2, "process")

      Post(s"/processes/import/${processToSave.id}", multipartForm) ~> routeWithAllPermissions ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
    }
  }

  private def assertProcessPrettyPrinted(response: String, process: CanonicalProcess): Unit = {
    response.replace("\n", "").replace(" ", "") shouldBe
      UiProcessMarshaller.toJson(process).nospaces.replace("\n", "").replace(" ", "")
  }

  private def assertProcessPrettyPrinted(response: String, process: DisplayableProcess): Unit = {
    assertProcessPrettyPrinted(response, ProcessConverter.fromDisplayable(process))
  }

}