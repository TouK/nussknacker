package pl.touk.nussknacker.ui.server

import com.typesafe.scalalogging.LazyLogging
import sttp.model.StatusCode
import sttp.monad.FutureMonad
import sttp.tapir.{statusCode, stringBody, DecodeResult}
import sttp.tapir.EndpointIO.Header
import sttp.tapir.server.akkahttp.{AkkaHttpServerInterpreter, AkkaHttpServerOptions}
import sttp.tapir.server.interceptor._
import sttp.tapir.server.interceptor.decodefailure.{DecodeFailureHandler, DefaultDecodeFailureHandler}
import sttp.tapir.server.interceptor.exception.{ExceptionContext, ExceptionHandler}
import sttp.tapir.server.model.ValuedEndpointOutput

import scala.concurrent.{ExecutionContext, Future}

class NuAkkaHttpServerInterpreterForTapirPurposes(
    implicit val executionContext: ExecutionContext
) extends AkkaHttpServerInterpreter
    with LazyLogging {

  private implicit val futureMonadError: FutureMonad = new FutureMonad

  override val akkaHttpServerOptions: AkkaHttpServerOptions =
    AkkaHttpServerOptions.customiseInterceptors
      .decodeFailureHandler(customDecodeFailureHandler)
      .exceptionHandler(customExceptionHandler)
      .options

  private lazy val customDecodeFailureHandler: DecodeFailureHandler[Future] = {
    val default: DefaultDecodeFailureHandler[Future] = DefaultDecodeFailureHandler[Future]
    default.copy(
      failureMessage = { ctx: DecodeFailureContext =>
        if (isMissingAuthorizationHeaderFailure(ctx))
          "The resource requires authentication, which was not supplied with the request"
        else
          default.failureMessage(ctx)
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
