package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.testkit.ScalatestRouteTest
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import org.scalatest._
import pl.touk.nussknacker.engine.additionalInfo.{MarkdownNodeAdditionalInfo, NodeAdditionalInfo}
import pl.touk.nussknacker.engine.api.StreamMetaData
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.ExpressionParseError
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.compile.NodeTypingInfo
import pl.touk.nussknacker.engine.graph.evaluatedparam.Parameter
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node.{Enricher, NodeData}
import pl.touk.nussknacker.engine.graph.service.ServiceRef
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.api.helpers.TestFactory.withPermissions
import pl.touk.nussknacker.ui.api.helpers.{EspItTest, ProcessTestData}
import pl.touk.nussknacker.engine.graph.NodeDataCodec._
import pl.touk.nussknacker.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.nussknacker.engine.graph.node
import pl.touk.nussknacker.restmodel.displayedgraph.ProcessProperties
import pl.touk.nussknacker.restmodel.validation.ValidationResults.NodeValidationError
import pl.touk.nussknacker.ui.validation.PrettyValidationErrors

class NodeResourcesSpec extends FunSuite with ScalatestRouteTest with FailFastCirceSupport
  with Matchers with PatientScalaFutures with OptionValues with BeforeAndAfterEach with BeforeAndAfterAll with EspItTest {

  private val nodeRoute = new NodesResources(processRepository, typeToConfig.mapValues(_.modelData))

  //see SampleNodeAdditionalInfoProvider
  test("it should return additional info for process") {
    val testProcess = ProcessTestData.sampleDisplayableProcess
    saveProcess(testProcess) {
      val data: NodeData = Enricher("1", ServiceRef("paramService", List(Parameter("id", Expression("spel", "'a'")))), "out", None)
      Post(s"/nodes/${testProcess.id}/additionalData", toEntity(data)) ~> withPermissions(nodeRoute, testPermissionRead) ~> check {
        responseAs[NodeAdditionalInfo] should matchPattern {
          case MarkdownNodeAdditionalInfo(content) if content.contains("http://touk.pl?id=a")=>
        }
      }

      val dataEmpty: NodeData = Enricher("1", ServiceRef("otherService", List()), "out", None)
      Post(s"/nodes/${testProcess.id}/additionalData", toEntity(dataEmpty)) ~> withPermissions(nodeRoute, testPermissionRead) ~> check  {
        responseAs[Option[NodeAdditionalInfo]] shouldBe None
      }
    }
  }
  
  test("validates filter nodes") {

    val testProcess = ProcessTestData.sampleDisplayableProcess
    saveProcess(testProcess) {
      val data: node.Filter = node.Filter("id", Expression("spel", "#existButString"))
      val request = NodeValidationRequest(data, ProcessProperties(StreamMetaData(),
        ExceptionHandlerRef(Nil)), Map("existButString" -> Typed[String], "longValue" -> Typed[Long]))

      Post(s"/nodes/${testProcess.id}/validation", toEntity(request)) ~> withPermissions(nodeRoute, testPermissionRead) ~> check {
        responseAs[NodeValidationResult] shouldBe NodeValidationResult(List(
          PrettyValidationErrors.formatErrorMessage(ExpressionParseError("Bad expression type, expected: boolean, found: java.lang.String",
            data.id, Some(NodeTypingInfo.DefaultExpressionId), data.expression.expression))
        ), validationPerformed = true)
      }
    }
  }

}
