package pl.touk.nussknacker.ui.server

import com.typesafe.scalalogging.LazyLogging
import sttp.tapir.server.akkahttp.{AkkaHttpServerInterpreter, AkkaHttpServerOptions}

import scala.concurrent.ExecutionContext

class NuAkkaHttpServerInterpreterForTapirPurposes(implicit val executionContext: ExecutionContext)
  extends AkkaHttpServerInterpreter
    with LazyLogging {

  override val akkaHttpServerOptions: AkkaHttpServerOptions =
    AkkaHttpServerOptions
      .customiseInterceptors
      .exceptionHandler(None)
      .options
}
