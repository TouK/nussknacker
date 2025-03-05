package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model.{ContentTypeRange, ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.syntax._
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Inside, OptionValues}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.{ProcessAdditionalFields, StreamMetaData}
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.restmodel.validation.ScenarioGraphWithValidationResult
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.test.base.it.NuResourcesTest
import pl.touk.nussknacker.test.utils.domain.ProcessTestData
import pl.touk.nussknacker.test.utils.domain.TestFactory.{asAdmin, processResolverByProcessingType, withAllPermissions}
import pl.touk.nussknacker.ui.process.marshall.CanonicalProcessConverter
import pl.touk.nussknacker.ui.util.MultipartUtils

class ProcessesExportImportResourcesSpec
    extends AnyFunSuite
    with ScalatestRouteTest
    with Matchers
    with Inside
    with FailFastCirceSupport
    with PatientScalaFutures
    with OptionValues
    with BeforeAndAfterEach
    with BeforeAndAfterAll
    with NuResourcesTest {

  import akka.http.scaladsl.server.RouteConcatenation._

  private implicit final val string: FromEntityUnmarshaller[String] =
    Unmarshaller.stringUnmarshaller.forContentTypes(ContentTypeRange.*)

  private val processesExportResources = new ProcessesExportResources(
    futureFetchingScenarioRepository,
    processService,
    scenarioActivityRepository,
    processResolverByProcessingType,
    dbioRunner,
  )

  private val routeWithAllPermissions = withAllPermissions(processesExportResources) ~
    withAllPermissions(processesRoute)
  private val adminRoute = asAdmin(processesExportResources) ~ asAdmin(processesRoute)

  test("export process from scenarioGraph") {
    val scenarioGraphToExport = ProcessTestData.sampleScenarioGraph
    createEmptyProcess(ProcessTestData.sampleProcessName)

    Post(
      s"/processesExport/${ProcessTestData.sampleProcessName}",
      scenarioGraphToExport
    ) ~> routeWithAllPermissions ~> check {
      status shouldEqual StatusCodes.OK
      val exported       = responseAs[String]
      val processDetails = ProcessMarshaller.fromJson(exported).toOption.get

      processDetails shouldBe CanonicalProcessConverter.fromScenarioGraph(
        scenarioGraphToExport,
        ProcessTestData.sampleProcessName
      )
    }
  }

  test("export process and import it (as common user)") {
    runImportExportTest(routeWithAllPermissions)
  }

  test("export process and import it (as admin)") {
    runImportExportTest(adminRoute)
  }

  private def runImportExportTest(route: Route): Unit = {
    val scenarioGraphToSave = ProcessTestData.sampleScenarioGraph
    saveProcess(scenarioGraphToSave) {
      status shouldEqual StatusCodes.OK
    }

    Get(s"/processesExport/${ProcessTestData.sampleProcessName}/2") ~> route ~> check {
      val response       = responseAs[String]
      val processDetails = ProcessMarshaller.fromJson(response).toOption.get
      assertProcessPrettyPrinted(response, processDetails)

      val modified = processDetails.copy(metaData =
        processDetails.metaData.withTypeSpecificData(typeSpecificData = StreamMetaData(Some(987)))
      )
      val multipartForm = MultipartUtils.prepareMultiPart(modified.asJson.spaces2, "process")
      Post(s"/processes/import/${ProcessTestData.sampleProcessName}", multipartForm) ~> route ~> check {
        status shouldEqual StatusCodes.OK
        val imported = responseAs[ScenarioGraphWithValidationResult]
        imported.scenarioGraph.properties.typeSpecificProperties.asInstanceOf[StreamMetaData].parallelism shouldBe Some(
          987
        )
        imported.scenarioGraph.nodes shouldBe scenarioGraphToSave.nodes
      }
    }
  }

  test("export process in new version") {
    val description         = "alamakota"
    val scenarioGraphToSave = ProcessTestData.sampleScenarioGraph
    val processWithDescription = scenarioGraphToSave.copy(properties =
      scenarioGraphToSave.properties.copy(additionalFields =
        ProcessAdditionalFields(Some(description), Map.empty, StreamMetaData.typeName)
      )
    )

    saveProcess(scenarioGraphToSave) {
      status shouldEqual StatusCodes.OK
    }
    updateProcess(processWithDescription) {
      status shouldEqual StatusCodes.OK
    }

    Get(s"/processesExport/${ProcessTestData.sampleProcessName}/2") ~> routeWithAllPermissions ~> check {
      val response = responseAs[String]
      response shouldNot include(description)
      assertProcessPrettyPrinted(response, scenarioGraphToSave)
    }

    Get(s"/processesExport/${ProcessTestData.sampleProcessName}/3") ~> routeWithAllPermissions ~> check {
      val latestProcessVersion = io.circe.parser.parse(responseAs[String])
      latestProcessVersion.toOption.get.spaces2 should include(description)

      Get(s"/processesExport/${ProcessTestData.sampleProcessName}") ~> routeWithAllPermissions ~> check {
        io.circe.parser.parse(responseAs[String]) shouldBe latestProcessVersion
      }

    }

  }

  test("export pdf") {
    val scenarioGraphToSave = ProcessTestData.sampleScenarioGraph
    saveProcess(scenarioGraphToSave) {
      status shouldEqual StatusCodes.OK

      val testSvg = "<svg viewBox=\"0 0 120 70\" xmlns=\"http://www.w3.org/2000/svg\">\n  " +
        "<path d=\"M20,20h20m5,0h20m5,0h20\" stroke=\"#c00000\" stroke-width=\"10\"/>\n  " +
        "<path d=\"M20,40h20m5,0h20m5,0h20M30,30v20m25,0v-20m25,0v20\" stroke=\"#008000\" stroke-width=\"6\"/>\n</svg>"

      Post(
        s"/processesExport/pdf/${ProcessTestData.sampleProcessName}/2",
        HttpEntity(testSvg)
      ) ~> routeWithAllPermissions ~> check {

        status shouldEqual StatusCodes.OK
        contentType shouldEqual ContentTypes.`application/octet-stream`
        // just simple sanity check that it's really pdf...
        responseAs[String] should startWith("%PDF")
      }
    }

  }

  private def assertProcessPrettyPrinted(response: String, expectedProcess: CanonicalProcess): Unit = {
    response shouldBe expectedProcess.asJson.spaces2
  }

  private def assertProcessPrettyPrinted(response: String, process: ScenarioGraph): Unit = {
    assertProcessPrettyPrinted(
      response,
      CanonicalProcessConverter.fromScenarioGraph(process, ProcessTestData.sampleProcessName)
    )
  }

}
