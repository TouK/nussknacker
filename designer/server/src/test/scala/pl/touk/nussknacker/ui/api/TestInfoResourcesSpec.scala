package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model.{ContentTypeRange, ContentTypes, StatusCodes}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}
import cats.data.NonEmptyList
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Json
import org.apache.commons.lang3.StringUtils
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.api.test.ScenarioTestData
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.definition.test.{
  PreliminaryScenarioTestData,
  PreliminaryScenarioTestRecord,
  TestInfoProvider,
  TestingCapabilities
}
import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.test.{EitherValuesDetailedMessage, PatientScalaFutures}
import pl.touk.nussknacker.test.utils.domain.TestFactory.{mapProcessingTypeDataProvider, withPermissions}
import pl.touk.nussknacker.test.base.it.NuResourcesTest
import pl.touk.nussknacker.test.config.WithSimplifiedDesignerConfig.TestProcessingType.Streaming
import pl.touk.nussknacker.test.utils.domain.ProcessTestData
import pl.touk.nussknacker.test.utils.scalas.AkkaHttpExtensions.toRequestEntity

class TestInfoResourcesSpec
    extends AnyFunSuite
    with ScalatestRouteTest
    with Matchers
    with FailFastCirceSupport
    with NuResourcesTest
    with PatientScalaFutures
    with EitherValuesDetailedMessage {

  private val scenarioGraph: ScenarioGraph = ProcessTestData.sampleScenarioGraph
  private val testPermissionAll            = List(Permission.Deploy, Permission.Read, Permission.Write)

  private def testInfoProvider(additionalDataSize: Int) = new TestInfoProvider {

    override def getTestingCapabilities(scenario: CanonicalProcess): TestingCapabilities =
      TestingCapabilities(canBeTested = true, canGenerateTestData = true, canTestWithForm = false)

    override def getTestParameters(scenario: CanonicalProcess): Map[String, List[Parameter]] = ???

    override def generateTestData(scenario: CanonicalProcess, size: Int): Either[String, PreliminaryScenarioTestData] =
      Right(
        PreliminaryScenarioTestData(
          NonEmptyList(
            PreliminaryScenarioTestRecord.Standard(
              "sourceId",
              Json.fromString(s"terefereKuku-$size${StringUtils.repeat("0", additionalDataSize)}")
            ),
            Nil
          )
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
    mapProcessingTypeDataProvider(
      Streaming.stringify -> createScenarioTestService(testInfoProvider(additionalDataSize))
    )
  )

  test("generates data") {
    saveProcess(scenarioGraph) {
      Post(
        s"/testInfo/${ProcessTestData.sampleProcessName}/generate/5",
        scenarioGraph.toJsonRequestEntity()
      ) ~> withPermissions(
        route(),
        testPermissionAll: _*
      ) ~> check {
        implicit val contentUnmarshaller: FromEntityUnmarshaller[String] = Unmarshaller.stringUnmarshaller
        status shouldEqual StatusCodes.OK
        val content = responseAs[String]
        content shouldBe """{"sourceId":"sourceId","record":"terefereKuku-5"}"""
      }
    }
  }

  test("refuses to generate too much data") {
    saveProcess(scenarioGraph) {
      Post(
        s"/testInfo/${ProcessTestData.sampleProcessName}/generate/100",
        scenarioGraph.toJsonRequestEntity()
      ) ~> withPermissions(
        route(),
        testPermissionAll: _*
      ) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
      Post(
        s"/testInfo/${ProcessTestData.sampleProcessName}/generate/1",
        scenarioGraph.toJsonRequestEntity()
      ) ~> withPermissions(
        route(additionalDataSize = 20000),
        testPermissionAll: _*
      ) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
    }
  }

  test("get full capabilities when user has deploy role") {
    saveProcess(scenarioGraph) {
      Post(
        s"/testInfo/${ProcessTestData.sampleProcessName}/capabilities",
        scenarioGraph.toJsonRequestEntity()
      ) ~> withPermissions(
        route(),
        testPermissionAll: _*
      ) ~> check {
        status shouldEqual StatusCodes.OK
        val entity = entityAs[Json]
        entity.hcursor.downField("canBeTested").as[Boolean].rightValue shouldBe true
        entity.hcursor.downField("canGenerateTestData").as[Boolean].rightValue shouldBe true
      }
    }
  }

  test("get empty capabilities when user hasn't got deploy role") {
    saveProcess(scenarioGraph) {
      Post(
        s"/testInfo/${ProcessTestData.sampleProcessName}/capabilities",
        scenarioGraph.toJsonRequestEntity()
      ) ~> withPermissions(
        route(),
        Permission.Read
      ) ~> check {
        status shouldEqual StatusCodes.OK
        val entity = entityAs[Json]
        entity.hcursor.downField("canBeTested").as[Boolean].rightValue shouldBe false
        entity.hcursor.downField("canGenerateTestData").as[Boolean].rightValue shouldBe false
      }
    }
  }

}

class ActivityInfoResourcesSpec
    extends AnyFunSuite
    with ScalatestRouteTest
    with Matchers
    with FailFastCirceSupport
    with NuResourcesTest
    with PatientScalaFutures
    with EitherValuesDetailedMessage {

  private val scenarioGraph: ScenarioGraph = ProcessTestData.sampleScenarioGraph
  private val testPermissionAll            = List(Permission.Deploy, Permission.Read, Permission.Write)

  private def route() = new ActivityInfoResources(
    processService,
    mapProcessingTypeDataProvider(
      Streaming.stringify -> createScenarioActivityService
    )
  )

  test("get activity parameters") {
    saveProcess(scenarioGraph) {
      Post(
        s"/activityInfo/${ProcessTestData.sampleProcessName}/activityParameters",
        scenarioGraph.toJsonRequestEntity()
      ) ~> withPermissions(
        route(),
        testPermissionAll: _*
      ) ~> check {
        status shouldEqual StatusCodes.OK
        val content = entityAs[Json].noSpaces
        content shouldBe """{}"""
      }
    }
  }

}
