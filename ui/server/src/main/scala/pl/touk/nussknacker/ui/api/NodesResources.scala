package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.server.Route
import cats.data.OptionT
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import pl.touk.nussknacker.engine.ProcessingTypeData
import pl.touk.nussknacker.engine.additionalInfo.{NodeAdditionalInfo, NodeAdditionalInfoProvider}
import pl.touk.nussknacker.engine.graph.node.NodeData
import pl.touk.nussknacker.engine.util.loader.ScalaServiceLoader
import pl.touk.nussknacker.ui.process.repository.FetchingProcessRepository
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.process.processingtypedata.ProcessingTypeDataProvider
import cats.instances.future._
import pl.touk.nussknacker.engine.ProcessingTypeData.ProcessingType

import scala.concurrent.{ExecutionContext, Future}

/**
 * This class should contain operations invoked for each node (e.g. node validation, retrieving additional data etc.)
 */
class NodesResources(val processRepository: FetchingProcessRepository[Future], additionalInfoProvider: AdditionalInfoProvider)(implicit val ec: ExecutionContext)
  extends ProcessDirectives with FailFastCirceSupport with RouteWithUser {


  def securedRoute(implicit loggedUser: LoggedUser): Route = {
    import akka.http.scaladsl.server.Directives._
    import pl.touk.nussknacker.engine.graph.NodeDataCodec._

    path("nodes" / Segment / "additionalData") { processName =>
      (post & processDetailsForName[Unit](processName)) { process =>
        entity(as[NodeData]) { nodeData =>
          complete {
            additionalInfoProvider.prepareAdditionalDataForNode(nodeData, process.processingType)
          }
        }
      }
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