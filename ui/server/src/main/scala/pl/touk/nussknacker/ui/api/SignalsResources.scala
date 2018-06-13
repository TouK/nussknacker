package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.server.{Directive0, Directives, Route}
import pl.touk.http.argonaut.Argonaut62Support
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.ui.process.ProcessObjectsFinder
import pl.touk.nussknacker.ui.process.repository.{FetchingProcessRepository, ProcessRepository}
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.ProcessDetails
import pl.touk.http.argonaut.Argonaut62Support
import pl.touk.nussknacker.ui.security.api.{LoggedUser, Permission}
import shapeless.syntax.typeable._

import scala.concurrent.{ExecutionContext, Future}

class SignalsResources(modelData: ModelData,
                       processRepository: FetchingProcessRepository,
                       val processAuthorizer:AuthorizeProcess)
                      (implicit ec: ExecutionContext)
  extends Directives
    with Argonaut62Support
    with RouteWithUser
    with AuthorizeProcessDirectives {

  import pl.touk.nussknacker.ui.codec.UiCodecs._

  def route(implicit user: LoggedUser): Route = {
    pathPrefix("signal" / Segment / Segment) { (signalType, processId) =>
      canDeploy(processId) {
        post {

          //Map[String, String] should be enough for now
          entity(as[Map[String, String]]) { params =>
            complete {
              modelData.dispatchSignal(signalType, processId, params.mapValues(_.asInstanceOf[AnyRef]))
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
    processRepository.fetchAllProcessesDetails().map { processList =>
      ProcessObjectsFinder.findSignals(processList, modelData.processDefinition)
    }
  }

}


//TODO: when parameters are List[Parameter] there is some argonaut issue
case class SignalDefinition(name: String, parameters: List[String], availableProcesses: List[String])
