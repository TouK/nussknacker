package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.{Directives, ExceptionHandler, Route}
import com.typesafe.scalalogging.LazyLogging
import pl.touk.http.argonaut.Argonaut62Support
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.graph.evaluatedparam.Parameter
import pl.touk.nussknacker.engine.util.service.query.{ExpressionServiceQuery, ServiceQuery}
import pl.touk.nussknacker.engine.util.service.query.ServiceQuery.{QueryResult, ServiceNotFoundException}
import pl.touk.nussknacker.ui.db.entity.ProcessEntity.ProcessingType
import pl.touk.nussknacker.ui.db.entity.ProcessEntity.ProcessingType.ProcessingType
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import argonaut._

class ServiceRoutes(modelDataMap: Map[ProcessingType, ModelData])
                   (implicit ec: ExecutionContext)
  extends Directives
    with RouteWithUser
    with Argonaut62Support
    with LazyLogging{

  import ServiceRoutes._
  import pl.touk.nussknacker.ui.codec.UiCodecs._

  private implicit def serviceExceptionHandler: ExceptionHandler =
    ExceptionHandler {
      case e@ServiceNotFoundException(serviceName) =>
        complete(HttpResponse(
          status = NotFound,
          entity = HttpEntity(ContentTypes.`application/json`,JsonThrowable(e)
            .asJson
            .toString())
        ))
      case NonFatal(e) =>
        logger.error("service invocation went wrong", e)
        complete(HttpResponse(
          status = InternalServerError,
          entity = HttpEntity(ContentTypes.`application/json`,JsonThrowable(e)
            .asJson
            .toString()
          )
        ))
    }

  override def route(implicit user: LoggedUser): Route = {
    authorize(user.isAdmin) {
      handleExceptions(serviceExceptionHandler) {
        invokeServicePath
      }
    }
  }

  private def invokeServicePath =
    path("service" / Segment / Segment) { (processingTypeName, serviceName) =>
      post {
        val processingType = ProcessingType.withName(processingTypeName)
        val modelData = modelDataMap(processingType)
        entity(as[List[Parameter]]) { params =>
          complete {
            invokeService(serviceName, modelData, params)
          }
        }
      }
    }

  private def invokeService(serviceName: String, modelData: ModelData, params: List[Parameter]): Future[QueryResult] = {
    ExpressionServiceQuery(new ServiceQuery(modelData), modelData)
      .invoke(serviceName, params)
  }
}

object ServiceRoutes {
  case class JsonThrowable(className: String, message: Option[String], stacktrace: List[String])

  object JsonThrowable {
    def apply(e: Throwable):JsonThrowable =
      JsonThrowable(
        className = e.getClass.getCanonicalName,
        message = Option(e.getMessage),
        stacktrace = e.getStackTrace.toList.map(_.toString)
      )
  }

}