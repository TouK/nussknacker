package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model._
import cats.data.OptionT
import cats.data.Validated.Invalid
import cats.instances.future._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Decoder
import io.circe.generic.JsonCodec
import io.circe.generic.extras.semiauto.deriveConfiguredDecoder
import org.springframework.util.ClassUtils
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.additionalInfo.{AdditionalInfo, AdditionalInfoProvider}
import pl.touk.nussknacker.engine.api.CirceUtil._
import pl.touk.nussknacker.engine.api.{MetaData, NodeId}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{EmptyNodeId, MissingParameters}
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.typed.TypingResultDecoder
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.compile.{ExpressionCompiler, FragmentResolver}
import pl.touk.nussknacker.engine.compile.nodecompilation.NodeDataValidator.OutgoingEdge
import pl.touk.nussknacker.engine.compile.nodecompilation.{
  NodeDataValidator,
  ValidationNotPerformed,
  ValidationPerformed
}
import pl.touk.nussknacker.engine.graph.NodeDataCodec._
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node.NodeData
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.engine.util.loader.ScalaServiceLoader
import pl.touk.nussknacker.engine.variables.GlobalVariablesPreparer
import pl.touk.nussknacker.restmodel.definition.{UIParameter, UIValueParameter}
import pl.touk.nussknacker.restmodel.displayedgraph.displayablenode.Edge
import pl.touk.nussknacker.restmodel.displayedgraph.{DisplayableProcess, ProcessProperties}
import pl.touk.nussknacker.restmodel.process.ProcessingType
import pl.touk.nussknacker.restmodel.validation.PrettyValidationErrors
import pl.touk.nussknacker.restmodel.validation.ValidationResults.NodeValidationError
import pl.touk.nussknacker.ui.api.NodesResources.{
  preparePropertiesRequestDecoder,
  prepareTypingResultDecoder,
  prepareValidationContext
}
import pl.touk.nussknacker.ui.definition.UIProcessObjectsFactory
import pl.touk.nussknacker.ui.process.processingtypedata.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.repository.FetchingProcessRepository
import pl.touk.nussknacker.ui.process.fragment.FragmentRepository
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.suggester.{CaretPosition2d, ExpressionSuggester}
import pl.touk.nussknacker.ui.validation.ProcessValidation

import scala.concurrent.{ExecutionContext, Future}

/** This class should contain operations invoked for each node (e.g. node validation, retrieving additional data etc.)
  */
class NodesResources(
    val processRepository: FetchingProcessRepository[Future],
    fragmentRepository: FragmentRepository,
    typeToConfig: ProcessingTypeDataProvider[ModelData, _],
    processValidation: ProcessValidation,
    typeToExpressionSuggester: ProcessingTypeDataProvider[ExpressionSuggester, _]
)(implicit val ec: ExecutionContext)
    extends ProcessDirectives
    with FailFastCirceSupport
    with RouteWithUser {

  private val additionalInfoProviders = new AdditionalInfoProviders(typeToConfig)
  private val nodeValidator           = new NodeValidator

  def securedRoute(implicit loggedUser: LoggedUser): Route = {
    import akka.http.scaladsl.server.Directives._

    pathPrefix("nodes" / Segment) { processName =>
      (post & processDetailsForName[Unit](processName)) { process =>
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
              nodeValidator.validate(nodeData, modelData, process.id, fragmentRepository)
            }
          }
        }
      }
    } ~ pathPrefix("properties" / Segment) { processName =>
      (post & processDetailsForName[Unit](processName)) { process =>
        path("additionalInfo") {
          entity(as[ProcessProperties]) { processProperties =>
            complete {
              additionalInfoProviders.prepareAdditionalInfoForProperties(
                processProperties.toMetaData(process.id),
                process.processingType
              )
            }
          }
        } ~ path("validation") {
          val modelData = typeToConfig.forTypeUnsafe(process.processingType)
          implicit val requestDecoder: Decoder[PropertiesValidationRequest] = preparePropertiesRequestDecoder(modelData)
          entity(as[PropertiesValidationRequest]) { properties =>
            complete {
              val scenario = DisplayableProcess(
                processName,
                properties.processProperties,
                Nil,
                Nil,
                process.processingType,
                process.processCategory
              )
              val result = processValidation.validate(scenario)
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
                  val validationResults =
                    NodesResources.validate(modelData, parametersToValidate, parametersToValidate.scenarioName)
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
                    expressionSuggestionRequest.variables
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

  def validate(
      modelData: ModelData,
      request: ParametersValidationRequest,
      processName: String
  ): List[NodeValidationError] = {
    implicit val metaData: MetaData = request.processProperties.toMetaData(processName)
    val context                     = prepareValidationContext(modelData)(request.variableTypes)
    val expressionCompiler          = ExpressionCompiler.withoutOptimization(modelData)
    request.parameters
      .map(param => expressionCompiler.compile(param.expression, Some(param.name), context, param.typ)(NodeId("")))
      .collect { case Invalid(a) => a.map(PrettyValidationErrors.formatErrorMessage).toList }
      .flatten
  }

  def prepareTypingResultDecoder(modelData: ModelData): Decoder[TypingResult] = {
    new TypingResultDecoder(name =>
      ClassUtils.forName(name, modelData.modelClassLoader.classLoader)
    ).decodeTypingResults
  }

  def prepareNodeRequestDecoder(modelData: ModelData): Decoder[NodeValidationRequest] = {
    implicit val typeDecoder: Decoder[TypingResult] = prepareTypingResultDecoder(modelData)
    implicit val nodeNameRequestDecoder: Decoder[NodeNameValidationRequest] = deriveConfiguredDecoder[NodeNameValidationRequest]
    deriveConfiguredDecoder[NodeValidationRequest]
  }

  def prepareParametersValidationDecoder(modelData: ModelData): Decoder[ParametersValidationRequest] = {
    implicit val typeDecoder: Decoder[TypingResult]                 = prepareTypingResultDecoder(modelData)
    implicit val uiValueParameterDecoder: Decoder[UIValueParameter] = deriveConfiguredDecoder[UIValueParameter]
    deriveConfiguredDecoder[ParametersValidationRequest]
  }

  def prepareTestFromParametersDecoder(modelData: ModelData): Decoder[TestFromParametersRequest] = {
    implicit val typeDecoder: Decoder[TypingResult] = prepareTypingResultDecoder(modelData)
    implicit val testSourceParametersDecoder: Decoder[TestSourceParameters] =
      deriveConfiguredDecoder[TestSourceParameters]
    deriveConfiguredDecoder[TestFromParametersRequest]
  }

  def preparePropertiesRequestDecoder(modelData: ModelData): Decoder[PropertiesValidationRequest] = {
    implicit val typeDecoder: Decoder[TypingResult] = prepareTypingResultDecoder(modelData)
    deriveConfiguredDecoder[PropertiesValidationRequest]
  }

  def prepareValidationContext(
      modelData: ModelData
  )(variableTypes: Map[String, TypingResult])(implicit metaData: MetaData): ValidationContext = {
    val emptyCtx = GlobalVariablesPreparer(modelData.modelDefinition.expressionConfig).emptyValidationContext(metaData)
    // It's a bit tricky, because FE does not distinguish between global and local vars...
    val localVars = variableTypes.filterNot(e => emptyCtx.globalVariables.keys.toSet.contains(e._1))
    emptyCtx.copy(localVariables = localVars)
  }

}

class NodeValidator {
  def validate(
      nodeData: NodeValidationRequest,
      modelData: ModelData,
      processId: String,
      fragmentRepository: FragmentRepository
  ): NodeValidationResult = {
    implicit val metaData: MetaData = nodeData.processProperties.toMetaData(processId)

    val validationContext = prepareValidationContext(modelData)(nodeData.variableTypes)
    val branchCtxs = nodeData.branchVariableTypes.getOrElse(Map.empty).mapValuesNow(prepareValidationContext(modelData))

    val edges            = nodeData.outgoingEdges.getOrElse(Nil).map(e => OutgoingEdge(e.to, e.edgeType))
    val fragmentResolver = FragmentResolver(k => fragmentRepository.get(k).map(_.canonical))

    val nodeIdErrors = validateNodeId(nodeData.nodeNameRequest)

    new NodeDataValidator(modelData, fragmentResolver).validate(
      nodeData.nodeData,
      validationContext,
      branchCtxs,
      edges
    ) match {
      case ValidationNotPerformed =>
        NodeValidationResult(
          parameters = None,
          expressionType = None,
          validationErrors = nodeIdErrors.map(PrettyValidationErrors.formatErrorMessage),
          validationPerformed = false
        )
      case ValidationPerformed(errors, parameters, expressionType) =>
        val uiParams = parameters.map(_.map(UIProcessObjectsFactory.createUIParameter))

        // We don't return MissingParameter error when we are returning those missing parameters to be added - since
        // it's not really exception ATM
        def shouldIgnoreError(pce: ProcessCompilationError): Boolean = pce match {
          case MissingParameters(params, _) => params.forall(missing => uiParams.exists(_.exists(_.name == missing)))
          case _                            => false
        }

        val uiErrors = (errors ++ nodeIdErrors).filterNot(shouldIgnoreError).map(PrettyValidationErrors.formatErrorMessage)
        NodeValidationResult(
          parameters = uiParams,
          expressionType = expressionType,
          validationErrors = uiErrors,
          validationPerformed = true
        )
    }
  }

  private def validateNodeId(request: NodeNameValidationRequest): List[ProcessCompilationError] = {
    if (request.newName.isEmpty) List(EmptyNodeId) else Nil
  }

}

class AdditionalInfoProviders(typeToConfig: ProcessingTypeDataProvider[ModelData, _]) {

  // TODO: do not load provider for each request...
  private val nodeProviders: ProcessingTypeDataProvider[Option[NodeData => Future[Option[AdditionalInfo]]], _] =
    typeToConfig.mapValues(pt =>
      ScalaServiceLoader
        .load[AdditionalInfoProvider](pt.modelClassLoader.classLoader)
        .headOption
        .map(_.nodeAdditionalInfo(pt.processConfig))
    )
  private val propertiesProviders: ProcessingTypeDataProvider[Option[MetaData => Future[Option[AdditionalInfo]]], _] =
    typeToConfig.mapValues(pt =>
      ScalaServiceLoader
        .load[AdditionalInfoProvider](pt.modelClassLoader.classLoader)
        .headOption
        .map(_.propertiesAdditionalInfo(pt.processConfig))
    )

  def prepareAdditionalInfoForNode(nodeData: NodeData, processingType: ProcessingType)(
      implicit ec: ExecutionContext
  ): Future[Option[AdditionalInfo]] = {
    (for {
      provider <- OptionT.fromOption[Future](nodeProviders.forType(processingType).flatten)
      data     <- OptionT(provider(nodeData))
    } yield data).value
  }

  def prepareAdditionalInfoForProperties(metaData: MetaData, processingType: ProcessingType)(
      implicit ec: ExecutionContext
  ): Future[Option[AdditionalInfo]] = {
    (for {
      provider <- OptionT.fromOption[Future](propertiesProviders.forType(processingType).flatten)
      data     <- OptionT(provider(metaData))
    } yield data).value
  }
}

@JsonCodec(encodeOnly = true) final case class TestSourceParameters(
    sourceId: String,
    parameterExpressions: Map[String, Expression]
)

@JsonCodec(encodeOnly = true) final case class TestFromParametersRequest(
    sourceParameters: TestSourceParameters,
    displayableProcess: DisplayableProcess
)

@JsonCodec(encodeOnly = true) final case class ParametersValidationResult(
    validationErrors: List[NodeValidationError],
    validationPerformed: Boolean
)

// TODO do not pass scenarioName, processProperties. Based on processingType prepare global variables
@JsonCodec(encodeOnly = true) final case class ParametersValidationRequest(
    scenarioName: String,
    parameters: List[UIValueParameter],
    processProperties: ProcessProperties,
    variableTypes: Map[String, TypingResult]
)

@JsonCodec(encodeOnly = true) final case class NodeValidationResult(
    parameters: Option[List[UIParameter]],
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
    outgoingEdges: Option[List[Edge]],
  nodeNameRequest: NodeNameValidationRequest
)

@JsonCodec(encodeOnly = true) final case class NodeNameValidationRequest(newName: String)

@JsonCodec(encodeOnly = true) final case class PropertiesValidationRequest(processProperties: ProcessProperties)

// TODO like in 'validate' create globalVariables based on processingType on backend side. Do not pass them from FE.
final case class ExpressionSuggestionRequest(
    expression: Expression,
    caretPosition2d: CaretPosition2d,
    variables: Map[String, TypingResult]
)

object ExpressionSuggestionRequest {
  implicit def decoder(implicit typing: Decoder[TypingResult]): Decoder[ExpressionSuggestionRequest] = {
    deriveConfiguredDecoder[ExpressionSuggestionRequest]
  }
}
