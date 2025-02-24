package pl.touk.nussknacker.engine.requestresponse

import org.apache.pekko.event.Logging
import org.apache.pekko.http.scaladsl.model.MediaTypes.`application/json`
import org.apache.pekko.http.scaladsl.model.{HttpEntity, HttpResponse, ResponseEntity, StatusCodes}
import org.apache.pekko.http.scaladsl.server.directives.{
  AuthenticationDirective,
  Credentials,
  DebuggingDirectives,
  SecurityDirectives
}
import org.apache.pekko.http.scaladsl.server.{Directive0, Directives, Route}
import cats.data.NonEmptyList
import com.typesafe.scalalogging.LazyLogging
import io.circe.Encoder
import io.circe.generic.JsonCodec
import io.circe.syntax.EncoderOps
import pl.touk.nussknacker.engine.api.exception.NuExceptionInfo
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.requestresponse.openapi.RequestResponseOpenApiGenerator

import scala.concurrent.Future

private[requestresponse] class ScenarioRoute(
    handler: RequestResponseHttpHandler[Future],
    config: RequestResponseConfig,
    scenarioName: ProcessName
) extends Directives
    with LazyLogging {

  // Regarding https://spec.openapis.org/oas/v3.1.0#serverObject server url accept urls "relative to the location where the OpenAPI document is being served"
  // We use this relative reference instead of default '/' because runtime can by server reverse proxies (i.e. ingress controller) that can rewrite this url
  private val defaultServerUrl = "./"

  val securityDirectiveOpt: Option[AuthenticationDirective[String]] = {
    def prepareAuthenticator(basicAuthConfig: BasicAuthConfig): Credentials => Option[String] = {
      val password = basicAuthConfig.password
      val user     = basicAuthConfig.user

      def rrAuthenticator(credentials: Credentials): Option[String] =
        credentials match {
          case p @ Credentials.Provided(username) if username == user && p.verify(password) => Some(username)
          case _                                                                            => None
        }

      rrAuthenticator
    }

    val authenticationDirective = config.security.flatMap(_.basicAuth.flatMap { conf =>
      Some(SecurityDirectives.authenticateBasic(authenticator = prepareAuthenticator(conf), realm = "request-response"))
    })

    authenticationDirective
  }

  val invocationRoute: Route = {
    logDirective {
      extractExecutionContext { implicit ec =>
        extractRequest { request =>
          entity(as[Array[Byte]]) { entity =>
            complete {
              handler.invoke(request, entity).map {
                case Left(errors) =>
                  logErrors(scenarioName, errors)
                  HttpResponse(
                    status = StatusCodes.InternalServerError,
                    entity = toEntity(
                      errors.toList
                        .map(info => NuError(info.nodeComponentInfo.map(_.nodeId), Option(info.throwable.getMessage)))
                    )
                  )
                case Right(results) =>
                  HttpResponse(status = StatusCodes.OK, entity = toEntity(results))
              }
            }
          }
        }
      }
    }
  }

  val definitionRoute: Route = {
    val interpreter = handler.requestResponseInterpreter
    val openApiInfo = interpreter.generateInfoOpenApiDefinitionPart()
    val oApiJson = new RequestResponseOpenApiGenerator(config.definitionMetadata.openApiVersion, openApiInfo)
      .generateOpenApiDefinition(interpreter, config.definitionMetadata.servers, defaultServerUrl)
    val oApiJsonAsString = jsonStringToEntity(oApiJson.spaces2)

    get {
      complete {
        HttpResponse(status = StatusCodes.OK, entity = oApiJsonAsString)
      }
    }
  }

  val combinedRoute: Route = {
    val invocationRoutePath = pathEndOrSingleSlash {
      invocationRoute
    }
    val invocationRouteWithSecurity: Route = securityDirectiveOpt match {
      case Some(securityDirective) => securityDirective { _: String => invocationRoutePath }
      case None                    => invocationRoutePath
    }
    SwaggerUiRoute.route ~ path("definition") {
      definitionRoute
    } ~ invocationRouteWithSecurity
  }

  protected def logDirective: Directive0 =
    DebuggingDirectives.logRequestResult((s"request-response-$scenarioName", Logging.DebugLevel))

  private def logErrors(scenarioName: ProcessName, errors: NonEmptyList[NuExceptionInfo[_ <: Throwable]]): Unit = {
    logger.info(s"Failed to invoke: $scenarioName with errors: ${errors.map(_.throwable.getMessage)}")
    errors.toList.foreach { error =>
      logger.debug(
        s"Invocation failed $scenarioName, error in ${error.nodeComponentInfo.map(_.nodeId)}: ${error.throwable.getMessage}",
        error.throwable
      )
    }
  }

  private def toEntity[T: Encoder](value: T): ResponseEntity = jsonStringToEntity(value.asJson.noSpacesSortKeys)

  private def jsonStringToEntity(j: String): ResponseEntity = HttpEntity(contentType = `application/json`, string = j)

  @JsonCodec sealed case class NuError(nodeId: Option[String], message: Option[String])

}
