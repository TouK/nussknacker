package pl.touk.nussknacker.ui.server

import com.typesafe.scalalogging.LazyLogging
import sttp.monad.MonadError
import sttp.tapir.server.akkahttp.{AkkaHttpServerInterpreter, AkkaHttpServerOptions}
import sttp.tapir.server.interceptor.RequestResult
import sttp.tapir.server.interceptor.log.{DefaultServerLog, ServerLog}
import sttp.tapir.server.interceptor.reject.RejectHandler
import sttp.tapir.server.model.ValuedEndpointOutput

import scala.concurrent.{ExecutionContext, Future}

class NuAkkaHttpServerInterpreterForTapirPurposes(implicit val executionContext: ExecutionContext)
  extends AkkaHttpServerInterpreter
    with LazyLogging {

  override val akkaHttpServerOptions: AkkaHttpServerOptions =
    AkkaHttpServerOptions
      .customiseInterceptors
      .exceptionHandler(None) // todo: ???
      .serverLog(noExceptionLoggingServerLog)
      .options

  private lazy val noExceptionLoggingServerLog: ServerLog[Future] = {
    def debugLog(msg: String, exOpt: Option[Throwable]): Future[Unit] = Future.successful {
      exOpt match {
        case None => logger.debug(msg)
        case Some(ex) => logger.debug(s"$msg; exception: {}", ex)
      }
    }

    DefaultServerLog[Future](
      doLogWhenReceived = _ => Future.unit,
      doLogWhenHandled = debugLog,
      doLogAllDecodeFailures = (_, _) => Future.unit,
      doLogExceptions = (_, _) => Future.unit,
      noLog = Future.unit,
      logWhenReceived = false,
      logWhenHandled = true,
      logAllDecodeFailures = false,
      logLogicExceptions = false,
    )
  }
}
