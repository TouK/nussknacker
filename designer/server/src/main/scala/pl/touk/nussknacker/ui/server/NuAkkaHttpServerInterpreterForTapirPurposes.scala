package pl.touk.nussknacker.ui.server

import com.typesafe.scalalogging.LazyLogging
import sttp.tapir.DecodeResult
import sttp.tapir.EndpointIO.Header
import sttp.tapir.server.akkahttp.{AkkaHttpServerInterpreter, AkkaHttpServerOptions}
import sttp.tapir.server.interceptor.DecodeFailureContext
import sttp.tapir.server.interceptor.decodefailure.DefaultDecodeFailureHandler

import scala.concurrent.ExecutionContext

class NuAkkaHttpServerInterpreterForTapirPurposes(implicit val executionContext: ExecutionContext)
  extends AkkaHttpServerInterpreter
    with LazyLogging {

  override val akkaHttpServerOptions: AkkaHttpServerOptions =
    AkkaHttpServerOptions
      .customiseInterceptors
//      .customiseInterceptors.corsInterceptor(CORSInterceptor.customOrThrow[Future](CORSConfig.default)) // todo:
      .decodeFailureHandler(customDecodeFailureHandler)
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
      case _ => false
    }
  }
}
