package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse, MessageEntity, ResponseEntity}
import akka.http.scaladsl.server.{Directives, ExceptionHandler, Route}
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.graph.evaluatedparam.Parameter
import pl.touk.nussknacker.engine.util.service.query.{ExpressionServiceQuery, ServiceQuery}
import pl.touk.nussknacker.engine.util.service.query.ServiceQuery.{QueryResult, ServiceNotFoundException}
import pl.touk.nussknacker.engine.ProcessingTypeData.ProcessingType
import pl.touk.nussknacker.ui.security.api.{LoggedUser, Permission}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.{Encoder, Json, ObjectEncoder}
import io.circe.generic.JsonCodec
import io.circe.syntax._
import pl.touk.nussknacker.engine.api.process.WithCategories
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.QueryServiceResult
import pl.touk.nussknacker.engine.util.json.BestEffortJsonEncoder

class ServiceRoutes(modelDataMap: Map[ProcessingType, ModelData])
                   (implicit ec: ExecutionContext)
  extends Directives
    with RouteWithUser
    with FailFastCirceSupport
    with LazyLogging{

  import ServiceRoutes._

  private implicit val encoder: Encoder[ServiceQuery.QueryResult] = {
    val resultEncoder: Encoder[Any] = BestEffortJsonEncoder(failOnUnkown = false).circeEncoder
    //FIXME: semi-auto like below does not work :/
    //implicit val queryResult: Encoder[QueryServiceResult] = io.circe.generic.semiauto.deriveEncoder[QueryServiceResult]
    //io.circe.generic.semiauto.deriveEncoder[ServiceQuery.QueryResult]
    new Encoder[QueryResult] {
      override def apply(a: QueryResult): Json = Json.obj(
        "result" -> resultEncoder(a.result),
        "collectedResults" -> Json.fromValues(a.collectedResults.map(r => Json.obj("name" -> Json.fromString(r.name), "result" -> resultEncoder(r.result)))))
    }
  }

  private implicit def serviceExceptionHandler: ExceptionHandler =
    ExceptionHandler {
      case e@ServiceNotFoundException(_) =>
        complete(Marshal(JsonThrowable(e)).to[ResponseEntity].map(res => HttpResponse(status = NotFound, entity = res)))
      case NonFatal(e) =>
        complete(Marshal(JsonThrowable(e)).to[ResponseEntity].map(res => HttpResponse(status = InternalServerError, entity = res)))
    }

  override def route(implicit user: LoggedUser): Route = {
      handleExceptions(serviceExceptionHandler) {
        invokeServicePath
      }
  }

  private def invokeServicePath(implicit user: LoggedUser) =
    path("service" / Segment / Segment) { (processingType, serviceName) =>
      post {
        val modelData = modelDataMap(processingType)
        authorize(canUserInvokeService(user, serviceName, modelData)) {
          entity(as[List[Parameter]]) { params =>
            complete {
              invokeService(serviceName, modelData, params)
            }
          }
        }
      }
    }

  private[api] def canUserInvokeService(user: LoggedUser, serviceName: String, modelData: ModelData): Boolean = {

    def hasUserDeployPermissionForServiceWithCategories(withCategories: WithCategories[_]) = {
      def isAllowed(categoryName: String): Boolean =
        user.can(categoryName, Permission.Deploy)

      withCategories.categories.exists(isAllowed)
    }

    val servicesMap = //TODO: partly dulicated with ServiceQuery.serviceMethodMap
      modelData.withThisAsContextClassLoader{
        modelData.configCreator.services(modelData.processConfig)
      }

    servicesMap.get(serviceName)
      .forall(hasUserDeployPermissionForServiceWithCategories)
  }

  private def invokeService(serviceName: String, modelData: ModelData, params: List[Parameter]): Future[QueryResult] = {
    ExpressionServiceQuery(new ServiceQuery(modelData), modelData)
      .invoke(serviceName, params)
  }
}

object ServiceRoutes {
  @JsonCodec case class JsonThrowable(className: String, message: Option[String], stacktrace: List[String])

  object JsonThrowable {
    def apply(e: Throwable):JsonThrowable =
      JsonThrowable(
        className = e.getClass.getCanonicalName,
        message = Option(e.getMessage),
        stacktrace = e.getStackTrace.toList.map(_.toString)
      )
  }

}