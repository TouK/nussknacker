package pl.touk.nussknacker.ui.server

import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.headers.{Authorization, BasicHttpCredentials, `WWW-Authenticate`}
import akka.http.scaladsl.server.{RequestContext, Route, RouteResult}
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.security.AuthCredentials.AnonymousAccess
import pl.touk.nussknacker.security.AesCrypter
import sttp.capabilities
import sttp.capabilities.akka.AkkaStreams
import sttp.model.StatusCode
import sttp.monad.MonadError
import sttp.tapir.EndpointIO.Header
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.akkahttp.{AkkaHttpServerInterpreter, AkkaHttpServerOptions}
import sttp.tapir.server.interceptor._
import sttp.tapir.server.interceptor.decodefailure.DefaultDecodeFailureHandler
import sttp.tapir.server.interceptor.exception.{ExceptionContext, ExceptionHandler}
import sttp.tapir.server.interceptor.reject.RejectHandler
import sttp.tapir.server.model.ValuedEndpointOutput
import sttp.tapir.{DecodeResult, statusCode, stringBody}

import scala.concurrent.{ExecutionContext, Future}

class NuAkkaHttpServerInterpreterForTapirPurposes(anonymousAccessEnabled: Boolean)(
    implicit val executionContext: ExecutionContext
) extends AkkaHttpServerInterpreter
    with LazyLogging {

  override def toRoute(ses: List[ServerEndpoint[AkkaStreams with capabilities.WebSockets, Future]]): Route = {
    if (anonymousAccessEnabled) {
      super.toRoute(ses) // .withAnonymousAccessFallbackOnMissingCredentials()
    } else {
      super.toRoute(ses)
    }
  }

  override def toRoute(se: ServerEndpoint[AkkaStreams with capabilities.WebSockets, Future]): Route =
    toRoute(se :: Nil)

  override val akkaHttpServerOptions: AkkaHttpServerOptions =
    AkkaHttpServerOptions.customiseInterceptors
      .decodeFailureHandler(customDecodeFailureHandler)
      .exceptionHandler(customExceptionHandler)
      .rejectHandler(new RejectHandler[Future] {

        override def apply(
            failure: RequestResult.Failure
        )(implicit monad: MonadError[Future]): Future[Option[ValuedEndpointOutput[_]]] = ???

      })
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

  private implicit class WithAnonymousAccessFallback(val route: Route) {

    def withAnonymousAccessFallbackOnMissingCredentials(): Route = { context: RequestContext =>
      route
        .apply(context)
        .flatMap {
          case complete @ RouteResult.Complete(_) if isMissingAuthHeader(complete.response) =>
            tryOnceAgainAsAnonymousUser(route, complete).apply(context)
          case complete @ RouteResult.Complete(_) =>
            Future.successful(complete)
          case rejected @ RouteResult.Rejected(_) =>
            Future.successful(rejected)
        }
    }

    private def isMissingAuthHeader(response: HttpResponse) = {
      val wwwAuthHeaderOpt = response.headers.find {
        case `WWW-Authenticate`(_) => true
        case _                     => false
      }
      wwwAuthHeaderOpt match {
        case Some(_) if response.status.intValue() == 401 => true
        case Some(_) | None                               => false
      }
    }

    private def tryOnceAgainAsAnonymousUser(route: Route, originCompleteResponse: RouteResult.Complete) = {
      context: RequestContext =>
        route
          .apply(withAnonymousCredentialsAuthorization(context))
          .map {
            case complete @ RouteResult.Complete(_) if complete.response.status.intValue() == 401 =>
              originCompleteResponse
            case complete @ RouteResult.Complete(_) =>
              complete
            case rejected @ RouteResult.Rejected(_) =>
              rejected
          }
    }

    private def withAnonymousCredentialsAuthorization(context: RequestContext) = {
      context.mapRequest(
        _.mapHeaders(
          _ ++ Seq(
            Authorization(BasicHttpCredentials(AnonymousAccess.stringify(AesCrypter)))
          )
        )
      )
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
