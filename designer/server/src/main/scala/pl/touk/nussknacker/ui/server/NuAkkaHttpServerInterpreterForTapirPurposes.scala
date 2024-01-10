package pl.touk.nussknacker.ui.server

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.ui.api.NuDesignerErrorToHttp
import sttp.model.StatusCode
import sttp.tapir.{DecodeResult, statusCode, stringBody}
import sttp.tapir.EndpointIO.Header
import sttp.tapir.server.akkahttp.{AkkaHttpServerInterpreter, AkkaHttpServerOptions}
import sttp.tapir.server.interceptor.DecodeFailureContext
import sttp.tapir.server.interceptor.decodefailure.DefaultDecodeFailureHandler
import sttp.tapir.server.interceptor.exception.{ExceptionContext, ExceptionHandler}
import sttp.tapir.server.model.ValuedEndpointOutput

import scala.concurrent.{ExecutionContext, Future}

class NuAkkaHttpServerInterpreterForTapirPurposes(implicit val executionContext: ExecutionContext)
    extends AkkaHttpServerInterpreter
    with LazyLogging {

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
    val statusAndMessage = NuDesignerErrorToHttp.errorToStatusAndMessage(ctx.e)
    Some(
      ValuedEndpointOutput(
        statusCode.and(stringBody),
        (
          StatusCode.apply(statusAndMessage._1.intValue()),
          statusAndMessage._2
        )
      )
    )
  }

}
