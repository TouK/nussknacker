package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.{Directives, Route}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.definition.SignalDispatcher
import pl.touk.nussknacker.ui.process.ProcessObjectsFinder
import pl.touk.nussknacker.ui.process.repository.FetchingProcessRepository
import pl.touk.nussknacker.restmodel.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.ui.process.processingtypedata.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.{ExecutionContext, Future}

class SignalsResources(modelData: ProcessingTypeDataProvider[ModelData],
                       val processRepository: FetchingProcessRepository[Future],
                       val processAuthorizer:AuthorizeProcess)(implicit val ec: ExecutionContext)
  extends Directives
    with FailFastCirceSupport
    with RouteWithUser
    with AuthorizeProcessDirectives
    with ProcessDirectives {

  def securedRoute(implicit user: LoggedUser): Route = {
    pathPrefix("signal" / Segment / Segment) { (signalType, processName) =>
      (post & processId(processName)) { processId =>
        canDeploy(processId) {
          //Map[String, String] should be enough for now
          entity(as[Map[String, String]]) { params =>
            complete {
              processRepository.fetchLatestProcessDetailsForProcessId[Unit](processId.id).map[ToResponseMarshallable] {
                case Some(process) =>
                  SignalDispatcher.dispatchSignal(modelData.forTypeUnsafe(process.processingType))(signalType, processId.name.value, params.mapValues(_.asInstanceOf[AnyRef]))
                case None =>
                  HttpResponse(status = StatusCodes.NotFound, entity = "Scenario not found")
              }
            }
          }
        }
      }
    } ~ path("signal") {
      get {
        complete {
          prepareSignalDefinitions
        }
      }
    }
  }

  private def prepareSignalDefinitions(implicit user: LoggedUser): Future[Map[String, SignalDefinition]] = {
    //TODO: only processes that are deployed right now??
    processRepository.fetchAllProcessesDetails[DisplayableProcess]().map { processList =>
      ProcessObjectsFinder.findSignals(processList, modelData.all.values.map(_.processDefinition))
    }
  }

}

//TODO: when parameters are List[Parameter] there is some argonaut issue
@JsonCodec case class SignalDefinition(name: String, parameters: List[String], availableProcesses: List[String])
