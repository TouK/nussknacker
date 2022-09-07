package pl.touk.nussknacker.engine.requestresponse

import akka.event.Logging
import akka.http.scaladsl.model.MediaTypes.`application/json`
import akka.http.scaladsl.model.{HttpEntity, HttpResponse, ResponseEntity, StatusCodes}
import akka.http.scaladsl.server.directives.DebuggingDirectives
import akka.http.scaladsl.server.{Directive0, Directives, Route}
import cats.data.NonEmptyList
import com.typesafe.scalalogging.LazyLogging
import io.circe.Encoder
import io.circe.generic.JsonCodec
import io.circe.syntax.EncoderOps
import pl.touk.nussknacker.engine.api.exception.NuExceptionInfo
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.requestresponse.openapi.RequestResponseOpenApiGenerator

class ScenarioRoute(handler: RequestResponseAkkaHttpHandler, definitionConfig: OpenApiDefinitionConfig, scenarioName: ProcessName, exposedPath: String) extends Directives with LazyLogging {

  val invocationRoute: Route = {
    logDirective {
      handler.invoke {
        case Left(errors) => complete {
          logErrors(scenarioName, errors)
          HttpResponse(status = StatusCodes.InternalServerError, entity = toEntity(errors.toList.map(info => NuError(info.nodeComponentInfo.map(_.nodeId), Option(info.throwable.getMessage)))))
        }
        case Right(results) => complete {
          HttpResponse(status = StatusCodes.OK, entity = toEntity(results))
        }
      }
    }
  }

  val definitionRoute: Route = {
    val interpreter = handler.requestResponseInterpreter
    val oApiJson = RequestResponseOpenApiGenerator
      .generateOpenApi(List((exposedPath, interpreter)), interpreter.generateOpenApiInfoForScenario(), definitionConfig.server)

    get {
      complete {
        HttpResponse(status = StatusCodes.OK, entity = jsonStringToEntity(oApiJson))
      }
    }
  }

  val combinedRoute: Route = {
    path("definition") {
      definitionRoute
    } ~ {
      pathEndOrSingleSlash {
        invocationRoute
      }
    }
  }

  protected def logDirective: Directive0 = DebuggingDirectives.logRequestResult((s"request-response-$scenarioName", Logging.DebugLevel))

  private def logErrors(scenarioName: ProcessName, errors: NonEmptyList[NuExceptionInfo[_ <: Throwable]]): Unit = {
    logger.info(s"Failed to invoke: $scenarioName with errors: ${errors.map(_.throwable.getMessage)}")
    errors.toList.foreach { error =>
      logger.debug(s"Invocation failed $scenarioName, error in ${error.nodeComponentInfo.map(_.nodeId)}: ${error.throwable.getMessage}", error.throwable)
    }
  }

  private def toEntity[T: Encoder](value: T): ResponseEntity = jsonStringToEntity(value.asJson.noSpacesSortKeys)

  private def jsonStringToEntity(j: String): ResponseEntity = HttpEntity(contentType = `application/json`, string = j)

  @JsonCodec case class NuError(nodeId: Option[String], message: Option[String])

}
