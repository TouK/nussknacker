package pl.touk.nussknacker.ui.server

import akka.http.scaladsl.model.headers.{Authorization, BasicHttpCredentials, `WWW-Authenticate`}
import akka.http.scaladsl.server.{Route, RouteResult}
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.security.AuthCredentials.AnonymousAccess
import pl.touk.nussknacker.security.Base64Crypter
import sttp.capabilities
import sttp.capabilities.akka.AkkaStreams
import sttp.model.StatusCode
import sttp.tapir.EndpointIO.Header
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.akkahttp.{AkkaHttpServerInterpreter, AkkaHttpServerOptions}
import sttp.tapir.server.interceptor._
import sttp.tapir.server.interceptor.decodefailure.DefaultDecodeFailureHandler
import sttp.tapir.server.interceptor.exception.{ExceptionContext, ExceptionHandler}
import sttp.tapir.server.model.ValuedEndpointOutput
import sttp.tapir.{DecodeResult, statusCode, stringBody}

import scala.concurrent.{ExecutionContext, Future}

class NuAkkaHttpServerInterpreterForTapirPurposes(implicit val executionContext: ExecutionContext)
    extends AkkaHttpServerInterpreter
    with LazyLogging {

  override def toRoute(ses: List[ServerEndpoint[AkkaStreams with capabilities.WebSockets, Future]]): Route = {
    context =>
      val route = super.toRoute(ses)
      route
        .apply(context)
        .flatMap {
          case complete @ RouteResult.Complete(_) =>
            val wwwAuthHeaderOpt = complete.response.headers.find {
              case `WWW-Authenticate`(_) => true
              case _                     => false
            }

            wwwAuthHeaderOpt match {
              case Some(_) if complete.response.status.intValue() == 401 =>
                route
                  .apply {
                    val newContext = context
                      .mapRequest(r =>
                        r.mapHeaders(
                          _ ++ Seq(
                            Authorization(BasicHttpCredentials(AnonymousAccess.stringify(Base64Crypter)))
                          )
                        )
                      )
                    newContext
                  }
                  .map {
                    case c @ RouteResult.Complete(_) if c.response.status.intValue() == 401 =>
                      complete
                    case c @ RouteResult.Complete(_) =>
                      c
                    case rejected @ RouteResult.Rejected(_) =>
                      rejected
                  }
              case Some(_) | None =>
                Future.successful(complete)
            }
          case rejected @ RouteResult.Rejected(_) =>
            Future.successful(rejected)
        }
  }

  override def toRoute(se: ServerEndpoint[AkkaStreams with capabilities.WebSockets, Future]): Route = { context =>
    super
      .toRoute(se)
      .apply(context)
      .map {
        case complete @ RouteResult.Complete(_) =>
          complete
        case rejected @ RouteResult.Rejected(_) =>
          rejected
      }
  }

  override val akkaHttpServerOptions: AkkaHttpServerOptions =
    AkkaHttpServerOptions.customiseInterceptors
      .decodeFailureHandler(customDecodeFailureHandler)
      .exceptionHandler(customExceptionHandler)
      .options

  private lazy val customDecodeFailureHandler = {
    DefaultDecodeFailureHandler.default.copy(
      failureMessage = ctx => {
        if (isMissingAuthorizationHeaderFailure(ctx))
          "The resource requires authentication, which was not supplied with the request"
        else
          DefaultDecodeFailureHandler.default.failureMessage(ctx)
      }
    )
  }

  private def isMissingAuthorizationHeaderFailure(ctx: DecodeFailureContext) = {
    (ctx.failingInput, ctx.failure) match {
      case (Header("Authorization", _, _), DecodeResult.Missing) => true
      case _                                                     => false
    }
  }

  private lazy val customExceptionHandler = ExceptionHandler.pure[Future] { ctx: ExceptionContext =>
    Some(
      ValuedEndpointOutput(
        statusCode.and(stringBody),
        (
          StatusCode.InternalServerError,
          ctx.e.getMessage
        )
      )
    )
  }

}
