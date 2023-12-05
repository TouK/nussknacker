package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model.{ContentTypeRange, ContentTypes, StatusCodes}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Json
import org.apache.commons.lang3.StringUtils
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.engine.api.test.ScenarioTestData
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.definition.test.{
  PreliminaryScenarioTestData,
  PreliminaryScenarioTestRecord,
  TestInfoProvider,
  TestingCapabilities
}
import pl.touk.nussknacker.test.{EitherValuesDetailedMessage, PatientScalaFutures}
import pl.touk.nussknacker.ui.api.helpers.TestCategories.Category1
import pl.touk.nussknacker.ui.api.helpers.TestFactory.{mapProcessingTypeDataProvider, posting, withPermissions}
import pl.touk.nussknacker.ui.api.helpers.{NuResourcesTest, ProcessTestData}

class TestInfoResourcesSpec
    extends AnyFunSuite
    with ScalatestRouteTest
    with Matchers
    with FailFastCirceSupport
    with NuResourcesTest
    with PatientScalaFutures
    with EitherValuesDetailedMessage {

  private val process: DisplayableProcess = ProcessTestData.sampleDisplayableProcess.copy(category = Category1)

  private def testInfoProvider(additionalDataSize: Int) = new TestInfoProvider {

    override def getTestingCapabilities(scenario: CanonicalProcess): TestingCapabilities =
      TestingCapabilities(canBeTested = true, canGenerateTestData = true, canTestWithForm = false)

    override def getTestParameters(scenario: CanonicalProcess): Map[String, List[Parameter]] = ???

    override def generateTestData(scenario: CanonicalProcess, size: Int): Option[PreliminaryScenarioTestData] = Some(
      PreliminaryScenarioTestData(
        PreliminaryScenarioTestRecord.Standard(
          "sourceId",
          Json.fromString(s"terefereKuku-$size${StringUtils.repeat("0", additionalDataSize)}")
        ) :: Nil
      )
    )

    override def prepareTestData(
        preliminaryTestData: PreliminaryScenarioTestData,
        scenario: CanonicalProcess
    ): Either[String, ScenarioTestData] = {
      ???
    }

  }

  private implicit final val bytes: FromEntityUnmarshaller[Array[Byte]] =
    Unmarshaller.byteArrayUnmarshaller.forContentTypes(ContentTypeRange(ContentTypes.`application/octet-stream`))

  private def route(additionalDataSize: Int = 0) = new TestInfoResources(
    processAuthorizer,
    processService,
    createScenarioTestService(mapProcessingTypeDataProvider("streaming" -> testInfoProvider(additionalDataSize)))
  )

  test("generates data") {
    saveProcess(process) {
      Post("/testInfo/generate/5", posting.toEntity(process)) ~> withPermissions(route(), testPermissionAll) ~> check {
        implicit val contentUnmarshaller: FromEntityUnmarshaller[String] = Unmarshaller.stringUnmarshaller
        status shouldEqual StatusCodes.OK
        val content = responseAs[String]
        content shouldBe """{"sourceId":"sourceId","record":"terefereKuku-5"}"""
      }
    }
  }

  test("refuses to generate too much data") {
    saveProcess(process) {
      Post("/testInfo/generate/100", posting.toEntity(process)) ~> withPermissions(
        route(),
        testPermissionAll
      ) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
      Post("/testInfo/generate/1", posting.toEntity(process)) ~> withPermissions(
        route(additionalDataSize = 20000),
        testPermissionAll
      ) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
    }
  }

  test("get full capabilities when user has deploy role") {
    saveProcess(process) {
      Post("/testInfo/capabilities", posting.toEntity(process)) ~> withPermissions(
        route(),
        testPermissionAll
      ) ~> check {
        status shouldEqual StatusCodes.OK
        val entity = entityAs[Json]
        entity.hcursor.downField("canBeTested").as[Boolean].rightValue shouldBe true
        entity.hcursor.downField("canGenerateTestData").as[Boolean].rightValue shouldBe true
      }
    }
  }

  test("get empty capabilities when user hasn't got deploy role") {
    saveProcess(process) {
      Post("/testInfo/capabilities", posting.toEntity(process)) ~> withPermissions(
        route(),
        testPermissionEmpty
      ) ~> check {
        status shouldEqual StatusCodes.OK
        val entity = entityAs[Json]
        entity.hcursor.downField("canBeTested").as[Boolean].rightValue shouldBe false
        entity.hcursor.downField("canGenerateTestData").as[Boolean].rightValue shouldBe false
      }
    }
  }

}
