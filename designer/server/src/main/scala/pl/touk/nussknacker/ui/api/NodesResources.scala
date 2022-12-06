package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.server.Route
import cats.data.OptionT
import cats.instances.future._
import cats.syntax.traverse._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Decoder
import io.circe.generic.JsonCodec
import io.circe.generic.extras.semiauto.deriveConfiguredDecoder
import org.springframework.util.ClassUtils
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.additionalInfo.{AdditionalInfo, AdditionalInfoProvider}
import pl.touk.nussknacker.engine.api.CirceUtil._
import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.MissingParameters
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.typed.TypingResultDecoder
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.compile.nodecompilation.NodeDataValidator.OutgoingEdge
import pl.touk.nussknacker.engine.compile.nodecompilation.{NodeDataValidator, ValidationNotPerformed, ValidationPerformed}
import pl.touk.nussknacker.engine.graph.NodeDataCodec._
import pl.touk.nussknacker.engine.graph.node.NodeData
import pl.touk.nussknacker.engine.util.loader.ScalaServiceLoader
import pl.touk.nussknacker.engine.variables.GlobalVariablesPreparer
import pl.touk.nussknacker.restmodel.definition.UIParameter
import pl.touk.nussknacker.restmodel.displayedgraph.displayablenode.Edge
import pl.touk.nussknacker.restmodel.displayedgraph.{DisplayableProcess, ProcessProperties}
import pl.touk.nussknacker.restmodel.process.ProcessingType
import pl.touk.nussknacker.restmodel.validation.PrettyValidationErrors
import pl.touk.nussknacker.restmodel.validation.ValidationResults.NodeValidationError
import pl.touk.nussknacker.ui.api.NodesResources.{preparePropertiesRequestDecoder, prepareValidationContext}
import pl.touk.nussknacker.ui.definition.UIProcessObjectsFactory
import pl.touk.nussknacker.ui.process.processingtypedata.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.repository.FetchingProcessRepository
import pl.touk.nussknacker.ui.process.subprocess.SubprocessRepository
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.validation.ProcessValidation

import scala.concurrent.{ExecutionContext, Future}

/**
  * This class should contain operations invoked for each node (e.g. node validation, retrieving additional data etc.)
  */
class NodesResources(val processRepository: FetchingProcessRepository[Future],
                     subprocessRepository: SubprocessRepository,
                     typeToConfig: ProcessingTypeDataProvider[ModelData],
                     processValidation: ProcessValidation,
                    )(implicit val ec: ExecutionContext)
  extends ProcessDirectives with FailFastCirceSupport with RouteWithUser {

  private val additionalInfoProviders = new AdditionalInfoProviders(typeToConfig)
  private val nodeValidator = new NodeValidator

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
          implicit val requestDecoder: Decoder[NodeValidationRequest] = NodesResources.prepareNodeRequestDecoder(modelData)
          entity(as[NodeValidationRequest]) { nodeData =>
            complete {
              nodeValidator.validate(nodeData, modelData, process.id, subprocessRepository)
            }
          }
        }
      }
    } ~ pathPrefix("properties" / Segment) { processName =>
      (post & processDetailsForName[Unit](processName)) { process =>
        path("additionalInfo") {
          entity(as[ProcessProperties]) { processProperties =>
            complete {
              additionalInfoProviders.prepareAdditionalInfoForProperties(processProperties.toMetaData(process.id), process.processingType)
            }
          }
        } ~ path("validation") {
          val modelData = typeToConfig.forTypeUnsafe(process.processingType)
          implicit val requestDecoder: Decoder[PropertiesValidationRequest] = preparePropertiesRequestDecoder(modelData)
          entity(as[PropertiesValidationRequest]) { properties =>
            complete {
              val scenario = DisplayableProcess(processName, properties.processProperties, Nil, Nil, process.processingType, Some(process.processCategory))
              val result = processValidation.validate(scenario, process.processCategory)
              NodeValidationResult(
                parameters = None,
                expressionType = None,
                validationErrors = result.errors.processPropertiesErrors,
                validationPerformed = true)
            }
          }
        }
      }
    }
  }
}

object NodesResources {

  def prepareTypingResultDecoder(modelData: ModelData): Decoder[TypingResult] = {
    new TypingResultDecoder(name => ClassUtils.forName(name, modelData.modelClassLoader.classLoader)).decodeTypingResults
  }

  def prepareNodeRequestDecoder(modelData: ModelData): Decoder[NodeValidationRequest] = {
    implicit val typeDecoder: Decoder[TypingResult] = prepareTypingResultDecoder(modelData)
    deriveConfiguredDecoder[NodeValidationRequest]
  }

  def preparePropertiesRequestDecoder(modelData: ModelData): Decoder[PropertiesValidationRequest] = {
    implicit val typeDecoder: Decoder[TypingResult] = prepareTypingResultDecoder(modelData)
    deriveConfiguredDecoder[PropertiesValidationRequest]
  }

  def prepareValidationContext(modelData: ModelData)(variableTypes: Map[String, TypingResult])(implicit metaData: MetaData): ValidationContext = {
    val emptyCtx = GlobalVariablesPreparer(modelData.processWithObjectsDefinition.expressionConfig).emptyValidationContext(metaData)
    //It's a bit tricky, because FE does not distinguish between global and local vars...
    val localVars = variableTypes.filterNot(e => emptyCtx.globalVariables.keys.toSet.contains(e._1))
    emptyCtx.copy(localVariables = localVars)
  }

}

class NodeValidator {
  def validate(nodeData: NodeValidationRequest, modelData: ModelData, processId: String, subprocessRepository: SubprocessRepository): NodeValidationResult = {
    implicit val metaData: MetaData = nodeData.processProperties.toMetaData(processId)

    val validationContext = prepareValidationContext(modelData)(nodeData.variableTypes)
    val branchCtxs = nodeData.branchVariableTypes.getOrElse(Map.empty).mapValues(prepareValidationContext(modelData))

    val edges = nodeData.outgoingEdges.getOrElse(Nil).map(e => OutgoingEdge(e.to, e.edgeType))
    NodeDataValidator.validate(nodeData.nodeData, modelData, validationContext, branchCtxs, k => subprocessRepository.get(k).map(_.canonical), edges) match {
      case ValidationNotPerformed => NodeValidationResult(parameters = None, expressionType = None, validationErrors = Nil, validationPerformed = false)
      case ValidationPerformed(errors, parameters, expressionType) =>
        val uiParams = parameters.map(_.map(UIProcessObjectsFactory.createUIParameter))

        //We don't return MissingParameter error when we are returning those missing parameters to be added - since
        //it's not really exception ATM
        def shouldIgnoreError(pce: ProcessCompilationError): Boolean = pce match {
          case MissingParameters(params, _) => params.forall(missing => uiParams.exists(_.exists(_.name == missing)))
          case _ => false
        }

        val uiErrors = errors.filterNot(shouldIgnoreError).map(PrettyValidationErrors.formatErrorMessage)
        NodeValidationResult(
          parameters = uiParams,
          expressionType = expressionType,
          validationErrors = uiErrors,
          validationPerformed = true)
    }
  }
}

class AdditionalInfoProviders(typeToConfig: ProcessingTypeDataProvider[ModelData]) {

  //TODO: do not load provider for each request...
  private val nodeProviders: ProcessingTypeDataProvider[Option[NodeData => Future[Option[AdditionalInfo]]]] = typeToConfig.mapValues(pt => ScalaServiceLoader
    .load[AdditionalInfoProvider](pt.modelClassLoader.classLoader).headOption.map(_.nodeAdditionalInfo(pt.processConfig)))
  private val propertiesProviders: ProcessingTypeDataProvider[Option[MetaData => Future[Option[AdditionalInfo]]]] = typeToConfig.mapValues(pt => ScalaServiceLoader
    .load[AdditionalInfoProvider](pt.modelClassLoader.classLoader).headOption.map(_.propertiesAdditionalInfo(pt.processConfig)))

  def prepareAdditionalInfoForNode(nodeData: NodeData, processingType: ProcessingType)(implicit ec: ExecutionContext): Future[Option[AdditionalInfo]] = {
    (for {
      provider <- OptionT.fromOption[Future](nodeProviders.forType(processingType).flatten)
      data <- OptionT(provider(nodeData))
    } yield data).value
  }

  def prepareAdditionalInfoForProperties(metaData: MetaData, processingType: ProcessingType)(implicit ec: ExecutionContext): Future[Option[AdditionalInfo]] = {
    (for {
      provider <- OptionT.fromOption[Future](propertiesProviders.forType(processingType).flatten)
      data <- OptionT(provider(metaData))
    } yield data).value
  }
}

@JsonCodec(encodeOnly = true) case class NodeValidationResult(parameters: Option[List[UIParameter]],
                                                              expressionType: Option[TypingResult],
                                                              validationErrors: List[NodeValidationError],
                                                              validationPerformed: Boolean)

@JsonCodec(encodeOnly = true) case class NodeValidationRequest(nodeData: NodeData,
                                                               processProperties: ProcessProperties,
                                                               variableTypes: Map[String, TypingResult],
                                                               branchVariableTypes: Option[Map[String, Map[String, TypingResult]]],
                                                               //TODO: remove Option when FE is ready
                                                               outgoingEdges: Option[List[Edge]])

@JsonCodec(encodeOnly = true) case class PropertiesValidationRequest(processProperties: ProcessProperties)

