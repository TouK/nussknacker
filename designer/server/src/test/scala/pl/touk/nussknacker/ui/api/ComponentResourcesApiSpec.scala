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
import org.hamcrest.Matchers.equalTo
import org.scalatest.matchers.must.Matchers.contain
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.definition.{ComponentIdProvider, DefaultComponentIdProvider}
import pl.touk.nussknacker.test.ProcessUtils.convertToAnyShouldWrapper
import pl.touk.nussknacker.ui.api.helpers.TestCategories.Category1

import scala.jdk.CollectionConverters.CollectionHasAsScala

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

  "The components endpoint should" - {
    "return users(test, admin) components list" in {

      val adminTrueList: List[String] = List.apply(
        "streaming-processor-accountservice",
        "streaming-customnode-additionalvariable",
        "streaming-source-boundedsource",
        "switch",
        "streaming-source-classinstancesource",
        "streaming-enricher-clienthttpservice",
        "streaming-customnode-collect",
        "streaming-processor-collectiontypesservice",
        "streaming-sink-communicationsink",
        "streaming-source-communicationsource",
        "streaming-enricher-complexreturnobjectservice",
        "streaming-processor-componentservice",
        "streaming-customnode-constantstatetransformer",
        "streaming-customnode-constantstatetransformerlongvalue",
        "streaming-source-csv-source",
        "streaming-source-csv-source-lite",
        "streaming-customnode-customfilter",
        "streaming-enricher-customvalidatedservice",
        "streaming-processor-datestypesservice",
        "streaming-sink-dead-end",
        "streaming-sink-dead-end-lite",
        "streaming-processor-dynamicmultipleparamsservice",
        "streaming-processor-dynamicservice",
        "streaming-enricher-echoenumservice",
        "streaming-customnode-enrichwithadditionaldata",
        "streaming-enricher-enricher",
        "streaming-enricher-enrichernullresult",
        "streaming-enricher-env",
        "filter",
        "streaming-customnode-for-each",
        "streaming-source-genericsourcewithcustomvariables",
        "streaming-customnode-hidevariables",
        "streaming-sink-kafka",
        "streaming-source-kafka",
        "streaming-sink-kafka-avro",
        "streaming-sink-kafka-string",
        "streaming-source-kafka-transaction",
        "streaming-customnode-lastvariablewithfilter",
        "streaming-enricher-listreturnobjectservice",
        "streaming-processor-log",
        "mapvariable",
        "streaming-enricher-modelconfigreader",
        "streaming-sink-monitor",
        "streaming-processor-multipleparamsservice",
        "streaming-customnode-nonereturntypetransformer",
        "streaming-source-onesource",
        "streaming-processor-optionaltypesservice",
        "streaming-enricher-paramservice",
        "streaming-source-real-kafka",
        "streaming-source-real-kafka-avro",
        "streaming-source-real-kafka-json-sampleproduct",
        "streaming-source-request",
        "streaming-sink-response",
        "streaming-customnode-sendcommunication",
        "streaming-sink-sendsms",
        "streaming-processor-servicemodelservice",
        "streaming-customnode-simpletypescustomnode",
        "streaming-processor-simpletypesservice",
        "split",
        "streaming-source-sql-source",
        "streaming-customnode-stateful",
        "streaming-processor-transactionservice",
        "streaming-customnode-union",
        "streaming-enricher-unionreturnobjectservice",
        "streaming-customnode-unionwitheditors",
        "variable"
      )
      val testTrueList: List[String] = List.apply(
        "streaming-source-boundedsource",
        "switch",
        "streaming-source-classinstancesource",
        "streaming-customnode-collect",
        "streaming-sink-dead-end",
        "streaming-customnode-enrichwithadditionaldata",
        "streaming-enricher-env",
        "filter",
        "streaming-customnode-for-each",
        "streaming-customnode-hidevariables",
        "streaming-sink-kafka",
        "streaming-source-kafka",
        "streaming-sink-kafka-avro",
        "streaming-sink-kafka-string",
        "streaming-source-kafka-transaction",
        "streaming-customnode-lastvariablewithfilter",
        "streaming-processor-log",
        "mapvariable",
        "streaming-customnode-nonereturntypetransformer",
        "streaming-source-real-kafka",
        "streaming-source-real-kafka-avro",
        "streaming-source-real-kafka-json-sampleproduct",
        "streaming-source-request",
        "streaming-sink-response",
        "streaming-customnode-sendcommunication",
        "streaming-sink-sendsms",
        "split",
        "streaming-customnode-union",
        "streaming-customnode-unionwitheditors",
        "variable"
      )

      val responseForTest: List[String] =
        given()
          .auth()
          .basic("allpermuser", "allpermuser")
          .when()
          .get(s"$nuDesignerHttpAddress/api/components")
          .Then()
          .statusCode(200)
          .extract()
          .body()
          .jsonPath()
          .getList("id")
          .asScala
          .toList

      val responseForAdmin: List[String] =
        given()
          .auth()
          .basic("admin", "admin")
          .when()
          .get(s"$nuDesignerHttpAddress/api/components")
          .Then()
          .statusCode(200)
          .extract()
          .body()
          .jsonPath
          .getList("id")
          .asScala
          .toList

      responseForAdmin should contain theSameElementsAs adminTrueList
      responseForTest should contain theSameElementsAs testTrueList
      responseForAdmin.length > responseForTest.length shouldBe true
    }

    "return component usages" in {
      val processName         = ProcessName("test")
      val sourceComponentName = "kafka" // it's real component name from DevProcessConfigCreator
      val process = ScenarioBuilder
        .streaming(processName.value)
        .source("source", sourceComponentName)
        .emptySink("sink", "kafka")

      val processId = createProcessS(process, Category1, TestProcessingTypes.Streaming)
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

    "return 405 when not get request" in {
      given()
        .auth()
        .basic("admin", "admin")
        .when()
        .put(s"$nuDesignerHttpAddress/api/components")
        .Then()
        .statusCode(405)
        .body(
          equalTo(
            s"HTTP method not allowed, supported methods: GET"
          )
        )
    }

    "return 401 when not authenticated" in {
      given()
        .auth()
        .none()
        .when()
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
