package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.testkit.ScalatestRouteTest
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Decoder
import io.circe.generic.extras.semiauto.deriveConfiguredDecoder
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, OptionValues}
import pl.touk.nussknacker.engine.additionalInfo.{AdditionalInfo, MarkdownAdditionalInfo}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{ExpressionParserCompilationError, InvalidPropertyFixedValue}
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.api.{MetaData, ProcessAdditionalFields, StreamMetaData}
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
import pl.touk.nussknacker.ui.api.helpers.{EspItTest, ProcessTestData, TestCategories}
import pl.touk.nussknacker.ui.process.subprocess.SubprocessResolver
import pl.touk.nussknacker.ui.validation.ProcessValidation
import pl.touk.nussknacker.engine.kafka.KafkaFactory._

class NodeResourcesSpec extends AnyFunSuite with ScalatestRouteTest with FailFastCirceSupport
  with Matchers with PatientScalaFutures with OptionValues with BeforeAndAfterEach with BeforeAndAfterAll with EspItTest {

  import pl.touk.nussknacker.engine.api.CirceUtil._

  private val testProcess = ProcessTestData.sampleDisplayableProcess.copy(category = TestCategories.TestCat)

  private val validation = ProcessValidation(typeToConfig.mapValues(_.modelData), typeToConfig.mapValues(_.additionalPropertiesConfig), typeToConfig.mapValues(_.additionalValidators), new SubprocessResolver(subprocessRepository))
  private val nodeRoute = new NodesResources(futureFetchingProcessRepository, subprocessRepository, typeToConfig.mapValues(_.modelData), validation)

  private implicit val typingResultDecoder: Decoder[TypingResult]
  = NodesResources.prepareTypingResultDecoder(typeToConfig.all.head._2.modelData)
  private implicit val uiParameterDecoder: Decoder[UIParameter] = deriveConfiguredDecoder[UIParameter]
  private implicit val responseDecoder: Decoder[NodeValidationResult] = deriveConfiguredDecoder[NodeValidationResult]
  private val processProperties = ProcessProperties(StreamMetaData(), additionalFields = Some(ProcessAdditionalFields(None, Map("numberOfThreads" -> "2", "environment" -> "test"))))

  //see SampleNodeAdditionalInfoProvider
  test("it should return additional info for process") {
    saveProcess(testProcess) {
      val data: NodeData = Enricher("1", ServiceRef("paramService", List(Parameter("id", Expression.spel("'a'")))), "out", None)
      Post(s"/nodes/${testProcess.id}/additionalInfo", toEntity(data)) ~> withPermissions(nodeRoute, testPermissionRead) ~> check {
        responseAs[AdditionalInfo] should matchPattern {
          case MarkdownAdditionalInfo(content) if content.contains("http://touk.pl?id=a") =>
        }
      }

      val dataEmpty: NodeData = Enricher("1", ServiceRef("otherService", List()), "out", None)
      Post(s"/nodes/${testProcess.id}/additionalInfo", toEntity(dataEmpty)) ~> withPermissions(nodeRoute, testPermissionRead) ~> check {
        responseAs[Option[AdditionalInfo]] shouldBe None
      }
    }
  }

  test("validates filter nodes") {
    saveProcess(testProcess) {
      val data: node.Filter = node.Filter("id", Expression.spel("#existButString"))
      val request = NodeValidationRequest(data, ProcessProperties(StreamMetaData()), Map("existButString" -> Typed[String], "longValue" -> Typed[Long]), None, None)

      Post(s"/nodes/${testProcess.id}/validation", toEntity(request)) ~> withPermissions(nodeRoute, testPermissionRead) ~> check {
        responseAs[NodeValidationResult] shouldBe NodeValidationResult(
          parameters = None,
          expressionType = Some(typing.Unknown),
          validationErrors = List(PrettyValidationErrors.formatErrorMessage(ExpressionParserCompilationError("Bad expression type, expected: Boolean, found: String", data.id, Some(DefaultExpressionId), data.expression.expression))),
          validationPerformed = true)
      }
    }
  }

  test("validates sink expression") {
    saveProcess(testProcess) {
      val data: node.Sink = node.Sink("mysink", SinkRef("kafka-string", List(
        Parameter(SinkValueParamName, Expression.spel("notvalidspelexpression")),
        Parameter(TopicParamName, Expression.spel("'test-topic'")))),
        None, None)
      val request = NodeValidationRequest(data, ProcessProperties(StreamMetaData()), Map("existButString" -> Typed[String], "longValue" -> Typed[Long]), None, None)

      Post(s"/nodes/${testProcess.id}/validation", toEntity(request)) ~> withPermissions(nodeRoute, testPermissionRead) ~> check {
        responseAs[NodeValidationResult] shouldBe NodeValidationResult(
          parameters = None,
          expressionType = None,
          validationErrors = List(PrettyValidationErrors.formatErrorMessage(ExpressionParserCompilationError("Non reference 'notvalidspelexpression' occurred. Maybe you missed '#' in front of it?",
            data.id, Some(SinkValueParamName), "notvalidspelexpression"))),
          validationPerformed = true)
      }
    }
  }

  test("validates nodes using dictionaries") {
    saveProcess(testProcess) {
      val data: node.Filter = node.Filter("id", Expression.spel("#DICT.Bar != #DICT.Foo"))
      val request = NodeValidationRequest(data, ProcessProperties(StreamMetaData()), Map(), None, None)

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
      ), None)

      Post(s"/nodes/${testProcess.id}/validation", toEntity(request)) ~> withPermissions(nodeRoute, testPermissionRead) ~> check {
        val res = responseAs[NodeValidationResult]
        res.validationErrors shouldBe Nil
      }
    }
  }

  test("it should return additional info for process properties") {
    saveProcess(testProcess) {
      import pl.touk.nussknacker.restmodel.displayedgraph.ProcessProperties.encodeProcessProperties

      Post(s"/properties/${testProcess.id}/additionalInfo", toEntity(processProperties)) ~> withPermissions(nodeRoute, testPermissionRead) ~> check {
        responseAs[AdditionalInfo] should matchPattern {
          case MarkdownAdditionalInfo(content) if content.equals("2 threads will be used on environment 'test'") =>
        }
      }
    }
  }

  test("validate properties") {
    saveProcess(testProcess) {
      val request = PropertiesValidationRequest(ProcessProperties(StreamMetaData(), additionalFields = Some(ProcessAdditionalFields(None, Map("numberOfThreads" -> "a", "environment" -> "test")))))

      Post(s"/properties/${testProcess.id}/validation", toEntity(request)) ~> withPermissions(nodeRoute, testPermissionRead) ~> check {
        responseAs[NodeValidationResult] shouldBe NodeValidationResult(
          parameters = None,
          expressionType = None,
          validationErrors = List(PrettyValidationErrors.formatErrorMessage(InvalidPropertyFixedValue("numberOfThreads", Some("Number of threads"), "a", List("1", "2"), ""))),
          validationPerformed = true)
      }
    }
  }
}
