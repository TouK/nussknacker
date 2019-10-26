package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model.{ContentTypeRange, ContentTypes, StatusCodes}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.definition.{TestInfoProvider, TestingCapabilities}
import pl.touk.nussknacker.engine.graph.node
import pl.touk.nussknacker.ui.api.helpers.{EspItTest, ProcessTestData}
import pl.touk.nussknacker.ui.api.helpers.TestFactory.{posting, testPermissionAll, withPermissions}

class TestInfoResourcesSpec extends FunSuite with ScalatestRouteTest with Matchers with FailFastCirceSupport with EspItTest {

  private val testInfoProvider = new TestInfoProvider {

    override def getTestingCapabilities(metaData: MetaData, source: node.Source): TestingCapabilities
      = TestingCapabilities(canBeTested = false, canGenerateTestData = true)

    override def generateTestData(metaData: MetaData, source: node.Source, size: Int): Option[Array[Byte]]
      = Some(s"terefereKuku-$size".getBytes())
  }

  private implicit final val bytes: FromEntityUnmarshaller[Array[Byte]] =
    Unmarshaller.byteArrayUnmarshaller.forContentTypes(ContentTypeRange(ContentTypes.`application/octet-stream`))

  private val route = withPermissions(new TestInfoResources(Map("streaming" -> testInfoProvider), processAuthorizer, processRepository), testPermissionAll)

  test("generates data"){
    val process = ProcessTestData.sampleDisplayableProcess
    saveProcess(process) {
      Post("/testInfo/generate/5", posting.toEntity(process)) ~> route ~> check {
        status shouldEqual StatusCodes.OK
        val entity = new String(entityAs[Array[Byte]])
        entity shouldBe s"terefereKuku-5"
      }
    }

  }

}
