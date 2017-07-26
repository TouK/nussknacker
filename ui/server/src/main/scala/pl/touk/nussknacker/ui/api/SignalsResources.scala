package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.server.{Directives, Route}
import argonaut.Parse
import argonaut.Json
import cats.data.OptionT
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectDefinition
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.{ProcessDefinition, QueryableStateName}
import pl.touk.nussknacker.engine.definition.SignalDispatcher
import pl.touk.nussknacker.engine.flink.queryablestate.EspQueryableClient
import pl.touk.nussknacker.engine.graph.node.{CustomNode, NodeData}
import pl.touk.nussknacker.ui.db.entity.ProcessEntity.ProcessingType
import pl.touk.nussknacker.ui.db.entity.ProcessEntity.ProcessingType.ProcessingType
import pl.touk.nussknacker.ui.process.{JobStatusService, ProcessObjectsFinder}
import pl.touk.nussknacker.ui.process.displayedgraph.ProcessStatus
import pl.touk.nussknacker.ui.process.repository.ProcessRepository
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.ProcessDetails
import pl.touk.nussknacker.ui.security.{LoggedUser, Permission}
import pl.touk.http.argonaut.Argonaut62Support
import shapeless.syntax.typeable._

import scala.concurrent.{ExecutionContext, Future}
import scala.math.Ordering

class SignalsResources(signalDispatcher: Map[ProcessingType, SignalDispatcher],
                       processDefinition: Map[ProcessingType, ProcessDefinition[ObjectDefinition]],
                       processRepository: ProcessRepository)
                      (implicit ec: ExecutionContext) extends Directives with Argonaut62Support  with RouteWithUser {

  import pl.touk.nussknacker.ui.codec.UiCodecs._

  //we don't need signals for standalone mode
  val processingType = ProcessingType.Streaming

  def route(implicit user: LoggedUser): Route = {
    authorize(user.hasPermission(Permission.Deploy)) {
      pathPrefix("signal" / Segment / Segment) { (signalType, processId) =>
        post {

          //Map[String, String] should be enough for now
          entity(as[Map[String, String]]) { params =>
            complete {
              val dispatcher = signalDispatcher(processingType)
              dispatcher.dispatchSignal(signalType, processId, params.mapValues(_.asInstanceOf[AnyRef]))
            }
          }
        }
      } ~ path("signal") {
        get {
          complete {
            prepareSignalDefinitions(processingType)
          }
        }
      }
    }
  }

  private def prepareSignalDefinitions(processingType: ProcessingType)(implicit user: LoggedUser): Future[Map[String, SignalDefinition]] = {
    //TODO: only processes that are deployed right now??
    processRepository.fetchDisplayableProcesses().map { processList =>
      ProcessObjectsFinder.findSignals(processList, processDefinition(processingType))
    }
  }

}


//TODO: when parameters are List[Parameter] there is some argonaut issue
case class SignalDefinition(name: String, parameters: List[String], availableProcesses: List[String])
