package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model.{ContentTypeRange, ContentTypes, StatusCodes}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Json
import org.apache.commons.lang3.StringUtils
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.api.test.{TestData, TestRecord}
import pl.touk.nussknacker.engine.definition.{TestInfoProvider, TestingCapabilities}
import pl.touk.nussknacker.engine.graph.node
import pl.touk.nussknacker.restmodel.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.test.{EitherValuesDetailedMessage, PatientScalaFutures}
import pl.touk.nussknacker.ui.api.helpers.TestCategories.TestCat
import pl.touk.nussknacker.ui.api.helpers.TestFactory.{mapProcessingTypeDataProvider, posting, withPermissions}
import pl.touk.nussknacker.ui.api.helpers.{EspItTest, ProcessTestData}

class TestInfoResourcesSpec extends AnyFunSuite with ScalatestRouteTest with Matchers with FailFastCirceSupport
  with EspItTest with PatientScalaFutures with EitherValuesDetailedMessage {

  private val process: DisplayableProcess = ProcessTestData.sampleDisplayableProcess.copy(category = Some(TestCat))

  private def testInfoProvider(additionalDataSize: Int) = new TestInfoProvider {

    override def getTestingCapabilities(metaData: MetaData, source: node.Source): TestingCapabilities
    = TestingCapabilities(canBeTested = true, canGenerateTestData = true)

    override def generateTestData(metaData: MetaData, source: node.Source, size: Int): Option[TestData]
    = Some(TestData(TestRecord(Json.fromString(s"terefereKuku-$size${StringUtils.repeat("0", additionalDataSize)}")) :: Nil))
  }

  private implicit final val bytes: FromEntityUnmarshaller[Array[Byte]] =
    Unmarshaller.byteArrayUnmarshaller.forContentTypes(ContentTypeRange(ContentTypes.`application/octet-stream`))

  private def route(additionalDataSize: Int = 0) = new TestInfoResources(mapProcessingTypeDataProvider("streaming" -> testInfoProvider(additionalDataSize)),
    processAuthorizer, fetchingProcessRepository, featureTogglesConfig.testDataSettings)

  test("generates data") {
    saveProcess(process) {
      Post("/testInfo/generate/5", posting.toEntity(process)) ~> withPermissions(route(), testPermissionAll) ~> check {
        status shouldEqual StatusCodes.OK
        val content = new String(entityAs[Array[Byte]])
        content shouldBe "\"terefereKuku-5\""
      }
    }
  }

  test("refuses to generate too much data") {
    saveProcess(process) {
      Post("/testInfo/generate/100", posting.toEntity(process)) ~> withPermissions(route(), testPermissionAll) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
      Post("/testInfo/generate/1", posting.toEntity(process)) ~> withPermissions(route(additionalDataSize = 20000), testPermissionAll) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
    }
  }

  test("get full capabilities when user has deploy role") {
    saveProcess(process) {
      Post("/testInfo/capabilities", posting.toEntity(process)) ~> withPermissions(route(), testPermissionAll) ~> check {
        status shouldEqual StatusCodes.OK
        val entity = entityAs[Json]
        entity.hcursor.downField("canBeTested").as[Boolean].rightValue shouldBe true
        entity.hcursor.downField("canGenerateTestData").as[Boolean].rightValue shouldBe true
      }
    }
  }

  test("get empty capabilities when user hasn't got deploy role") {
    saveProcess(process) {
      Post("/testInfo/capabilities", posting.toEntity(process)) ~> withPermissions(route(), testPermissionEmpty) ~> check {
        status shouldEqual StatusCodes.OK
        val entity = entityAs[Json]
        entity.hcursor.downField("canBeTested").as[Boolean].rightValue shouldBe false
        entity.hcursor.downField("canGenerateTestData").as[Boolean].rightValue shouldBe false
      }
    }
  }

}
