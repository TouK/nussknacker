package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model.{ContentTypeRange, ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import org.scalatest._
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time.{Millis, Seconds, Span}
import pl.touk.nussknacker.engine.api.{ProcessAdditionalFields, StreamMetaData}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.ui.api.helpers.{EspItTest, ProcessTestData}
import pl.touk.nussknacker.ui.api.helpers.TestFactory._
import pl.touk.nussknacker.restmodel.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.util.{FileUploadUtils, MultipartUtils}

import scala.language.higherKinds

class ProcessesExportImportResourcesSpec extends FunSuite with ScalatestRouteTest with Matchers with Inside with FailFastCirceSupport
  with ScalaFutures with OptionValues with Eventually with BeforeAndAfterEach with BeforeAndAfterAll with EspItTest {

  implicit override val patienceConfig: PatienceConfig = PatienceConfig(timeout = scaled(Span(1, Seconds)), interval = scaled(Span(100, Millis)))

  private implicit final val string: FromEntityUnmarshaller[String] = Unmarshaller.stringUnmarshaller.forContentTypes(ContentTypeRange.*)

  import akka.http.scaladsl.server.RouteConcatenation._
  private val routeWithAllPermissions = withAllPermissions(processesExportResources) ~ withAllPermissions(processesRoute)
  private val adminRoute = asAdmin(processesExportResources) ~ asAdmin(processesRoute)
  private implicit val loggedUser: LoggedUser = LoggedUser("lu", testPermissionEmpty)

  test("export process from displayable") {
    val processToExport = ProcessTestData.sampleDisplayableProcess

    Post(s"/processesExport", processToExport) ~> routeWithAllPermissions ~> check {
      status shouldEqual StatusCodes.OK
      val exported = responseAs[String]
      val processDetails = ProcessMarshaller.fromJson(exported).toOption.get
      
      processDetails shouldBe ProcessConverter.fromDisplayable(processToExport)
    }

  }

  test("export process and import it (as common user)") {
    runImportExportTest(routeWithAllPermissions)
  }

  test("export process and import it (as admin)") {
    runImportExportTest(adminRoute)
  }

  private def runImportExportTest(route: Route): Unit = {
    val processToSave = ProcessTestData.sampleDisplayableProcess
    saveProcess(processToSave) {
      status shouldEqual StatusCodes.OK
    }

    Get(s"/processesExport/${processToSave.id}/2") ~> route ~> check {
      val response = responseAs[String]
      val processDetails = ProcessMarshaller.fromJson(response).toOption.get
      assertProcessPrettyPrinted(response, processDetails)

      val modified = processDetails.copy(metaData = processDetails.metaData.copy(typeSpecificData = StreamMetaData(Some(987))))
      val multipartForm =
        MultipartUtils.prepareMultiPart(ProcessMarshaller.toJson(modified).spaces2, "process")
      Post(s"/processes/import/${processToSave.id}", multipartForm) ~> route ~> check {
        status shouldEqual StatusCodes.OK
        val imported = responseAs[DisplayableProcess]
        imported.properties.typeSpecificProperties.asInstanceOf[StreamMetaData].parallelism shouldBe Some(987)
        imported.id shouldBe processToSave.id
        imported.nodes shouldBe processToSave.nodes
      }
    }
  }

  test("export process in new version") {
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

  test("fail to import process with different id") {
    val processToSave = ProcessTestData.sampleDisplayableProcess
    saveProcess(processToSave) {
      status shouldEqual StatusCodes.OK
    }

    Get(s"/processesExport/${processToSave.id}/2") ~> routeWithAllPermissions ~> check {
      val processDetails = ProcessMarshaller.fromJson(responseAs[String]).toOption.get
      val modified = processDetails.copy(metaData = processDetails.metaData.copy(id = "SOMEVERYFAKEID"))

      val multipartForm =
        FileUploadUtils.prepareMultiPart(ProcessMarshaller.toJson(modified).spaces2, "process")

      Post(s"/processes/import/${processToSave.id}", multipartForm) ~> routeWithAllPermissions ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
    }
  }

  test("export pdf") {
    val processToSave = ProcessTestData.sampleDisplayableProcess
    saveProcess(processToSave) {
      status shouldEqual StatusCodes.OK

      val testSvg = "<svg viewBox=\"0 0 120 70\" xmlns=\"http://www.w3.org/2000/svg\">\n  " +
        "<path d=\"M20,20h20m5,0h20m5,0h20\" stroke=\"#c00000\" stroke-width=\"10\"/>\n  " +
        "<path d=\"M20,40h20m5,0h20m5,0h20M30,30v20m25,0v-20m25,0v20\" stroke=\"#008000\" stroke-width=\"6\"/>\n</svg>"

      Post(s"/processesExport/pdf/${processToSave.id}/2", HttpEntity(testSvg)) ~> routeWithAllPermissions ~> check {

        status shouldEqual StatusCodes.OK
        contentType shouldEqual ContentTypes.`application/octet-stream`
        //just simple sanity check that it's really pdf...
        responseAs[String] should startWith ("%PDF")
      }
    }

  }

  private def assertProcessPrettyPrinted(response: String, process: CanonicalProcess): Unit = {
    val expected = ProcessMarshaller.toJson(process).spaces2
    response shouldBe expected
  }

  private def assertProcessPrettyPrinted(response: String, process: DisplayableProcess): Unit = {
    assertProcessPrettyPrinted(response, ProcessConverter.fromDisplayable(process))
  }

}