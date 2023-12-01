package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.testkit.ScalatestRouteTest
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Decoder
import io.circe.generic.extras.semiauto.deriveConfiguredDecoder
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, OptionValues}
import pl.touk.nussknacker.engine.additionalInfo.{AdditionalInfo, MarkdownAdditionalInfo}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{
  BlankId,
  ExpressionParserCompilationError,
  InvalidPropertyFixedValue,
  NodeIdValidationError,
  ScenarioIdError,
  ScenarioNameValidationError
}
import pl.touk.nussknacker.engine.api.displayedgraph.ProcessProperties
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.api.{ProcessAdditionalFields, StreamMetaData}
import pl.touk.nussknacker.engine.graph.NodeDataCodec._
import pl.touk.nussknacker.engine.graph.evaluatedparam.Parameter
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.expression.NodeExpressionId._
import pl.touk.nussknacker.engine.graph.node
import pl.touk.nussknacker.engine.graph.node.{Enricher, NodeData}
import pl.touk.nussknacker.engine.graph.service.ServiceRef
import pl.touk.nussknacker.engine.graph.sink.SinkRef
import pl.touk.nussknacker.restmodel.definition.UIParameter
import pl.touk.nussknacker.restmodel.validation.PrettyValidationErrors
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.api.helpers.TestFactory.withPermissions
import pl.touk.nussknacker.ui.api.helpers.{NuResourcesTest, ProcessTestData, TestCategories}
import pl.touk.nussknacker.ui.process.fragment.FragmentResolver
import pl.touk.nussknacker.ui.validation.{NodeValidator, ParametersValidator, UIProcessValidator}
import pl.touk.nussknacker.engine.kafka.KafkaFactory._
import pl.touk.nussknacker.ui.suggester.ExpressionSuggester

class NodesResourcesSpec
    extends AnyFunSuite
    with ScalatestRouteTest
    with FailFastCirceSupport
    with Matchers
    with PatientScalaFutures
    with OptionValues
    with BeforeAndAfterEach
    with BeforeAndAfterAll
    with NuResourcesTest {

  import pl.touk.nussknacker.engine.api.CirceUtil._

  private val testProcess = ProcessTestData.sampleDisplayableProcess.copy(category = TestCategories.Category1)

  private val validation = UIProcessValidator(
    typeToConfig.mapValues(_.modelData),
    typeToConfig.mapValues(_.scenarioPropertiesConfig),
    typeToConfig.mapValues(_.additionalValidators),
    new FragmentResolver(fragmentRepository)
  )

  private val nodeRoute = new NodesResources(
    processService,
    typeToConfig.mapValues(_.modelData),
    validation,
    typeToConfig.mapValues(v => new NodeValidator(v.modelData, fragmentRepository)),
    typeToConfig.mapValues(v =>
      new ExpressionSuggester(
        v.modelData.modelDefinition.expressionConfig,
        v.modelData.modelDefinitionWithTypes.typeDefinitions,
        v.modelData.uiDictServices,
        v.modelData.modelClassLoader.classLoader,
        v.scenarioPropertiesConfig.keys
      )
    ),
    typeToConfig.mapValues(v => new ParametersValidator(v.modelData, v.scenarioPropertiesConfig.keys))
  )

  private implicit val typingResultDecoder: Decoder[TypingResult] =
    NodesResources.prepareTypingResultDecoder(typeToConfig.all.head._2.modelData)
  private implicit val uiParameterDecoder: Decoder[UIParameter]       = deriveConfiguredDecoder[UIParameter]
  private implicit val responseDecoder: Decoder[NodeValidationResult] = deriveConfiguredDecoder[NodeValidationResult]

  private val processProperties = ProcessProperties.combineTypeSpecificProperties(
    StreamMetaData(),
    additionalFields =
      ProcessAdditionalFields(None, Map("numberOfThreads" -> "2", "environment" -> "test"), StreamMetaData.typeName)
  )

  // see SampleNodeAdditionalInfoProvider
  test("it should return additional info for process") {
    saveProcess(testProcess) {
      val data: NodeData =
        Enricher("1", ServiceRef("paramService", List(Parameter("id", Expression.spel("'a'")))), "out", None)
      Post(s"/nodes/${testProcess.id}/additionalInfo", toEntity(data)) ~> withPermissions(
        nodeRoute,
        testPermissionRead
      ) ~> check {
        responseAs[AdditionalInfo] should matchPattern {
          case MarkdownAdditionalInfo(content) if content.contains("http://touk.pl?id=a") =>
        }
      }

      val dataEmpty: NodeData = Enricher("1", ServiceRef("otherService", List()), "out", None)
      Post(s"/nodes/${testProcess.id}/additionalInfo", toEntity(dataEmpty)) ~> withPermissions(
        nodeRoute,
        testPermissionRead
      ) ~> check {
        responseAs[Option[AdditionalInfo]] shouldBe None
      }
    }
  }

  test("validates filter nodes") {
    saveProcess(testProcess) {
      val data: node.Filter = node.Filter("id", Expression.spel("#existButString"))
      val request = NodeValidationRequest(
        data,
        ProcessProperties(StreamMetaData()),
        Map("existButString" -> Typed[String], "longValue" -> Typed[Long]),
        None,
        None
      )

      Post(s"/nodes/${testProcess.id}/validation", toEntity(request)) ~> withPermissions(
        nodeRoute,
        testPermissionRead
      ) ~> check {
        responseAs[NodeValidationResult] shouldBe NodeValidationResult(
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
      }
    }
  }

  test("validates sink expression") {
    saveProcess(testProcess) {
      val data: node.Sink = node.Sink(
        "mysink",
        SinkRef(
          "kafka-string",
          List(
            Parameter(SinkValueParamName, Expression.spel("notvalidspelexpression")),
            Parameter(TopicParamName, Expression.spel("'test-topic'"))
          )
        ),
        None,
        None
      )
      val request = NodeValidationRequest(
        data,
        ProcessProperties(StreamMetaData()),
        Map("existButString" -> Typed[String], "longValue" -> Typed[Long]),
        None,
        None
      )

      Post(s"/nodes/${testProcess.id}/validation", toEntity(request)) ~> withPermissions(
        nodeRoute,
        testPermissionRead
      ) ~> check {
        responseAs[NodeValidationResult] should matchPattern {
          case NodeValidationResult(_, _, validationErrors, _)
              if validationErrors == List(
                PrettyValidationErrors.formatErrorMessage(
                  ExpressionParserCompilationError(
                    "Non reference 'notvalidspelexpression' occurred. Maybe you missed '#' in front of it?",
                    data.id,
                    Some(SinkValueParamName),
                    "notvalidspelexpression"
                  )
                )
              ) =>
        }
      }
    }
  }

  test("validates nodes using dictionaries") {
    saveProcess(testProcess) {
      val data: node.Filter = node.Filter("id", Expression.spel("#DICT.Bar != #DICT.Foo"))
      val request           = NodeValidationRequest(data, ProcessProperties(StreamMetaData()), Map(), None, None)

      Post(s"/nodes/${testProcess.id}/validation", toEntity(request)) ~> withPermissions(
        nodeRoute,
        testPermissionRead
      ) ~> check {
        responseAs[NodeValidationResult] shouldBe NodeValidationResult(
          parameters = None,
          expressionType = Some(Typed[Boolean]),
          validationErrors = Nil,
          validationPerformed = true
        )
      }
    }
  }

  test("it should return additional info for process properties") {
    saveProcess(testProcess) {

      Post(s"/properties/${testProcess.id}/additionalInfo", toEntity(processProperties)) ~> withPermissions(
        nodeRoute,
        testPermissionRead
      ) ~> check {
        responseAs[AdditionalInfo] should matchPattern {
          case MarkdownAdditionalInfo(content) if content.equals("2 threads will be used on environment 'test'") =>
        }
      }
    }
  }

  test("validates node id") {
    saveProcess(testProcess) {
      val blankValue        = " "
      val data: node.Filter = node.Filter(blankValue, Expression.spel("true"))
      val request           = NodeValidationRequest(data, ProcessProperties(StreamMetaData()), Map(), None, None)

      Post(s"/nodes/${testProcess.id}/validation", toEntity(request)) ~> withPermissions(
        nodeRoute,
        testPermissionRead
      ) ~> check {
        responseAs[NodeValidationResult].validationErrors shouldBe List(
          PrettyValidationErrors.formatErrorMessage(NodeIdValidationError(BlankId, " "))
        )
      }
    }
  }

  test("validate properties") {
    saveProcess(testProcess) {
      val request = PropertiesValidationRequest(
        additionalFields = ProcessAdditionalFields(
          properties = StreamMetaData().toMap ++ Map("numberOfThreads" -> "a", "environment" -> "test"),
          metaDataType = StreamMetaData.typeName,
          description = None
        ),
        id = testProcess.id
      )

      Post(s"/properties/${testProcess.id}/validation", toEntity(request)) ~> withPermissions(
        nodeRoute,
        testPermissionRead
      ) ~> check {
        responseAs[NodeValidationResult] shouldBe NodeValidationResult(
          parameters = None,
          expressionType = None,
          validationErrors = List(
            PrettyValidationErrors.formatErrorMessage(
              InvalidPropertyFixedValue("numberOfThreads", Some("Number of threads"), "a", List("1", "2"), "")
            )
          ),
          validationPerformed = true
        )
      }
    }
  }

  test("validate scenario id") {
    saveProcess(testProcess) {
      val blankValue = " "
      val request = PropertiesValidationRequest(
        additionalFields = ProcessAdditionalFields(
          properties = StreamMetaData().toMap ++ Map("numberOfThreads" -> "a", "environment" -> "test"),
          metaDataType = StreamMetaData.typeName,
          description = None
        ),
        id = blankValue
      )

      val expectedErrors = List(
        PrettyValidationErrors.formatErrorMessage(
          InvalidPropertyFixedValue("numberOfThreads", Some("Number of threads"), "a", List("1", "2"), "")
        ),
        PrettyValidationErrors.formatErrorMessage(
          ScenarioNameValidationError(
            s"Invalid scenario name $blankValue. Only digits, letters, underscore (_), hyphen (-) and space in the middle are allowed",
            "Provided scenario name is invalid for this category. Please enter valid name using only specified characters."
          )
        ),
        PrettyValidationErrors.formatErrorMessage(
          ScenarioIdError(BlankId, blankValue, isFragment = false)
        )
      )
      Post(s"/properties/${testProcess.id}/validation", toEntity(request)) ~> withPermissions(
        nodeRoute,
        testPermissionRead
      ) ~> check {
        responseAs[NodeValidationResult].validationErrors should contain allElementsOf expectedErrors
      }
    }
  }

}
