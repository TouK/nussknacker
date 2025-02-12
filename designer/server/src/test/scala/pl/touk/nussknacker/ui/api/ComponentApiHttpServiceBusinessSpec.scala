package pl.touk.nussknacker.ui.api

import io.restassured.RestAssured.given
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import io.restassured.response.ValidatableResponse
import org.hamcrest.Matchers.equalTo
import org.scalatest.freespec.AnyFreeSpecLike
import org.scalatest.matchers.must.Matchers.contain
import pl.touk.nussknacker.engine.api.component.{ComponentId, ComponentType, DesignerWideComponentId}
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.test.ProcessUtils.convertToAnyShouldWrapper
import pl.touk.nussknacker.test.{
  NuRestAssureExtensions,
  NuRestAssureMatchers,
  PatientScalaFutures,
  RestAssuredVerboseLoggingIfValidationFails
}
import pl.touk.nussknacker.test.base.it.{NuItTest, WithSimplifiedConfigScenarioHelper}
import pl.touk.nussknacker.test.config.WithSimplifiedDesignerConfig.TestCategory.Category1
import pl.touk.nussknacker.test.config.WithSimplifiedDesignerConfig.TestProcessingType.Streaming
import pl.touk.nussknacker.test.config.{
  WithBusinessCaseRestAssuredUsersExtensions,
  WithMockableDeploymentManager,
  WithSimplifiedDesignerConfig
}

class ComponentApiHttpServiceBusinessSpec
    extends AnyFreeSpecLike
    with NuItTest
    with WithSimplifiedDesignerConfig
    with WithSimplifiedConfigScenarioHelper
    with WithMockableDeploymentManager
    with WithBusinessCaseRestAssuredUsersExtensions
    with NuRestAssureExtensions
    with NuRestAssureMatchers
    with RestAssuredVerboseLoggingIfValidationFails
    with PatientScalaFutures {

  "The endpoint for getting components when" - {
    "return component list for current user" in {
      def getComponentListIds(skipUsages: Boolean, skipFragments: Boolean) = {
        given()
          .queryParam("skipUsages", skipUsages.toString)
          .queryParam("skipFragments", skipFragments.toString)
          .when()
          .basicAuthAllPermUser()
          .get(s"$nuDesignerHttpAddress/api/components")
          .Then()
          .statusCode(200)
          .extractToStringsList("id")
      }

      val componentIdListForTestUser              = getComponentListIds(skipUsages = false, skipFragments = false)
      val componentIdListForTestUserSkippedUsages = getComponentListIds(skipUsages = true, skipFragments = false)

      componentIdListForTestUser shouldBe componentIdListForTestUserSkippedUsages
      componentIdListForTestUser.sorted should contain theSameElementsAs expectedComponentIdsForAllPermUser
    }
  }

  "The endpoint for getting component usages when" - {
    "return component usages for existing component" in {
      val scenarioName        = "test"
      val sourceComponentName = "kafka" // it's real component name from DevProcessConfigCreator
      val scenario = ScenarioBuilder
        .streaming(scenarioName)
        .source("source", sourceComponentName)
        .emptySink("sink", "kafka")

      val componentId = DesignerWideComponentId.default(
        processingType = Streaming.stringify,
        componentId = ComponentId(ComponentType.Source, sourceComponentName)
      )

      given()
        .applicationState {
          createSavedScenario(scenario)
        }
        .basicAuthAllPermUser()
        .pathParam("componentId", componentId.value)
        .when()
        .get(s"$nuDesignerHttpAddress/api/components/{componentId}/usages")
        .Then()
        .statusCode(200)
        .body(
          matchJsonWithRegexValues(
            s"""[{
               |  "name": "$scenarioName",
               |  "nodesUsagesData": [ { "nodeId": "source", "type": "ScenarioUsageData" } ],
               |  "isFragment": false,
               |  "processCategory": "${Category1.stringify}",
               |  "modificationDate": "${regexes.zuluDateRegex}",
               |  "modifiedAt": "${regexes.zuluDateRegex}",
               |  "modifiedBy": "admin",
               |  "createdAt": "${regexes.zuluDateRegex}",
               |  "createdBy": "admin",
               |  "lastAction": null
               |}]""".stripMargin
          )
        )
    }
    "return 404 when component does not exist" in {
      val badComponentId: DesignerWideComponentId = DesignerWideComponentId("not-exist-component")

      given()
        .when()
        .basicAuthAdmin()
        .pathParam("componentId", badComponentId.value)
        .get(s"$nuDesignerHttpAddress/api/components/{componentId}/usages")
        .Then()
        .statusCode(404)
        .body(equalTo(s"Component ${badComponentId.value} not exist."))
    }
    "return 405 when invalid HTTP method is passed" in {
      given()
        .when()
        .basicAuthAdmin()
        .put(s"$nuDesignerHttpAddress/api/components/id/usages")
        .Then()
        .statusCode(405)
        .body(
          equalTo(
            s"Method Not Allowed"
          )
        )
    }
  }

  private lazy val expectedComponentIdsForAllPermUser: List[String] = List(
    "builtin-choice",
    "builtin-filter",
    "builtin-record-variable",
    "builtin-split",
    "builtin-variable",
    "streaming-custom-additionalvariable",
    "streaming-custom-constantstatetransformer",
    "streaming-custom-constantstatetransformerlongvalue",
    "streaming-custom-customfilter",
    "streaming-custom-enrichwithadditionaldata",
    "streaming-custom-hidevariables",
    "streaming-custom-lastvariablewithfilter",
    "streaming-custom-nonereturntypetransformer",
    "streaming-custom-sendcommunication",
    "streaming-custom-simpletypescustomnode",
    "streaming-custom-stateful",
    "streaming-custom-unionwitheditors",
    "streaming-service-accountservice",
    "streaming-service-clienthttpservice",
    "streaming-service-collectiontypesservice",
    "streaming-service-complexreturnobjectservice",
    "streaming-service-componentservice",
    "streaming-service-configuratorservice",
    "streaming-service-customvalidatedservice",
    "streaming-service-datestypesservice",
    "streaming-service-dynamicmultipleparamsservice",
    "streaming-service-dynamicservice",
    "streaming-service-echoenumservice",
    "streaming-service-enricher",
    "streaming-service-enrichernullresult",
    "streaming-service-listreturnobjectservice",
    "streaming-service-log",
    "streaming-service-meetingservice",
    "streaming-service-modelconfigreader",
    "streaming-service-multipleparamsservice",
    "streaming-service-optionaltypesservice",
    "streaming-service-paramservice",
    "streaming-service-providedcomponent-component-v1",
    "streaming-service-providedcomponent-component-v2",
    "streaming-service-providedcomponent-component-v3",
    "streaming-service-servicemodelservice",
    "streaming-service-servicewithdictparametereditor",
    "streaming-service-simpletypesservice",
    "streaming-service-transactionservice",
    "streaming-service-unionreturnobjectservice",
    "streaming-sink-communicationsink",
    "streaming-sink-dead-end-lite",
    "streaming-sink-kafka-string",
    "streaming-sink-monitor",
    "streaming-sink-sendsms",
    "streaming-source-boundedsource",
    "streaming-source-classinstancesource",
    "streaming-source-communicationsource",
    "streaming-source-csv-source",
    "streaming-source-csv-source-lite",
    "streaming-source-genericsourcewithcustomvariables",
    "streaming-source-kafka-transaction",
    "streaming-source-onesource",
    "streaming-source-real-kafka",
    "streaming-source-real-kafka-json-sampleproduct",
    "streaming-source-sql-source"
  )

  implicit class ExtractRootKey[T <: ValidatableResponse](validatableResponse: T) {

    def extractToStringsList(key: String): List[String] = {
      validatableResponse
        .extract()
        .body()
        .jsonPath()
        .getList(key)
        .toArray()
        .toList
        .asInstanceOf[List[String]]
    }

  }

}
