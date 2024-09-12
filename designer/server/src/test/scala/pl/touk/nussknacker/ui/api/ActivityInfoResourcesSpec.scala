package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Json
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.test.base.it.NuResourcesTest
import pl.touk.nussknacker.test.config.WithSimplifiedDesignerConfig.TestProcessingType.Streaming
import pl.touk.nussknacker.test.utils.domain.ProcessTestData
import pl.touk.nussknacker.test.utils.domain.TestFactory.{mapProcessingTypeDataProvider, withPermissions}
import pl.touk.nussknacker.test.{EitherValuesDetailedMessage, PatientScalaFutures}
import pl.touk.nussknacker.test.utils.scalas.AkkaHttpExtensions.toRequestEntity

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
