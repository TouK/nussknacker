package pl.touk.nussknacker.ui.api

import io.restassured.RestAssured.given
import org.scalatest.freespec.AnyFreeSpecLike
import pl.touk.nussknacker.engine.api.component.{ComponentId, ComponentType}
import pl.touk.nussknacker.test.{
  NuRestAssureExtensions,
  NuRestAssureMatchers,
  PatientScalaFutures,
  RestAssuredVerboseLogging
}
import pl.touk.nussknacker.ui.api.helpers.{
  NuItTest,
  NuScenarioConfigurationHelper,
  TestProcessingTypes,
  WithMockableDeploymentManager
}
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import io.restassured.response.ValidatableResponse
import org.hamcrest.Matchers.equalTo
import org.scalatest.matchers.must.Matchers.contain
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.definition.{ComponentIdProvider, DefaultComponentIdProvider}
import pl.touk.nussknacker.test.ProcessUtils.convertToAnyShouldWrapper
import pl.touk.nussknacker.ui.api.helpers.TestCategories.Category1

class ComponentResourcesApiSpec
    extends AnyFreeSpecLike
    with NuItTest
    with WithMockableDeploymentManager
    with NuScenarioConfigurationHelper
    with NuRestAssureExtensions
    with NuRestAssureMatchers
    with RestAssuredVerboseLogging
    with PatientScalaFutures {

  private val defaultComponentIdProvider: ComponentIdProvider = new DefaultComponentIdProvider(Map.empty)

  "The endpoint for getting components when" - {
    "authenticated should" - {

      val correctListForTestUser: List[String] = List(
        "filter",
        "mapvariable",
        "split",
        "streaming-customnode-additionalvariable",
        "streaming-customnode-constantstatetransformer",
        "streaming-customnode-constantstatetransformerlongvalue",
        "streaming-customnode-customfilter",
        "streaming-customnode-enrichwithadditionaldata",
        "streaming-customnode-hidevariables",
        "streaming-customnode-lastvariablewithfilter",
        "streaming-customnode-nonereturntypetransformer",
        "streaming-customnode-sendcommunication",
        "streaming-customnode-simpletypescustomnode",
        "streaming-customnode-stateful",
        "streaming-customnode-unionwitheditors",
        "streaming-enricher-clienthttpservice",
        "streaming-enricher-complexreturnobjectservice",
        "streaming-enricher-customvalidatedservice",
        "streaming-enricher-echoenumservice",
        "streaming-enricher-enricher",
        "streaming-enricher-enrichernullresult",
        "streaming-enricher-listreturnobjectservice",
        "streaming-enricher-modelconfigreader",
        "streaming-enricher-paramservice",
        "streaming-enricher-unionreturnobjectservice",
        "streaming-processor-accountservice",
        "streaming-processor-collectiontypesservice",
        "streaming-processor-componentservice",
        "streaming-processor-configuratorservice",
        "streaming-processor-datestypesservice",
        "streaming-processor-dynamicmultipleparamsservice",
        "streaming-processor-dynamicservice",
        "streaming-processor-log",
        "streaming-processor-meetingservice",
        "streaming-processor-multipleparamsservice",
        "streaming-processor-optionaltypesservice",
        "streaming-processor-providedcomponent-component-v1",
        "streaming-processor-providedcomponent-component-v2",
        "streaming-processor-providedcomponent-component-v3",
        "streaming-processor-servicemodelservice",
        "streaming-processor-simpletypesservice",
        "streaming-processor-transactionservice",
        "streaming-sink-communicationsink",
        "streaming-sink-dead-end-lite",
        "streaming-sink-kafka",
        "streaming-sink-kafka-avro",
        "streaming-sink-kafka-string",
        "streaming-sink-monitor",
        "streaming-sink-sendsms",
        "streaming-source-boundedsource",
        "streaming-source-classinstancesource",
        "streaming-source-communicationsource",
        "streaming-source-csv-source",
        "streaming-source-csv-source-lite",
        "streaming-source-genericsourcewithcustomvariables",
        "streaming-source-kafka",
        "streaming-source-kafka-transaction",
        "streaming-source-onesource",
        "streaming-source-real-kafka",
        "streaming-source-real-kafka-avro",
        "streaming-source-real-kafka-json-sampleproduct",
        "streaming-source-sql-source",
        "switch",
        "variable"
      )

      "return component list for current user" in {
        val componentIdListForTestUser: List[String] =
          given()
            .auth()
            .basic("allpermuser", "allpermuser")
            .when()
            .get(s"$nuDesignerHttpAddress/api/components")
            .Then()
            .statusCode(200)
            .extractToStringsList("id")

        componentIdListForTestUser.sorted should contain theSameElementsAs correctListForTestUser
      }

      "return different component lists for users(test, admin)" in {

        val correctListForAdminUser: List[String] = List.apply(
          "filter",
          "mapvariable",
          "split",
          "streaming2-customnode-additionalvariable",
          "streaming2-customnode-constantstatetransformer",
          "streaming2-customnode-constantstatetransformerlongvalue",
          "streaming2-customnode-customfilter",
          "streaming2-customnode-enrichwithadditionaldata",
          "streaming2-customnode-hidevariables",
          "streaming2-customnode-lastvariablewithfilter",
          "streaming2-customnode-nonereturntypetransformer",
          "streaming2-customnode-sendcommunication",
          "streaming2-customnode-simpletypescustomnode",
          "streaming2-customnode-stateful",
          "streaming2-customnode-unionwitheditors",
          "streaming2-enricher-clienthttpservice",
          "streaming2-enricher-complexreturnobjectservice",
          "streaming2-enricher-customvalidatedservice",
          "streaming2-enricher-echoenumservice",
          "streaming2-enricher-enricher",
          "streaming2-enricher-enrichernullresult",
          "streaming2-enricher-listreturnobjectservice",
          "streaming2-enricher-modelconfigreader",
          "streaming2-enricher-paramservice",
          "streaming2-enricher-unionreturnobjectservice",
          "streaming2-processor-accountservice",
          "streaming2-processor-campaignservice",
          "streaming2-processor-collectiontypesservice",
          "streaming2-processor-componentservice",
          "streaming2-processor-configuratorservice",
          "streaming2-processor-datestypesservice",
          "streaming2-processor-dynamicmultipleparamsservice",
          "streaming2-processor-dynamicservice",
          "streaming2-processor-log",
          "streaming2-processor-meetingservice",
          "streaming2-processor-multipleparamsservice",
          "streaming2-processor-optionaltypesservice",
          "streaming2-processor-providedcomponent-component-v1",
          "streaming2-processor-providedcomponent-component-v2",
          "streaming2-processor-providedcomponent-component-v3",
          "streaming2-processor-servicemodelservice",
          "streaming2-processor-simpletypesservice",
          "streaming2-processor-transactionservice",
          "streaming2-sink-communicationsink",
          "streaming2-sink-dead-end-lite",
          "streaming2-sink-kafka",
          "streaming2-sink-kafka-avro",
          "streaming2-sink-kafka-string",
          "streaming2-sink-monitor",
          "streaming2-sink-sendsms",
          "streaming2-source-boundedsource",
          "streaming2-source-classinstancesource",
          "streaming2-source-communicationsource",
          "streaming2-source-csv-source",
          "streaming2-source-csv-source-lite",
          "streaming2-source-genericsourcewithcustomvariables",
          "streaming2-source-kafka",
          "streaming2-source-kafka-transaction",
          "streaming2-source-onesource",
          "streaming2-source-real-kafka",
          "streaming2-source-real-kafka-avro",
          "streaming2-source-real-kafka-json-sampleproduct",
          "streaming2-source-sql-source",
          "streaming-customnode-additionalvariable",
          "streaming-customnode-constantstatetransformer",
          "streaming-customnode-constantstatetransformerlongvalue",
          "streaming-customnode-customfilter",
          "streaming-customnode-enrichwithadditionaldata",
          "streaming-customnode-hidevariables",
          "streaming-customnode-lastvariablewithfilter",
          "streaming-customnode-nonereturntypetransformer",
          "streaming-customnode-sendcommunication",
          "streaming-customnode-simpletypescustomnode",
          "streaming-customnode-stateful",
          "streaming-customnode-unionwitheditors",
          "streaming-enricher-clienthttpservice",
          "streaming-enricher-complexreturnobjectservice",
          "streaming-enricher-customvalidatedservice",
          "streaming-enricher-echoenumservice",
          "streaming-enricher-enricher",
          "streaming-enricher-enrichernullresult",
          "streaming-enricher-listreturnobjectservice",
          "streaming-enricher-modelconfigreader",
          "streaming-enricher-paramservice",
          "streaming-enricher-unionreturnobjectservice",
          "streaming-processor-accountservice",
          "streaming-processor-collectiontypesservice",
          "streaming-processor-componentservice",
          "streaming-processor-configuratorservice",
          "streaming-processor-datestypesservice",
          "streaming-processor-dynamicmultipleparamsservice",
          "streaming-processor-dynamicservice",
          "streaming-processor-log",
          "streaming-processor-meetingservice",
          "streaming-processor-multipleparamsservice",
          "streaming-processor-optionaltypesservice",
          "streaming-processor-providedcomponent-component-v1",
          "streaming-processor-providedcomponent-component-v2",
          "streaming-processor-providedcomponent-component-v3",
          "streaming-processor-servicemodelservice",
          "streaming-processor-simpletypesservice",
          "streaming-processor-transactionservice",
          "streaming-sink-communicationsink",
          "streaming-sink-dead-end-lite",
          "streaming-sink-kafka",
          "streaming-sink-kafka-avro",
          "streaming-sink-kafka-string",
          "streaming-sink-monitor",
          "streaming-sink-sendsms",
          "streaming-source-boundedsource",
          "streaming-source-classinstancesource",
          "streaming-source-communicationsource",
          "streaming-source-csv-source",
          "streaming-source-csv-source-lite",
          "streaming-source-genericsourcewithcustomvariables",
          "streaming-source-kafka",
          "streaming-source-kafka-transaction",
          "streaming-source-onesource",
          "streaming-source-real-kafka",
          "streaming-source-real-kafka-avro",
          "streaming-source-real-kafka-json-sampleproduct",
          "streaming-source-sql-source",
          "switch",
          "variable"
        )

        val componentIdListForTestUser: List[String] =
          given()
            .auth()
            .basic("allpermuser", "allpermuser")
            .when()
            .get(s"$nuDesignerHttpAddress/api/components")
            .Then()
            .statusCode(200)
            .extractToStringsList("id")

        val componentIdListForAdminUser: List[String] =
          given()
            .auth()
            .basic("admin", "admin")
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
          .auth()
          .none()
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
        val processName         = ProcessName("test")
        val sourceComponentName = "kafka" // it's real component name from DevProcessConfigCreator
        val process = ScenarioBuilder
          .streaming(processName.value)
          .source("source", sourceComponentName)
          .emptySink("sink", "kafka")

        val processId = createSavedProcess(process, Category1, TestProcessingTypes.Streaming)
        val componentId = defaultComponentIdProvider.createComponentId(
          TestProcessingTypes.Streaming,
          Some(sourceComponentName),
          ComponentType.Source
        )

        given()
          .auth()
          .basic("admin", "admin")
          .and()
          .pathParam("componentId", componentId.value)
          .when()
          .get(s"$nuDesignerHttpAddress/api/components/{componentId}/usages")
          .Then()
          .statusCode(200)
          .body(
            matchJsonWithRegexValues(
              s"""[{
                 |  "id": "${processName.value}",
                 |  "name": "${processName.value}",
                 |  "processId": ${processId.value},
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
          .and()
          .auth()
          .basic("admin", "admin")
          .when()
          .get(s"$nuDesignerHttpAddress/api/components/{componentId}/usages")
          .Then()
          .statusCode(404)
          .body(equalTo(s"Component ${badComponent.value} not exist."))
      }
      "return 405 when invalid HTTP method is passed" in {
        given()
          .auth()
          .basic("admin", "admin")
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
          .auth()
          .none()
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
