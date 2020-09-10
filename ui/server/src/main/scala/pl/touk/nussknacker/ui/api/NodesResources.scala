package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.server.Route
import cats.data.OptionT
import cats.instances.future._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Decoder
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.ProcessingTypeData.ProcessingType
import pl.touk.nussknacker.engine.additionalInfo.{NodeAdditionalInfo, NodeAdditionalInfoProvider}
import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.process.ParameterConfig
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.compile.nodecompilation.{NodeDataValidator, ValidationNotPerformed, ValidationPerformed}
import pl.touk.nussknacker.engine.graph.node.NodeData
import pl.touk.nussknacker.engine.util.loader.ScalaServiceLoader
import pl.touk.nussknacker.restmodel.validation.ValidationResults.NodeValidationError
import pl.touk.nussknacker.ui.process.processingtypedata.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.repository.FetchingProcessRepository
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.engine.graph.NodeDataCodec._
import pl.touk.nussknacker.restmodel.displayedgraph.ProcessProperties
import pl.touk.nussknacker.ui.validation.PrettyValidationErrors
import io.circe.generic.semiauto.deriveDecoder
import org.springframework.util.ClassUtils
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.MissingParameters
import pl.touk.nussknacker.engine.api.typed.TypingResultDecoder
import pl.touk.nussknacker.engine.variables.GlobalVariablesPreparer
import pl.touk.nussknacker.restmodel.definition.{UIParameter, UITypedExpression}
import pl.touk.nussknacker.ui.api.NodesResources.prepareValidationContext
import pl.touk.nussknacker.ui.definition.UIProcessObjectsFactory

import scala.concurrent.{ExecutionContext, Future}

/**
 * This class should contain operations invoked for each node (e.g. node validation, retrieving additional data etc.)
 */
class NodesResources(val processRepository: FetchingProcessRepository[Future],
                     typeToConfig: ProcessingTypeDataProvider[ModelData])(implicit val ec: ExecutionContext)
  extends ProcessDirectives with FailFastCirceSupport with RouteWithUser {

  private val additionalInfoProvider = new AdditionalInfoProvider(typeToConfig)

  def securedRoute(implicit loggedUser: LoggedUser): Route = {
    import akka.http.scaladsl.server.Directives._

    pathPrefix("nodes" / Segment) { processName =>
      (post & processDetailsForName[Unit](processName)) { process =>
        path("additionalData") {
          entity(as[NodeData]) { nodeData =>
            complete {
              additionalInfoProvider.prepareAdditionalDataForNode(nodeData, process.processingType)
            }
          }
        } ~ path("validation") {
          val modelData = typeToConfig.forTypeUnsafe(process.processingType)
          implicit val requestDecoder: Decoder[NodeValidationRequest] = NodesResources.prepareRequestDecoder(modelData)
          entity(as[NodeValidationRequest]) { nodeData =>
            complete {
              implicit val metaData: MetaData = nodeData.processProperties.toMetaData(process.id)

              val validationContext = prepareValidationContext(modelData)(nodeData.variableTypes)
              val branchCtxs = nodeData.branchVariableTypes.getOrElse(Map.empty).mapValues(prepareValidationContext(modelData))

              NodeDataValidator.validate(nodeData.nodeData, modelData, validationContext, branchCtxs) match {
                case ValidationNotPerformed => NodeValidationResult(parameters = None, typedExpressions = None, validationErrors = Nil, validationPerformed = false)
                case ValidationPerformed(errors, parameters, typedExpressionMap) =>
                  val uiParams = parameters.map(_.map(UIProcessObjectsFactory.createUIParameter))
                  val uiTypedExpressions = typedExpressionMap.map(_.valueByKey.map { case (name, typedExpression) =>
                    UITypedExpression(name, typedExpression.returnType)
                  }.toList)
                  //We don't return MissingParameter error when we are returning those missing parameters to be added - since
                  //it's not really exception ATM
                  def shouldIgnoreError(pce: ProcessCompilationError): Boolean = pce match {
                    case MissingParameters(params, _) => params.forall(missing => uiParams.exists(_.exists(_.name == missing)))
                    case _ => false
                  }
                  val uiErrors = errors.filterNot(shouldIgnoreError).map(PrettyValidationErrors.formatErrorMessage)
                  NodeValidationResult(
                    parameters = uiParams,
                    typedExpressions = uiTypedExpressions,
                    validationErrors = uiErrors,
                    validationPerformed = true)
              }
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

  def prepareRequestDecoder(modelData: ModelData): Decoder[NodeValidationRequest] = {
    implicit val typeDecoder: Decoder[TypingResult] = prepareTypingResultDecoder(modelData)
    deriveDecoder[NodeValidationRequest]
  }

  def prepareValidationContext(modelData: ModelData)(variableTypes: Map[String, TypingResult])(implicit metaData: MetaData): ValidationContext = {
    val emptyCtx = GlobalVariablesPreparer(modelData.processWithObjectsDefinition.expressionConfig).emptyValidationContext(metaData)
    //It's a bit tricky, because FE does not distinguish between global and local vars...
    val localVars = variableTypes.filterNot(e => emptyCtx.globalVariables.keys.toSet.contains(e._1))
    emptyCtx.copy(localVariables = localVars)
  }

}

class AdditionalInfoProvider(typeToConfig: ProcessingTypeDataProvider[ModelData]) {

  //TODO: do not load provider for each request...
  private val providers: ProcessingTypeDataProvider[Option[NodeData => Future[Option[NodeAdditionalInfo]]]] = typeToConfig.mapValues(pt => ScalaServiceLoader
    .load[NodeAdditionalInfoProvider](pt.modelClassLoader.classLoader).headOption.map(_.additionalInfo(pt.processConfig)))

  def prepareAdditionalDataForNode(nodeData: NodeData, processingType: ProcessingType)(implicit ec: ExecutionContext): Future[Option[NodeAdditionalInfo]] = {
    (for {
      provider <- OptionT.fromOption[Future](providers.forType(processingType).flatten)
      data <- OptionT(provider(nodeData))
    } yield data).value
  }

}

@JsonCodec(encodeOnly = true) case class NodeValidationResult(parameters: Option[List[UIParameter]],
                                                              typedExpressions: Option[List[UITypedExpression]],
                                                              validationErrors: List[NodeValidationError],
                                                              validationPerformed: Boolean)

@JsonCodec(encodeOnly = true) case class NodeValidationRequest(nodeData: NodeData,
                                            processProperties: ProcessProperties,
                                            variableTypes: Map[String, TypingResult], branchVariableTypes: Option[Map[String, Map[String, TypingResult]]])


