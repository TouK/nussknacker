package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.{Directives, ExceptionHandler, Route}
import pl.touk.http.argonaut.Argonaut62Support
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.Displayable
import pl.touk.nussknacker.engine.util.json.BestEffortJsonEncoder
import pl.touk.nussknacker.engine.util.service.query.ServiceQuery
import pl.touk.nussknacker.engine.util.service.query.ServiceQuery.ServiceNotFoundException
import pl.touk.nussknacker.ui.db.entity.ProcessEntity.ProcessingType
import pl.touk.nussknacker.ui.db.entity.ProcessEntity.ProcessingType.ProcessingType
import pl.touk.nussknacker.ui.security.api.{LoggedUser, Permission}

import scala.concurrent.ExecutionContext

// TODO: GUI
class ServiceRoutes(modelDataMap: Map[ProcessingType, ModelData])
                   (implicit ec: ExecutionContext)
  extends Directives
    with RouteWithUser
    with Argonaut62Support {

  private val encoder = BestEffortJsonEncoder(failOnUnkown = false)

  private implicit val metaData = ServiceQuery.Implicits.metaData

  private implicit def serviceExceptionHandler: ExceptionHandler =
    ExceptionHandler {
      case ServiceNotFoundException(serviceName) =>
        complete(HttpResponse(StatusCodes.NotFound, entity = s"Service '$serviceName' not found."))
      case e: IllegalArgumentException =>
        complete(HttpResponse(StatusCodes.BadRequest, entity = s"Illegal argument ${e.getMessage}"))
    }

  private def invokeServicePath =
    path("service" / Segment / Segment) { (processingTypeName, serviceName) =>
      post {
        val processingType = ProcessingType.withName(processingTypeName)
        val modelData = modelDataMap(processingType)
        entity(as[Map[String, String]]) { params =>
          complete {
            new ServiceQuery(modelData)
              .invoke(serviceName, params)
              .map(encoder.encode)
          }
        }
      }
    }

  override def route(implicit user: LoggedUser): Route = {
    authorize(user.isAdmin) {
      handleExceptions(serviceExceptionHandler) {
        invokeServicePath
      }
    }
  }
}