package pl.touk.esp.ui.api

import akka.http.scaladsl.server.{Directives, Route}
import pl.touk.esp.engine.definition.SignalDispatcher
import pl.touk.esp.ui.db.entity.ProcessEntity.ProcessingType
import pl.touk.esp.ui.db.entity.ProcessEntity.ProcessingType.ProcessingType
import pl.touk.esp.ui.security.{LoggedUser, Permission}
import pl.touk.esp.ui.util.Argonaut62Support

import scala.concurrent.ExecutionContext

class SignalsResources(signalDispatcher: Map[ProcessingType, SignalDispatcher])
                       (implicit ec: ExecutionContext) extends Directives with Argonaut62Support {
  import argonaut.ArgonautShapeless._

  def route(implicit user: LoggedUser): Route = {
    authorize(user.hasPermission(Permission.Deploy)) {
      pathPrefix("signal" / Segment) { signalType =>
        post {
          //na razie Map[String, String] wystarczy
          entity(as[Map[String, String]]) { params =>
            complete {
              val dispatcher = signalDispatcher(ProcessingType.Streaming) //na razie dla standalone nie chcemy sygnalow
              dispatcher.dispatchSignal(signalType, params.mapValues(_.asInstanceOf[AnyRef]))
            }
          }
        }
      }
    }
  }

}