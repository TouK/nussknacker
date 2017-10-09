package pl.touk.nussknacker.engine.standalone.http


import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.{Directives, Route}
import argonaut.Json
import cats.data.NonEmptyList
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.Displayable
import pl.touk.nussknacker.engine.api.exception.EspExceptionInfo
import pl.touk.nussknacker.engine.standalone.StandaloneProcessInterpreter
import pl.touk.nussknacker.engine.standalone.StandaloneProcessInterpreter.GenericResultType
import pl.touk.nussknacker.engine.standalone.management.DeploymentService
import pl.touk.http.argonaut.Argonaut62Support
import pl.touk.nussknacker.engine.ModelData

import scala.concurrent.ExecutionContext

class ProcessRoute(deploymentService: DeploymentService)  extends Directives with Argonaut62Support with LazyLogging {

  import argonaut.ArgonautShapeless._

  def route(implicit ec: ExecutionContext): Route =
    path(Segment) { processPath =>
      post {
        entity(as[Array[Byte]]) { bytes =>
          val interpreter = deploymentService.getInterpreterByPath(processPath)
          interpreter match {
            case None =>
              complete {
                HttpResponse(status = StatusCodes.NotFound)
              }
            case Some(processInterpreter) =>
              complete {
                processInterpreter.invoke(bytes).map(toResponse)
              }
          }
        }
      }
  }


  //TODO: is it ok?
  def toResponse(either: GenericResultType[Json, EspExceptionInfo[_<:Throwable]]) : ToResponseMarshallable = {
    either.left.map(_.map(info => EspError(info.nodeId, info.throwable.getMessage)))
  }

  case class EspError(nodeId: Option[String], message: String)

}
