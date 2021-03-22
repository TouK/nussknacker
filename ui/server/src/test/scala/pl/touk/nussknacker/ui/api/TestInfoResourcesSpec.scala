package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model.{ContentTypeRange, ContentTypes, StatusCodes}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Json
import org.scalatest.{EitherValues, FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.definition.{TestInfoProvider, TestingCapabilities}
import pl.touk.nussknacker.engine.graph.node
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.api.helpers.TestFactory.{mapProcessingTypeDataProvider, posting, withPermissions}
import pl.touk.nussknacker.ui.api.helpers.{EspItTest, ProcessTestData, TestFactory}

class TestInfoResourcesSpec extends FunSuite with ScalatestRouteTest with Matchers with FailFastCirceSupport
  with EspItTest with PatientScalaFutures with EitherValues {

  private val testInfoProvider = new TestInfoProvider {

    override def getTestingCapabilities(metaData: MetaData, source: node.Source): TestingCapabilities
      = TestingCapabilities(canBeTested = true, canGenerateTestData = true)

    override def generateTestData(metaData: MetaData, source: node.Source, size: Int): Option[Array[Byte]]
      = Some(s"terefereKuku-$size".getBytes())
  }

  private implicit final val bytes: FromEntityUnmarshaller[Array[Byte]] =
    Unmarshaller.byteArrayUnmarshaller.forContentTypes(ContentTypeRange(ContentTypes.`application/octet-stream`))

  private val route = new TestInfoResources(mapProcessingTypeDataProvider("streaming" -> testInfoProvider), processAuthorizer, fetchingProcessRepository)

  test("generates data"){
    val process = ProcessTestData.sampleDisplayableProcess
    saveProcess(process) {
      Post("/testInfo/generate/5", posting.toEntity(process)) ~> withPermissions(route, testPermissionAll) ~> check {
        status shouldEqual StatusCodes.OK
        val entity = new String(entityAs[Array[Byte]])
        entity shouldBe s"terefereKuku-5"
      }
    }
  }

  test("get full capabilities when user has deploy role"){
    val process = ProcessTestData.sampleDisplayableProcess
    saveProcess(process) {
      Post("/testInfo/capabilities", posting.toEntity(process)) ~> withPermissions(route, testPermissionAll) ~> check {
        status shouldEqual StatusCodes.OK
        val entity = entityAs[Json]
        entity.hcursor.downField("canBeTested").as[Boolean].right.value shouldBe true
        entity.hcursor.downField("canGenerateTestData").as[Boolean].right.value shouldBe true
      }
    }
  }

  test("get empty capabilities when user hasn't got deploy role"){
    val process = ProcessTestData.sampleDisplayableProcess
    saveProcess(process) {
      Post("/testInfo/capabilities", posting.toEntity(process)) ~> withPermissions(route, testPermissionEmpty) ~> check {
        status shouldEqual StatusCodes.OK
        val entity = entityAs[Json]
        entity.hcursor.downField("canBeTested").as[Boolean].right.value shouldBe false
        entity.hcursor.downField("canGenerateTestData").as[Boolean].right.value shouldBe false
      }
    }
  }

}
