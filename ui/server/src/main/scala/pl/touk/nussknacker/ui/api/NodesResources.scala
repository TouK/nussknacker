package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Route
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import pl.touk.nussknacker.engine.ProcessingTypeData
import pl.touk.nussknacker.engine.additionalInfo.{NodeAdditionalInfo, NodeAdditionalInfoProvider}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.graph.node.NodeData
import pl.touk.nussknacker.engine.util.loader.ScalaServiceLoader
import pl.touk.nussknacker.ui.process.repository.FetchingProcessRepository
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.process.ProcessingTypeDataProvider

import scala.concurrent.{ExecutionContext, Future}

class NodesResources(val processRepository: FetchingProcessRepository[Future], typeToConfig: ProcessingTypeDataProvider[ProcessingTypeData])(implicit val ec: ExecutionContext)
  extends ProcessDirectives with FailFastCirceSupport {

  //TODO: do not load provider for each request...
  private val providers: ProcessingTypeDataProvider[Option[NodeData => Future[Option[NodeAdditionalInfo]]]] = typeToConfig.mapValues(pt => ScalaServiceLoader
    .load[NodeAdditionalInfoProvider](pt.modelData.modelClassLoader.classLoader).headOption.map(_.additionalInfo(pt.modelData.processConfig)))

  def additionalData(implicit loggedUser: LoggedUser): Route = {
    import akka.http.scaladsl.server.Directives._
    import pl.touk.nussknacker.engine.graph.NodeDataCodec._

    path("nodes" / Segment ) { processName =>
      (post & processId(processName)) { processId =>
        entity(as[NodeData]) { nodeData =>
          complete {
            processRepository.fetchLatestProcessDetailsForProcessId[CanonicalProcess](processId.id).map[ToResponseMarshallable] {
              case Some(process) =>
                providers.forType(process.processingType).flatten match {
                  case Some(fun) => fun.apply(nodeData)
                  case None => Future.successful(Option.empty[NodeAdditionalInfo])
                }
              case None =>
                HttpResponse(status = StatusCodes.NotFound, entity = "Process not found")
            }
          }
        }
      }
    }
  }

}
