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
import pl.touk.nussknacker.engine.compile.nodevalidation.{NodeDataValidator, ValidationNotPerformed, ValidationPerformed}
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
import pl.touk.nussknacker.ui.definition.UIParameter

import scala.concurrent.{ExecutionContext, Future}

/**
 * This class should contain operations invoked for each node (e.g. node validation, retrieving additional data etc.)
 */
class NodesResources(val processRepository: FetchingProcessRepository[Future],
                     typeToConfig: ProcessingTypeDataProvider[ModelData])(implicit val ec: ExecutionContext)
  extends ProcessDirectives with FailFastCirceSupport with RouteWithUser {

  private val additionalInfoProvider = new AdditionalInfoProvider(typeToConfig)

  private def prepareRequestDecoder(modelData: ModelData): Decoder[NodeValidationRequest] = {
    implicit val typeDecoder: Decoder[TypingResult] =
      new TypingResultDecoder(name => ClassUtils.forName(name, modelData.modelClassLoader.classLoader)).decodeTypingResults
    deriveDecoder[NodeValidationRequest]
  }

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
          implicit val requestDecoder: Decoder[NodeValidationRequest] = prepareRequestDecoder(modelData)
          entity(as[NodeValidationRequest]) { nodeData =>
            complete {
              val globals = modelData.processDefinition.expressionConfig.globalVariables.mapValues(_.returnType)
              val validationContext = ValidationContext(nodeData.variableTypes, globals, None)
              implicit val metaData: MetaData = nodeData.processProperties.toMetaData(process.id)
              NodeDataValidator.validate(nodeData.nodeData, modelData, validationContext) match {
                case ValidationNotPerformed => NodeValidationResult(None, Nil, validationPerformed = false)
                case ValidationPerformed(errors, parameters) =>
                  val uiParams = parameters.map(_.map(UIParameter(_, ParameterConfig.empty)))
                  //We don't return MissingParameter error when we are returning those missing parameters to be added - since
                  //it's not really exception ATM
                  def shouldIgnoreError(pce: ProcessCompilationError): Boolean = pce match {
                    case MissingParameters(params, _) => params.forall(missing => uiParams.exists(_.exists(_.name == missing)))
                    case _ => false
                  }
                  val uiErrors = errors.filterNot(shouldIgnoreError).map(PrettyValidationErrors.formatErrorMessage)
                  NodeValidationResult(uiParams, uiErrors, validationPerformed = true)
              }
            }
          }
        }
      }
    }
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

@JsonCodec(encodeOnly = true) case class NodeValidationResult(parameters: Option[List[UIParameter]], validationErrors: List[NodeValidationError], validationPerformed: Boolean)

@JsonCodec(encodeOnly = true) case class NodeValidationRequest(nodeData: NodeData,
                                            processProperties: ProcessProperties,
                                            variableTypes: Map[String, TypingResult])


