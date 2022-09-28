package pl.touk.nussknacker.engine.requestresponse

import akka.event.Logging
import akka.http.scaladsl.model.MediaTypes.`application/json`
import akka.http.scaladsl.model.headers.{CacheDirectives, `Cache-Control`}
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

import java.nio.file.Files

class ScenarioRoute(handler: RequestResponseAkkaHttpHandler, definitionConfig: OpenApiDefinitionConfig, scenarioName: ProcessName) extends Directives with LazyLogging {

  // Regarding https://spec.openapis.org/oas/v3.1.0#serverObject server url accept urls "relative to the location where the OpenAPI document is being served"
  // We use this relative reference instead of default '/' because runtime can by server reverse proxies (i.e. ingress controller) that can rewrite this url
  private val defaultServerUrl = "./"

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
    val openApiInfo = interpreter.generateInfoOpenApiDefinitionPart()
    val oApiJson = new RequestResponseOpenApiGenerator(definitionConfig.openApiVersion, openApiInfo).generateOpenApiDefinition(interpreter, definitionConfig.servers, defaultServerUrl)
    val oApiJsonAsString = jsonStringToEntity(oApiJson.spaces2)

    get {
      complete {
        HttpResponse(status = StatusCodes.OK, entity = oApiJsonAsString)
      }
    }
  }

  val combinedRoute: Route = {
    SwaggerUiRoute.route ~ path("definition") {
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
