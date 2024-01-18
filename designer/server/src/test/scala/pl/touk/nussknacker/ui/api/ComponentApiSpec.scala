package pl.touk.nussknacker.ui.api

import io.restassured.RestAssured.given
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import io.restassured.response.ValidatableResponse
import org.hamcrest.Matchers.equalTo
import org.scalatest.freespec.AnyFreeSpecLike
import org.scalatest.matchers.must.Matchers.contain
import pl.touk.nussknacker.engine.api.component.{ComponentId, ComponentInfo, ComponentType}
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.test.ProcessUtils.convertToAnyShouldWrapper
import pl.touk.nussknacker.test.{
  NuRestAssureExtensions,
  NuRestAssureMatchers,
  PatientScalaFutures,
  RestAssuredVerboseLogging
}
import pl.touk.nussknacker.ui.api.helpers.TestCategories.Category1
import pl.touk.nussknacker.ui.api.helpers.TestProcessingTypes.Streaming
import pl.touk.nussknacker.ui.api.helpers.{NuItTest, NuTestScenarioManager, WithMockableDeploymentManager}

class ComponentApiSpec
    extends AnyFreeSpecLike
    with NuItTest
    with WithMockableDeploymentManager
    with NuTestScenarioManager
    with NuRestAssureExtensions
    with NuRestAssureMatchers
    with RestAssuredVerboseLogging
    with PatientScalaFutures {

  "The endpoint for getting components when" - {
    "authenticated should" - {

      val correctListForTestUser: List[String] = List(
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

      "return component list for current user" in {
        val componentIdListForTestUser: List[String] =
          given()
            .basicAuth("allpermuser", "allpermuser")
            .when()
            .get(s"$nuDesignerHttpAddress/api/components")
            .Then()
            .statusCode(200)
            .extractToStringsList("id")

        componentIdListForTestUser.sorted should contain theSameElementsAs correctListForTestUser
      }

      "return different component lists for users(test, admin)" in {

        val correctListForAdminUser: List[String] = List(
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
          "streaming-source-sql-source",
          "streaming2-custom-additionalvariable",
          "streaming2-custom-constantstatetransformer",
          "streaming2-custom-constantstatetransformerlongvalue",
          "streaming2-custom-customfilter",
          "streaming2-custom-enrichwithadditionaldata",
          "streaming2-custom-hidevariables",
          "streaming2-custom-lastvariablewithfilter",
          "streaming2-custom-nonereturntypetransformer",
          "streaming2-custom-sendcommunication",
          "streaming2-custom-simpletypescustomnode",
          "streaming2-custom-stateful",
          "streaming2-custom-unionwitheditors",
          "streaming2-service-accountservice",
          "streaming2-service-campaignservice",
          "streaming2-service-clienthttpservice",
          "streaming2-service-collectiontypesservice",
          "streaming2-service-complexreturnobjectservice",
          "streaming2-service-componentservice",
          "streaming2-service-configuratorservice",
          "streaming2-service-customvalidatedservice",
          "streaming2-service-datestypesservice",
          "streaming2-service-dynamicmultipleparamsservice",
          "streaming2-service-dynamicservice",
          "streaming2-service-echoenumservice",
          "streaming2-service-enricher",
          "streaming2-service-enrichernullresult",
          "streaming2-service-listreturnobjectservice",
          "streaming2-service-log",
          "streaming2-service-meetingservice",
          "streaming2-service-modelconfigreader",
          "streaming2-service-multipleparamsservice",
          "streaming2-service-optionaltypesservice",
          "streaming2-service-paramservice",
          "streaming2-service-providedcomponent-component-v1",
          "streaming2-service-providedcomponent-component-v2",
          "streaming2-service-providedcomponent-component-v3",
          "streaming2-service-servicemodelservice",
          "streaming2-service-simpletypesservice",
          "streaming2-service-transactionservice",
          "streaming2-service-unionreturnobjectservice",
          "streaming2-sink-communicationsink",
          "streaming2-sink-dead-end-lite",
          "streaming2-sink-kafka-string",
          "streaming2-sink-monitor",
          "streaming2-sink-sendsms",
          "streaming2-source-boundedsource",
          "streaming2-source-classinstancesource",
          "streaming2-source-communicationsource",
          "streaming2-source-csv-source",
          "streaming2-source-csv-source-lite",
          "streaming2-source-genericsourcewithcustomvariables",
          "streaming2-source-kafka-transaction",
          "streaming2-source-onesource",
          "streaming2-source-real-kafka",
          "streaming2-source-real-kafka-json-sampleproduct",
          "streaming2-source-sql-source"
        )

        val componentIdListForTestUser: List[String] =
          given()
            .basicAuth("allpermuser", "allpermuser")
            .when()
            .get(s"$nuDesignerHttpAddress/api/components")
            .Then()
            .statusCode(200)
            .extractToStringsList("id")

        val componentIdListForAdminUser: List[String] =
          given()
            .basicAuth("admin", "admin")
            .when()
            .get(s"$nuDesignerHttpAddress/api/components")
            .Then()
            .statusCode(200)
            .extractToStringsList("id")

        componentIdListForAdminUser.sorted should contain theSameElementsAs correctListForAdminUser

        componentIdListForAdminUser.length > componentIdListForTestUser.length shouldBe true
      }
    }
    "not authenticated should" - {
      "forbid access" in {
        given()
          .noAuth()
          .get(s"$nuDesignerHttpAddress/api/components")
          .Then()
          .statusCode(401)
          .body(
            equalTo(
              "The resource requires authentication, which was not supplied with the request"
            )
          )
      }
    }
  }

  "The endpoint for getting component usages when" - {
    "authenticated should" - {
      "return component usages for existing component" in {
        val scenarioName        = "test"
        val sourceComponentName = "kafka" // it's real component name from DevProcessConfigCreator
        val scenario = ScenarioBuilder
          .streaming(scenarioName)
          .source("source", sourceComponentName)
          .emptySink("sink", "kafka")

        createSavedScenario(scenario, Category1, Streaming)
        val componentId = ComponentId.default(Streaming, ComponentInfo(ComponentType.Source, sourceComponentName))

        given()
          .basicAuth("admin", "admin")
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
                 |  "processCategory": "$Category1",
                 |  "modificationDate": "^\\\\d{4}-\\\\d{2}-\\\\d{2}T\\\\d{2}:\\\\d{2}:\\\\d{2}.\\\\d{6}Z$$",
                 |  "modifiedAt": "^\\\\d{4}-\\\\d{2}-\\\\d{2}T\\\\d{2}:\\\\d{2}:\\\\d{2}.\\\\d{6}Z$$",
                 |  "modifiedBy": "admin",
                 |  "createdAt": "^\\\\d{4}-\\\\d{2}-\\\\d{2}T\\\\d{2}:\\\\d{2}:\\\\d{2}.\\\\d{6}Z$$",
                 |  "createdBy": "admin",
                 |  "lastAction": null
                 |}]""".stripMargin
            )
          )
      }

      "return 404 when component not exist" in {
        val badComponent: ComponentId = ComponentId("not-exist-component")

        given()
          .pathParam("componentId", badComponent.value)
          .basicAuth("admin", "admin")
          .when()
          .get(s"$nuDesignerHttpAddress/api/components/{componentId}/usages")
          .Then()
          .statusCode(404)
          .body(equalTo(s"Component ${badComponent.value} not exist."))
      }
      "return 405 when invalid HTTP method is passed" in {
        given()
          .basicAuth("admin", "admin")
          .when()
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

    "not authenticated should" - {
      "forbid access" in {
        given()
          .noAuth()
          .when()
          .get(s"$nuDesignerHttpAddress/api/components/id/usages")
          .Then()
          .statusCode(401)
          .body(
            equalTo(
              "The resource requires authentication, which was not supplied with the request"
            )
          )
      }
    }
  }

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
