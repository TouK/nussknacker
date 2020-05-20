package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.server.Route
import cats.data.OptionT
import cats.instances.future._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Decoder
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.ProcessingTypeData
import pl.touk.nussknacker.engine.ProcessingTypeData.ProcessingType
import pl.touk.nussknacker.engine.additionalInfo.{NodeAdditionalInfo, NodeAdditionalInfoProvider}
import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.process.ParameterConfig
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult, Unknown}
import pl.touk.nussknacker.engine.compile.nodevalidation.NodeDataValidator
import pl.touk.nussknacker.engine.graph.node.NodeData
import pl.touk.nussknacker.engine.util.loader.ScalaServiceLoader
import pl.touk.nussknacker.restmodel.validation.ValidationResults.NodeValidationError
import pl.touk.nussknacker.ui.process.processingtypedata.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.repository.FetchingProcessRepository
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.engine.graph.NodeDataCodec._
import pl.touk.nussknacker.restmodel.displayedgraph.ProcessProperties
import pl.touk.nussknacker.ui.definition.UIParameter
import pl.touk.nussknacker.ui.validation.PrettyValidationErrors

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Success, Try}

/**
 * This class should contain operations invoked for each node (e.g. node validation, retrieving additional data etc.)
 */
class NodesResources(val processRepository: FetchingProcessRepository[Future],
                     additionalInfoProvider: AdditionalInfoProvider, typeToConfig: ProcessingTypeDataProvider[ProcessingTypeData])(implicit val ec: ExecutionContext)
  extends ProcessDirectives with FailFastCirceSupport with RouteWithUser {


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
          entity(as[NodeValidationRequest]) { nodeData =>
            complete {
              val modelData = typeToConfig.forTypeUnsafe(process.processingType)
              val globals = modelData.modelData.processDefinition.expressionConfig.globalVariables.mapValues(_.returnType)
              val validationContext = ValidationContext(nodeData.variableTypes, globals, None)
              implicit val metaData = nodeData.processProperties.toMetaData(process.id)
              val baseResult = NodeDataValidator.validate(nodeData.nodeData, modelData.modelData, validationContext)
              NodeValidationResult(baseResult._1.map(_.map(UIParameter(_, ParameterConfig.empty))), baseResult._2.map(PrettyValidationErrors.formatErrorMessage))
            }
          }
        }
      }
    }
  }
}

object NodeValidationRequest {

  private val objectName = classOf[Object].toString

  //FIXME: this is not enough, we have to make it more similar to: TypeEncoders, at least
  implicit val decodeTypingResults: Decoder[TypingResult] = Decoder.instanceTry { hcursor =>
    hcursor.downField("refClazzName").focus.flatMap(_.asString) match {
      case Some(`objectName`) => Success(Unknown)
      case Some(name) => Try(Typed.typedClass(getClass.getClassLoader.loadClass(name))).orElse(Try(Unknown))
      case None => Success(Unknown)
    }
  }

}

class AdditionalInfoProvider(typeToConfig: ProcessingTypeDataProvider[ProcessingTypeData]) {

  //TODO: do not load provider for each request...
  private val providers: ProcessingTypeDataProvider[Option[NodeData => Future[Option[NodeAdditionalInfo]]]] = typeToConfig.mapValues(pt => ScalaServiceLoader
    .load[NodeAdditionalInfoProvider](pt.modelData.modelClassLoader.classLoader).headOption.map(_.additionalInfo(pt.modelData.processConfig)))

  def prepareAdditionalDataForNode(nodeData: NodeData, processingType: ProcessingType)(implicit ec: ExecutionContext): Future[Option[NodeAdditionalInfo]] = {
    (for {
      provider <- OptionT.fromOption[Future](providers.forType(processingType).flatten)
      data <- OptionT(provider(nodeData))
    } yield data).value
  }

}

//parameters =>
@JsonCodec(encodeOnly = true) case class NodeValidationResult(parameters: Option[List[UIParameter]], validationErrors: List[NodeValidationError])

@JsonCodec case class NodeValidationRequest(nodeData: NodeData,
                                            processProperties: ProcessProperties,
                                            variableTypes: Map[String, TypingResult])


