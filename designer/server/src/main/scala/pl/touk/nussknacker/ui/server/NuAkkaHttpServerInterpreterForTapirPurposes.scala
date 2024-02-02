package pl.touk.nussknacker.ui.server

import akka.http.javadsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.model.HttpHeader
import akka.http.scaladsl.model.headers.{Authorization, BasicHttpCredentials, RawHeader}
import akka.http.scaladsl.server.{Route, RouteResult}
import com.typesafe.scalalogging.LazyLogging
import sttp.capabilities
import sttp.capabilities.akka.AkkaStreams
import sttp.model.StatusCode
import sttp.monad.MonadError
import sttp.tapir.EndpointIO.Header
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.akkahttp.{AkkaHttpServerInterpreter, AkkaHttpServerOptions}
import sttp.tapir.server.interceptor._
import sttp.tapir.server.interceptor.decodefailure.{DecodeFailureHandler, DefaultDecodeFailureHandler}
import sttp.tapir.server.interceptor.exception.{ExceptionContext, ExceptionHandler}
import sttp.tapir.server.interpreter.BodyListener
import sttp.tapir.server.model.{ServerResponse, ValuedEndpointOutput}
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
            val headers = complete.response.headers.map(_.name())
            if (complete.response.status.intValue() == 401 && headers.contains("WWW-Authenticate")) {
              route
                .apply {
                  val newContext = context
                    .mapRequest(r =>
                      r.mapHeaders(
                        _ ++ Seq(
                          Authorization(akka.http.scaladsl.model.headers.BasicHttpCredentials("anonymous", "anonymous"))
                        )
                      )
                    )
                  newContext
                }
                .map {
                  case complete @ RouteResult.Complete(_) =>
                    complete
                  case rejected @ RouteResult.Rejected(_) =>
                    rejected
                }
            } else {
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

  private val interceptor = new EndpointInterceptor[Future] {

    override def apply[B](
        responder: Responder[Future, B],
        endpointHandler: EndpointHandler[Future, B]
    ): EndpointHandler[Future, B] = {
      new EndpointHandler[Future, B] {
        override def onDecodeSuccess[A, U, I](
            ctx: DecodeSuccessContext[Future, A, U, I]
        )(implicit monad: MonadError[Future], bodyListener: BodyListener[Future, B]): Future[ServerResponse[B]] =
          endpointHandler.onDecodeSuccess(ctx)

        override def onSecurityFailure[A](
            ctx: SecurityFailureContext[Future, A]
        )(implicit monad: MonadError[Future], bodyListener: BodyListener[Future, B]): Future[ServerResponse[B]] =
          endpointHandler.onSecurityFailure(ctx)

        override def onDecodeFailure(ctx: DecodeFailureContext)(
            implicit monad: MonadError[Future],
            bodyListener: BodyListener[Future, B]
        ): Future[Option[ServerResponse[B]]] =
          endpointHandler.onDecodeFailure(ctx)
      }
    }

  }

  override val akkaHttpServerOptions: AkkaHttpServerOptions =
    AkkaHttpServerOptions.customiseInterceptors
      .decodeFailureHandler(customDecodeFailureHandler)
      .exceptionHandler(customExceptionHandler)
      .options

  private lazy val decodeFailureHandler = new DecodeFailureHandler {
    override def apply(ctx: DecodeFailureContext): Option[ValuedEndpointOutput[_]] = ???
  }

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
