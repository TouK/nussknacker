package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.{Directives, Route}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.{Codec, Decoder}
import io.circe.generic.JsonCodec
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveUnwrappedCodec}
import pl.touk.nussknacker.engine.api.CirceUtil._
import org.springframework.util.ClassUtils
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.ProcessAdditionalFields
import pl.touk.nussknacker.engine.api.graph.{Edge, ProcessProperties, ScenarioGraph}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.typed.TypingResultDecoder
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node.NodeData
import pl.touk.nussknacker.engine.graph.node.NodeData._
import pl.touk.nussknacker.restmodel.definition.{UIParameter, UIValueParameter}
import pl.touk.nussknacker.restmodel.validation.ValidationResults.NodeValidationError
import pl.touk.nussknacker.ui.additionalInfo.AdditionalInfoProviders
import pl.touk.nussknacker.ui.api.NodesResources.{preparePropertiesRequestDecoder, prepareTypingResultDecoder}
import pl.touk.nussknacker.ui.process.ProcessService
import pl.touk.nussknacker.ui.process.processingtype.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.suggester.{CaretPosition2d, ExpressionSuggester}
import pl.touk.nussknacker.ui.validation.{NodeValidator, ParametersValidator, UIProcessValidator}
import TestSourceParameters._
import scala.concurrent.ExecutionContext

/** This class should contain operations invoked for each node (e.g. node validation, retrieving additional data etc.)
  */
class NodesResources(
    protected val processService: ProcessService,
    typeToConfig: ProcessingTypeDataProvider[ModelData, _],
    typeToProcessValidator: ProcessingTypeDataProvider[UIProcessValidator, _],
    typeToNodeValidator: ProcessingTypeDataProvider[NodeValidator, _],
    typeToExpressionSuggester: ProcessingTypeDataProvider[ExpressionSuggester, _],
    typeToParametersValidator: ProcessingTypeDataProvider[ParametersValidator, _],
)(implicit val ec: ExecutionContext)
    extends Directives
    with ProcessDirectives
    with FailFastCirceSupport
    with RouteWithUser {

  private val additionalInfoProviders = new AdditionalInfoProviders(typeToConfig)

  def securedRoute(implicit loggedUser: LoggedUser): Route = {
    pathPrefix("nodes" / ProcessNameSegment) { processName =>
      (post & processDetailsForName(processName)) { process =>
        path("additionalInfo") {
          entity(as[NodeData]) { nodeData =>
            complete {
              additionalInfoProviders.prepareAdditionalInfoForNode(nodeData, process.processingType)
            }
          }
        } ~ path("validation") {
          val modelData = typeToConfig.forTypeUnsafe(process.processingType)
          implicit val requestDecoder: Decoder[NodeValidationRequest] =
            NodesResources.prepareNodeRequestDecoder(modelData)
          entity(as[NodeValidationRequest]) { nodeData =>
            complete {
              val nodeValidator = typeToNodeValidator.forTypeUnsafe(process.processingType)
              nodeValidator.validate(process.name, nodeData)
            }
          }
        }
      }
    } ~ pathPrefix("properties" / ProcessNameSegment) { processName =>
      (post & processDetailsForName(processName)) { process =>
        path("additionalInfo") {
          entity(as[ProcessProperties]) { processProperties =>
            complete {
              additionalInfoProviders.prepareAdditionalInfoForProperties(
                processProperties.toMetaData(process.name),
                process.processingType
              )
            }
          }
        } ~ path("validation") {
          val modelData = typeToConfig.forTypeUnsafe(process.processingType)
          implicit val requestDecoder: Decoder[PropertiesValidationRequest] = preparePropertiesRequestDecoder(modelData)
          entity(as[PropertiesValidationRequest]) { request =>
            complete {
              val scenarioGraph = ScenarioGraph(
                ProcessProperties(request.additionalFields),
                Nil,
                Nil
              )
              val result = typeToProcessValidator
                .forTypeUnsafe(process.processingType)
                .validate(scenarioGraph, request.name, process.isFragment)
              NodeValidationResult(
                parameters = None,
                expressionType = None,
                validationErrors = result.errors.processPropertiesErrors,
                validationPerformed = true
              )
            }
          }
        }
      }
    } ~ pathPrefix("parameters" / Segment) { processingType =>
      post {
        typeToConfig
          .forType(processingType)
          .map { modelData =>
            path("validate") {
              implicit val requestDecoder: Decoder[ParametersValidationRequest] =
                NodesResources.prepareParametersValidationDecoder(modelData)
              entity(as[ParametersValidationRequest]) { parametersToValidate =>
                complete {
                  val validator         = typeToParametersValidator.forTypeUnsafe(processingType)
                  val validationResults = validator.validate(parametersToValidate)
                  ParametersValidationResult(validationErrors = validationResults, validationPerformed = true)
                }
              }
            } ~ path("suggestions") {
              val expressionSuggester                         = typeToExpressionSuggester.forTypeUnsafe(processingType)
              implicit val typeDecoder: Decoder[TypingResult] = prepareTypingResultDecoder(modelData)
              implicit val expressionSuggestionRequestDecoder: Decoder[ExpressionSuggestionRequest] =
                ExpressionSuggestionRequest.decoder(typeDecoder)
              (post & entity(as[ExpressionSuggestionRequest])) { expressionSuggestionRequest =>
                complete {
                  expressionSuggester.expressionSuggestions(
                    expressionSuggestionRequest.expression,
                    expressionSuggestionRequest.caretPosition2d,
                    expressionSuggestionRequest.variableTypes
                  )
                }
              }
            }
          }
          .getOrElse {
            complete(
              HttpResponse(status = StatusCodes.NotFound, entity = s"ProcessingType type: $processingType not found")
            )
          }
      }
    }
  }

}

object NodesResources {

  def prepareTypingResultDecoder(modelData: ModelData): Decoder[TypingResult] = {
    new TypingResultDecoder(name =>
      ClassUtils.forName(name, modelData.modelClassLoader.classLoader)
    ).decodeTypingResults
  }

  def prepareNodeRequestDecoder(modelData: ModelData): Decoder[NodeValidationRequest] = {
    implicit val typeDecoder: Decoder[TypingResult] = prepareTypingResultDecoder(modelData)
    deriveConfiguredDecoder[NodeValidationRequest]
  }

  def prepareParametersValidationDecoder(modelData: ModelData): Decoder[ParametersValidationRequest] = {
    implicit val typeDecoder: Decoder[TypingResult]                 = prepareTypingResultDecoder(modelData)
    implicit val uiValueParameterDecoder: Decoder[UIValueParameter] = deriveConfiguredDecoder[UIValueParameter]
    deriveConfiguredDecoder[ParametersValidationRequest]
  }

  def prepareTestFromParametersDecoder(modelData: ModelData): Decoder[TestFromParametersRequest] = {
    implicit val parameterNameDecoder: Decoder[ParameterName]
    implicit val typeDecoder: Decoder[TypingResult] = prepareTypingResultDecoder(modelData)
    implicit val testSourceParametersDecoder: Decoder[TestSourceParameters] =
      deriveConfiguredDecoder[TestSourceParameters]
    deriveConfiguredDecoder[TestFromParametersRequest]
  }

  def preparePropertiesRequestDecoder(modelData: ModelData): Decoder[PropertiesValidationRequest] = {
    implicit val typeDecoder: Decoder[TypingResult] = prepareTypingResultDecoder(modelData)
    deriveConfiguredDecoder[PropertiesValidationRequest]
  }

}

object TestSourceParameters {
  implicit val parameterNameCodec: Codec[ParameterName] = deriveUnwrappedCodec
}

@JsonCodec(encodeOnly = true) final case class TestSourceParameters(
    sourceId: String,
    parameterExpressions: Map[ParameterName, Expression]
)

@JsonCodec(encodeOnly = true) final case class TestFromParametersRequest(
    sourceParameters: TestSourceParameters,
    scenarioGraph: ScenarioGraph
)

@JsonCodec(encodeOnly = true) final case class ParametersValidationResult(
    validationErrors: List[NodeValidationError],
    validationPerformed: Boolean
)

@JsonCodec(encodeOnly = true) final case class ParametersValidationRequest(
    parameters: List[UIValueParameter],
    variableTypes: Map[String, TypingResult]
)

@JsonCodec(encodeOnly = true) final case class NodeValidationResult(
    // It it used for node parameter adjustment on FE side (see ParametersUtils.ts -> adjustParameters)
    parameters: Option[List[UIParameter]],
    // expressionType is returned to present inferred types of a single, hardcoded parameter of the node
    // We currently support only type inference for an expression in the built-in components: variable and switch
    // and fields of the record-variable and fragment output (we return TypedObjectTypingResult in this case)
    // TODO: We should keep this in a map, instead of TypedObjectTypingResult as it is done in ValidationResult.typingInfo
    //       Thanks to that we could remove some code on the FE side and be closer to support also not built-in components
    expressionType: Option[TypingResult],
    validationErrors: List[NodeValidationError],
    validationPerformed: Boolean
)

@JsonCodec(encodeOnly = true) final case class NodeValidationRequest(
    nodeData: NodeData,
    processProperties: ProcessProperties,
    variableTypes: Map[String, TypingResult],
    branchVariableTypes: Option[Map[String, Map[String, TypingResult]]],
    // TODO: remove Option when FE is ready
    // In this request edges are not guaranteed to have the correct "from" field. Normally it's synced with node id but
    // when renaming node, it contains node's id before the rename.
    outgoingEdges: Option[List[Edge]]
)

@JsonCodec(encodeOnly = true) final case class PropertiesValidationRequest(
    additionalFields: ProcessAdditionalFields,
    name: ProcessName
)

final case class ExpressionSuggestionRequest(
    expression: Expression,
    caretPosition2d: CaretPosition2d,
    variableTypes: Map[String, TypingResult]
)

object ExpressionSuggestionRequest {

  implicit def decoder(implicit typing: Decoder[TypingResult]): Decoder[ExpressionSuggestionRequest] = {
    deriveConfiguredDecoder[ExpressionSuggestionRequest]
  }

}
