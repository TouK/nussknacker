package pl.touk.nussknacker.ui.api

import io.circe.{Encoder, Json, JsonObject}
import io.restassured.RestAssured.`given`
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.hamcrest.Matchers.equalTo
import org.scalatest.freespec.AnyFreeSpecLike
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node.{Enricher, NodeData}
import pl.touk.nussknacker.engine.graph.service.ServiceRef
import pl.touk.nussknacker.test.{
  NuRestAssureExtensions,
  NuRestAssureMatchers,
  PatientScalaFutures,
  RestAssuredVerboseLogging
}
import pl.touk.nussknacker.ui.api.helpers._
import pl.touk.nussknacker.engine.graph.evaluatedparam.Parameter

import java.io.InputStream

class NodeApiSpec
    extends AnyFreeSpecLike
    with NuItTest
    with WithMockableDeploymentManager
    with NuScenarioConfigurationHelper
    with NuRestAssureExtensions
    with NuRestAssureMatchers
    with RestAssuredVerboseLogging
    with PatientScalaFutures {

  "The endpoint for nodes when" - {
    "authenticated should" - {
      "return additional info for process" in {
        val processName = ProcessName("test")

        val process = ScenarioBuilder
          .streaming(processName.value)
          .source("sourceId", "barSource")
          .emptySink("sinkId", "barSink")
//          .withNodes(
//            NonEmptyList.one(
//              List(
//                canonicalnode.FlatNode(
//                  Source(
//                    "sourceId",
//                    ref = SourceRef(existingSourceFactory, List.empty),
//                    additionalFields = Some(UserDefinedAdditionalNodeFields(Some("node description"), None))
//                  )
//                ),
//                canonicalnode.FlatNode(
//                  Sink(
//                    id = "sinkId",
//                    ref = SinkRef(existingSinkFactory, List.empty),
//                    additionalFields = None
//                  )
//                )
//              )
//            )
//          )

        val data: NodeData =
          Enricher("enricher", ServiceRef("paramService", List(Parameter("id", Expression.spel("'a'")))), "out", None)

        val json: Json = Encoder[NodeData].apply(data)

        createSavedProcess(process, TestCategories.Category1, TestProcessingTypes.Streaming)

        given
          .contentType("application/json")
          .body(json.toString())
          .and()
          .auth()
          .basic("admin", "admin")
          .when()
          .post(s"$nuDesignerHttpAddress/api/nodes/${process.id}/additionalInfo")
          .Then()
          .statusCode(200)
          .body(
            equalsJson(s"""{
               |  "content": "\\nSamples:\\n\\n| id  | value |\\n| --- | ----- |\\n| a   | generated |\\n| b   | not existent |\\n\\nResults for a can be found [here](http://touk.pl?id=a)\\n",
               |  "type": "MarkdownAdditionalInfo"
               |}""".stripMargin)
          )
      }
    }

  }

}
