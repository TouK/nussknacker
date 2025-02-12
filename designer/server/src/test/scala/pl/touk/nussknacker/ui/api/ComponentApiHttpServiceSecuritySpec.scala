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
import pl.touk.nussknacker.test.base.it.{NuItTest, WithAccessControlCheckingConfigScenarioHelper}
import pl.touk.nussknacker.test.config.WithAccessControlCheckingDesignerConfig.TestCategory
import pl.touk.nussknacker.test.config.WithAccessControlCheckingDesignerConfig.TestCategory.{Category1, Category2}
import pl.touk.nussknacker.test.config.{
  WithAccessControlCheckingConfigRestAssuredUsersExtensions,
  WithAccessControlCheckingDesignerConfig
}
import pl.touk.nussknacker.test.{
  NuRestAssureExtensions,
  NuRestAssureMatchers,
  PatientScalaFutures,
  RestAssuredVerboseLoggingIfValidationFails
}

class ComponentApiHttpServiceSecuritySpec
    extends AnyFreeSpecLike
    with NuItTest
    with WithAccessControlCheckingDesignerConfig
    with WithAccessControlCheckingConfigScenarioHelper
    with WithAccessControlCheckingConfigRestAssuredUsersExtensions
    with NuRestAssureExtensions
    with NuRestAssureMatchers
    with RestAssuredVerboseLoggingIfValidationFails
    with PatientScalaFutures {

  "The endpoint for getting components when" - {
    "authenticated should" - {
      "return component list for current user" in {
        val componentIds: List[String] =
          given()
            .when()
            .basicAuthLimitedReader()
            .get(s"$nuDesignerHttpAddress/api/components")
            .Then()
            .statusCode(200)
            .extractToStringsList("id")

        componentIds.sorted should contain theSameElementsAs expectedComponentIdsForLimitedUser
      }
      "return different component lists for admin & limitedreader users" in {
        val componentIdsForAllPermUser: List[String] =
          given()
            .when()
            .basicAuthLimitedReader()
            .get(s"$nuDesignerHttpAddress/api/components")
            .Then()
            .statusCode(200)
            .extractToStringsList("id")

        val componentIdListForAdminUser: List[String] =
          given()
            .when()
            .basicAuthAdmin()
            .get(s"$nuDesignerHttpAddress/api/components")
            .Then()
            .statusCode(200)
            .extractToStringsList("id")

        componentIdListForAdminUser.sorted should contain theSameElementsAs expectedComponentIdsForAdminUser

        componentIdListForAdminUser.length > componentIdsForAllPermUser.length shouldBe true
      }
    }
    "not authenticated should" - {
      "forbid access" in {
        given()
          .when()
          .basicAuthUnknownUser()
          .get(s"$nuDesignerHttpAddress/api/components")
          .Then()
          .statusCode(401)
          .body(equalTo("The supplied authentication is invalid"))
      }
    }
    "no credentials were passed should" - {
      "authenticate as anonymous and return components list related to anonymous role category" in {
        val componentIds: List[String] =
          given()
            .when()
            .noAuth()
            .get(s"$nuDesignerHttpAddress/api/components")
            .Then()
            .statusCode(200)
            .extractToStringsList("id")

        componentIds.sorted should contain theSameElementsAs expectedComponentIdsForLimitedAnonymousUser
      }
    }
  }

  "The endpoint for getting component usages when" - {
    "authenticated should" - {
      "return component usages when component is used in the allowed category for the given user" in {
        val (componentId1, scenario1, category1) = createScenarioWithKafkaSource("s1", Category1)
        val (_, scenario2, category2)            = createScenarioWithKafkaSource("s2", Category2)

        given()
          .applicationState {
            createSavedScenario(scenario1, category1)
            createSavedScenario(scenario2, category2)
          }
          .when()
          .basicAuthLimitedReader()
          .pathParam("componentId", componentId1.value)
          .get(s"$nuDesignerHttpAddress/api/components/{componentId}/usages")
          .Then()
          .statusCode(200)
          .body(
            matchJsonWithRegexValues(
              s"""[{
                 |  "name": "${scenario1.name}",
                 |  "nodesUsagesData": [ { "nodeId": "source", "type": "ScenarioUsageData" } ],
                 |  "isFragment": false,
                 |  "processCategory": "${category1.stringify}",
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
      "return 404 when component is NOT used in the allowed category for the given user" in {
        val (componentId2, scenario2, category2) = createScenarioWithKafkaSource("s2", Category2)

        given()
          .applicationState {
            createSavedScenario(scenario2, category2)
          }
          .when()
          .basicAuthLimitedReader()
          .pathParam("componentId", componentId2.value)
          .get(s"$nuDesignerHttpAddress/api/components/{componentId}/usages")
          .Then()
          .statusCode(404)
          .body(equalTo("Component streaming2-source-kafka not exist."))
      }
    }
    "not authenticated should" - {
      "forbid access" in {
        given()
          .when()
          .basicAuthUnknownUser()
          .get(s"$nuDesignerHttpAddress/api/components/id/usages")
          .Then()
          .statusCode(401)
          .body(equalTo("The supplied authentication is invalid"))
      }
    }
    "no credentials were passed should" - {
      "authenticate as anonymous and return components usages list related to anonymous role category" in {
        val (_, scenario1, category1)            = createScenarioWithKafkaSource("s1", Category1)
        val (componentId2, scenario2, category2) = createScenarioWithKafkaSource("s2", Category2)

        given()
          .applicationState {
            createSavedScenario(scenario1, category1)
            createSavedScenario(scenario2, category2)
          }
          .when()
          .noAuth()
          .pathParam("componentId", componentId2.value)
          .get(s"$nuDesignerHttpAddress/api/components/{componentId}/usages")
          .Then()
          .statusCode(200)
          .body(
            matchJsonWithRegexValues(
              s"""[{
                 |  "name": "${scenario2.name}",
                 |  "nodesUsagesData": [ { "nodeId": "source", "type": "ScenarioUsageData" } ],
                 |  "isFragment": false,
                 |  "processCategory": "${category2.stringify}",
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
    }
  }

  private def createScenarioWithKafkaSource(scenarioName: String, category: TestCategory) = {
    val sourceComponentName = "kafka" // it's real component name from DevProcessConfigCreator
    val scenario = ScenarioBuilder
      .streaming(scenarioName)
      .source("source", sourceComponentName)
      .emptySink("sink", "kafka")

    val componentId = DesignerWideComponentId.default(
      processingType = TestCategory.processingTypeBy(category).stringify,
      componentId = ComponentId(ComponentType.Source, sourceComponentName)
    )
    (componentId, scenario, category)
  }

  private lazy val expectedComponentIdsForLimitedUser: List[String] = List(
    "builtin-choice",
    "builtin-filter",
    "builtin-record-variable",
    "builtin-split",
    "builtin-variable",
    "streaming1-custom-additionalvariable",
    "streaming1-custom-constantstatetransformer",
    "streaming1-custom-constantstatetransformerlongvalue",
    "streaming1-custom-customfilter",
    "streaming1-custom-enrichwithadditionaldata",
    "streaming1-custom-hidevariables",
    "streaming1-custom-lastvariablewithfilter",
    "streaming1-custom-nonereturntypetransformer",
    "streaming1-custom-sendcommunication",
    "streaming1-custom-simpletypescustomnode",
    "streaming1-custom-stateful",
    "streaming1-custom-unionwitheditors",
    "streaming1-service-accountservice",
    "streaming1-service-clienthttpservice",
    "streaming1-service-collectiontypesservice",
    "streaming1-service-complexreturnobjectservice",
    "streaming1-service-componentservice",
    "streaming1-service-configuratorservice",
    "streaming1-service-customvalidatedservice",
    "streaming1-service-datestypesservice",
    "streaming1-service-dynamicmultipleparamsservice",
    "streaming1-service-dynamicservice",
    "streaming1-service-echoenumservice",
    "streaming1-service-enricher",
    "streaming1-service-enrichernullresult",
    "streaming1-service-listreturnobjectservice",
    "streaming1-service-log",
    "streaming1-service-meetingservice",
    "streaming1-service-modelconfigreader",
    "streaming1-service-multipleparamsservice",
    "streaming1-service-optionaltypesservice",
    "streaming1-service-paramservice",
    "streaming1-service-providedcomponent-component-v1",
    "streaming1-service-providedcomponent-component-v2",
    "streaming1-service-providedcomponent-component-v3",
    "streaming1-service-servicemodelservice",
    "streaming1-service-servicewithdictparametereditor",
    "streaming1-service-simpletypesservice",
    "streaming1-service-transactionservice",
    "streaming1-service-unionreturnobjectservice",
    "streaming1-sink-communicationsink",
    "streaming1-sink-dead-end-lite",
    "streaming1-sink-kafka-string",
    "streaming1-sink-monitor",
    "streaming1-sink-sendsms",
    "streaming1-source-boundedsource",
    "streaming1-source-classinstancesource",
    "streaming1-source-communicationsource",
    "streaming1-source-csv-source",
    "streaming1-source-csv-source-lite",
    "streaming1-source-genericsourcewithcustomvariables",
    "streaming1-source-kafka-transaction",
    "streaming1-source-onesource",
    "streaming1-source-real-kafka",
    "streaming1-source-real-kafka-json-sampleproduct",
    "streaming1-source-sql-source"
  )

  private lazy val expectedComponentIdsForLimitedAnonymousUser: List[String] = List(
    "builtin-choice",
    "builtin-filter",
    "builtin-record-variable",
    "builtin-split",
    "builtin-variable",
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
    "streaming2-service-servicewithdictparametereditor",
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

  private lazy val expectedComponentIdsForAdminUser: List[String] = List(
    "builtin-choice",
    "builtin-filter",
    "builtin-record-variable",
    "builtin-split",
    "builtin-variable",
    "streaming1-custom-additionalvariable",
    "streaming1-custom-constantstatetransformer",
    "streaming1-custom-constantstatetransformerlongvalue",
    "streaming1-custom-customfilter",
    "streaming1-custom-enrichwithadditionaldata",
    "streaming1-custom-hidevariables",
    "streaming1-custom-lastvariablewithfilter",
    "streaming1-custom-nonereturntypetransformer",
    "streaming1-custom-sendcommunication",
    "streaming1-custom-simpletypescustomnode",
    "streaming1-custom-stateful",
    "streaming1-custom-unionwitheditors",
    "streaming1-service-accountservice",
    "streaming1-service-clienthttpservice",
    "streaming1-service-collectiontypesservice",
    "streaming1-service-complexreturnobjectservice",
    "streaming1-service-componentservice",
    "streaming1-service-configuratorservice",
    "streaming1-service-customvalidatedservice",
    "streaming1-service-datestypesservice",
    "streaming1-service-dynamicmultipleparamsservice",
    "streaming1-service-dynamicservice",
    "streaming1-service-echoenumservice",
    "streaming1-service-enricher",
    "streaming1-service-enrichernullresult",
    "streaming1-service-listreturnobjectservice",
    "streaming1-service-log",
    "streaming1-service-meetingservice",
    "streaming1-service-modelconfigreader",
    "streaming1-service-multipleparamsservice",
    "streaming1-service-optionaltypesservice",
    "streaming1-service-paramservice",
    "streaming1-service-providedcomponent-component-v1",
    "streaming1-service-providedcomponent-component-v2",
    "streaming1-service-providedcomponent-component-v3",
    "streaming1-service-servicemodelservice",
    "streaming1-service-servicewithdictparametereditor",
    "streaming1-service-simpletypesservice",
    "streaming1-service-transactionservice",
    "streaming1-service-unionreturnobjectservice",
    "streaming1-sink-communicationsink",
    "streaming1-sink-dead-end-lite",
    "streaming1-sink-kafka-string",
    "streaming1-sink-monitor",
    "streaming1-sink-sendsms",
    "streaming1-source-boundedsource",
    "streaming1-source-classinstancesource",
    "streaming1-source-communicationsource",
    "streaming1-source-csv-source",
    "streaming1-source-csv-source-lite",
    "streaming1-source-genericsourcewithcustomvariables",
    "streaming1-source-kafka-transaction",
    "streaming1-source-onesource",
    "streaming1-source-real-kafka",
    "streaming1-source-real-kafka-json-sampleproduct",
    "streaming1-source-sql-source",
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
    "streaming2-service-servicewithdictparametereditor",
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
