package pl.touk.nussknacker.ui.api

import io.circe.Encoder
import io.restassured.RestAssured.`given`
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import io.restassured.specification.RequestSpecification
import org.hamcrest.Matchers.equalTo
import org.scalatest.freespec.AnyFreeSpecLike
import pl.touk.nussknacker.engine.api.StreamMetaData
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.ExpressionParserCompilationError
import pl.touk.nussknacker.engine.api.displayedgraph.ProcessProperties
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.Typed
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
import pl.touk.nussknacker.engine.graph.expression.NodeExpressionId.DefaultExpressionId
import pl.touk.nussknacker.engine.graph.node
import pl.touk.nussknacker.restmodel.validation.PrettyValidationErrors

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
      val processName = ProcessName("test")
      val process = ScenarioBuilder
        .streaming(processName.value)
        .source("sourceId", "barSource")
        .emptySink("sinkId", "barSink")

      "return additional info for process" in {
        val data: NodeData =
          Enricher("enricher", ServiceRef("paramService", List(Parameter("id", Expression.spel("'a'")))), "out", None)

        createSavedProcess(process, TestCategories.Category1, TestProcessingTypes.Streaming)

        sendNodeAsJsonAsAdmin(data)
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

        val dataEmpty: NodeData = Enricher("1", ServiceRef("otherService", List()), "out", None)

        sendNodeAsJsonAsAdmin(dataEmpty)
          .when()
          .post(s"$nuDesignerHttpAddress/api/nodes/${process.id}/additionalInfo")
          .Then()
          .statusCode(200)
          .body(
            equalTo("")
          )
      }

      "validate filter nodes" in {
        createSavedProcess(process, TestCategories.Category1, TestProcessingTypes.Streaming)

        val data: node.Filter = node.Filter("id", Expression.spel("#existButString"))
        val request = NodeValidationRequest(
          data,
          ProcessProperties(StreamMetaData()),
          Map("existButString" -> Typed[String], "longValue" -> Typed[Long]),
          None,
          None
        )

        sendRequestAsJsonAsAdmin(request)
          .post(s"$nuDesignerHttpAddress/api/nodes/${process.id}/validation")
          .Then()
          .statusCode(200)
          .body(
            equalsJson(
              Encoder[NodeValidationResult]
                .apply(
                  NodeValidationResult(
                    parameters = None,
                    expressionType = Some(typing.Unknown),
                    validationErrors = List(
                      PrettyValidationErrors.formatErrorMessage(
                        ExpressionParserCompilationError(
                          "Bad expression type, expected: Boolean, found: String",
                          data.id,
                          Some(DefaultExpressionId),
                          data.expression.expression
                        )
                      )
                    ),
                    validationPerformed = true
                  )
                )
                .toString()
            )
          )
      }
    }

  }

  def sendNodeAsJsonAsAdmin(data: NodeData): RequestSpecification =
    sendNodeAsJson(data, "admin")

  def sendNodeAsJsonAsAllpermuser(data: NodeData): RequestSpecification =
    sendNodeAsJson(data, "allpermuser")

  def sendNodeAsJson(node: NodeData, user: String): RequestSpecification = {
    val json = Encoder[NodeData].apply(node)

    given
      .contentType("application/json")
      .body(json.toString())
      .and()
      .auth()
      .basic(user, user)
  }

  def sendRequestAsJsonAsAdmin(request: NodeValidationRequest): RequestSpecification =
    sendRequestAsJson(request, "admin")

  def sendRequestAsJsonAsAllpermuser(request: NodeValidationRequest): RequestSpecification =
    sendRequestAsJson(request, "allpermuser")

  def sendRequestAsJson(request: NodeValidationRequest, user: String): RequestSpecification = {
    val json = Encoder[NodeValidationRequest].apply(request)

    given
      .contentType("application/json")
      .body(json.toString())
      .and()
      .auth()
      .basic(user, user)
  }

}
