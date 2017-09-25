package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.server.{Directives, Route}
import pl.touk.http.argonaut.Argonaut62Support
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.ui.process.ProcessObjectsFinder
import pl.touk.nussknacker.ui.process.repository.ProcessRepository
import pl.touk.nussknacker.ui.security.{LoggedUser, Permission}

import scala.concurrent.{ExecutionContext, Future}

class SignalsResources(modelData: ModelData,
                       processRepository: ProcessRepository)
                      (implicit ec: ExecutionContext) extends Directives with Argonaut62Support  with RouteWithUser {

  import pl.touk.nussknacker.ui.codec.UiCodecs._

  def route(implicit user: LoggedUser): Route = {
    authorize(user.hasPermission(Permission.Deploy)) {
      pathPrefix("signal" / Segment / Segment) { (signalType, processId) =>
        post {

          //Map[String, String] should be enough for now
          entity(as[Map[String, String]]) { params =>
            complete {
              modelData.dispatchSignal(signalType, processId, params.mapValues(_.asInstanceOf[AnyRef]))
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
  }

  private def prepareSignalDefinitions(implicit user: LoggedUser): Future[Map[String, SignalDefinition]] = {
    //TODO: only processes that are deployed right now??
    processRepository.fetchProcessesDetails().map { processList =>
      ProcessObjectsFinder.findSignals(processList, modelData.processDefinition)
    }
  }

}


//TODO: when parameters are List[Parameter] there is some argonaut issue
case class SignalDefinition(name: String, parameters: List[String], availableProcesses: List[String])
