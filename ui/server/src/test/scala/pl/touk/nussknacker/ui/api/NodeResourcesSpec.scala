package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.testkit.ScalatestRouteTest
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Decoder
import io.circe.generic.extras.semiauto.deriveConfiguredDecoder
import org.scalatest._
import pl.touk.nussknacker.engine.additionalInfo.{MarkdownNodeAdditionalInfo, NodeAdditionalInfo}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.ExpressionParseError
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.api.{MetaData, StreamMetaData}
import pl.touk.nussknacker.engine.graph.NodeDataCodec._
import pl.touk.nussknacker.engine.graph.evaluatedparam.{BranchParameters, Parameter}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.expression.NodeExpressionId._
import pl.touk.nussknacker.engine.graph.node
import pl.touk.nussknacker.engine.graph.node.{Enricher, NodeData}
import pl.touk.nussknacker.engine.graph.service.ServiceRef
import pl.touk.nussknacker.engine.graph.sink.SinkRef
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.restmodel.definition.UIParameter
import pl.touk.nussknacker.restmodel.displayedgraph.ProcessProperties
import pl.touk.nussknacker.restmodel.validation.PrettyValidationErrors
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.api.helpers.TestFactory.withPermissions
import pl.touk.nussknacker.ui.api.helpers.{EspItTest, ProcessTestData}
import pl.touk.nussknacker.engine.api.CirceUtil._

class NodeResourcesSpec extends FunSuite with ScalatestRouteTest with FailFastCirceSupport
  with Matchers with PatientScalaFutures with OptionValues with BeforeAndAfterEach with BeforeAndAfterAll with EspItTest {

  private val nodeRoute = new NodesResources(fetchingProcessRepository, typeToConfig.mapValues(_.modelData))

  private implicit val typingResultDecoder: Decoder[TypingResult]
    = NodesResources.prepareTypingResultDecoder(typeToConfig.all.head._2.modelData)
  private implicit val uiParameterDecoder: Decoder[UIParameter] = deriveConfiguredDecoder[UIParameter]
  private implicit val responseDecoder: Decoder[NodeValidationResult] = deriveConfiguredDecoder[NodeValidationResult]

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
      val request = NodeValidationRequest(data, ProcessProperties(StreamMetaData()), Map("existButString" -> Typed[String], "longValue" -> Typed[Long]), None)

      Post(s"/nodes/${testProcess.id}/validation", toEntity(request)) ~> withPermissions(nodeRoute, testPermissionRead) ~> check {
        responseAs[NodeValidationResult] shouldBe NodeValidationResult(
          parameters = None,
          expressionType = Some(typing.Unknown),
          validationErrors = List(PrettyValidationErrors.formatErrorMessage(ExpressionParseError("Bad expression type, expected: Boolean, found: String", data.id, Some(DefaultExpressionId), data.expression.expression))),
          validationPerformed = true)
      }
    }
  }

  test("validates sink expression") {
    val testProcess = ProcessTestData.sampleDisplayableProcess
    saveProcess(testProcess) {
      val data: node.Sink = node.Sink("mysink", SinkRef("kafka-string", List(
        Parameter("value", Expression("spel", "notvalidspelexpression")),
        Parameter("topic", Expression("spel", "'test-topic'")))),
        None, None)
      val request = NodeValidationRequest(data, ProcessProperties(StreamMetaData()), Map("existButString" -> Typed[String], "longValue" -> Typed[Long]), None)

      Post(s"/nodes/${testProcess.id}/validation", toEntity(request)) ~> withPermissions(nodeRoute, testPermissionRead) ~> check {
        responseAs[NodeValidationResult] shouldBe NodeValidationResult(
          parameters = None,
          expressionType = None,
          validationErrors = List(PrettyValidationErrors.formatErrorMessage(ExpressionParseError("Non reference 'notvalidspelexpression' occurred. Maybe you missed '#' in front of it?",
            data.id, Some("value"), "notvalidspelexpression"))),
          validationPerformed = true)
      }
    }
  }

  test("validates nodes using dictionaries") {
    val testProcess = ProcessTestData.sampleDisplayableProcess
    saveProcess(testProcess) {
      val data: node.Filter = node.Filter("id", Expression("spel", "#DICT.Bar != #DICT.Foo"))
      val request = NodeValidationRequest(data, ProcessProperties(StreamMetaData()), Map(), None)

      Post(s"/nodes/${testProcess.id}/validation", toEntity(request)) ~> withPermissions(nodeRoute, testPermissionRead) ~> check {
        responseAs[NodeValidationResult] shouldBe NodeValidationResult(
          parameters = None,
          expressionType = Some(Typed[Boolean]),
          validationErrors = Nil,
          validationPerformed = true)
      }
    }
  }

  test("handles global variables in NodeValidationRequest") {

    val testProcess = ProcessTestData.sampleDisplayableProcess

    saveProcess(testProcess) {
      val data = node.Join("id", Some("output"), "enrichWithAdditionalData", List(
        Parameter("additional data value", "#longValue")
      ), List(
        BranchParameters("b1", List(Parameter("role", "'Events'"))),
        BranchParameters("b2", List(Parameter("role", "'Additional data'")))
      ), None)
      val request = NodeValidationRequest(data, ProcessProperties(StreamMetaData()), Map(), Some(
        Map(
          //It's a bit tricky, because FE does not distinguish between global and local vars...
          "b1" -> Map("existButString" -> Typed[String], "meta" -> Typed[MetaData]),
          "b2" -> Map("longValue" -> Typed[Long], "meta" -> Typed[MetaData])
        )
      ))

      Post(s"/nodes/${testProcess.id}/validation", toEntity(request)) ~> withPermissions(nodeRoute, testPermissionRead) ~> check {
        val res = responseAs[NodeValidationResult]
        res.validationErrors shouldBe Nil
      }
    }
  }

}
