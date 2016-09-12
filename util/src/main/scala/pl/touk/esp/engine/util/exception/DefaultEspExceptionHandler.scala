package pl.touk.esp.engine.util.exception

import java.net.ConnectException

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.functions.RuntimeContext
import org.apache.flink.api.common.restartstrategy.RestartStrategies
import pl.touk.esp.engine.api.MetaData
import pl.touk.esp.engine.api.exception.{EspExceptionConsumer, EspExceptionHandler, EspExceptionInfo, NonTransientException}
import pl.touk.esp.engine.util.exception.DefaultEspExceptionHandler.{DefaultNonTransientExceptionExtractor, DefaultTransientExceptionExtractor}

import scala.concurrent.duration._

trait DefaultEspExceptionHandler
  extends EspExceptionHandler
    with ConsumingNonTransientExceptions
    with RestartingProcessAfterDelay

object DefaultEspExceptionHandler {

  object DefaultTransientExceptionExtractor
    extends DeeplyCheckingExceptionExtractor({case _: ConnectException =>})

  object DefaultNonTransientExceptionExtractor
    extends DeeplyCheckingExceptionExtractor({case a: NonTransientException => a})

}

case class BrieflyLoggingExceptionHandler(processMetaData: MetaData)
  extends EspExceptionHandler
    with ConsumingNonTransientExceptions
    with NotRestartingProcesses
    with LazyLogging {

  override protected def consumer = new EspExceptionConsumer {
    override def consume(e: EspExceptionInfo[NonTransientException]) = {
      logger.warn(s"${processMetaData.id}: Exception: ${e.throwable.getMessage} (${e.throwable.getClass.getName})")
    }
  }

}

case class VerboselyLoggingExceptionHandler(processMetaData: MetaData)
  extends EspExceptionHandler
    with ConsumingNonTransientExceptions
    with NotRestartingProcesses
    with LazyLogging {

  override protected def consumer = new EspExceptionConsumer {
    override def consume(e: EspExceptionInfo[NonTransientException]) = {
      logger.error(s"${processMetaData.id}: Exception during processing job, context: ${e.context}", e.throwable)
    }
  }
  
}

trait ConsumingNonTransientExceptions { self: EspExceptionHandler =>

  protected def consumer: EspExceptionConsumer

  override def open(runtimeContext: RuntimeContext) = {
    consumer.open(runtimeContext)
  }

  override protected def handle(exceptionInfo: EspExceptionInfo[Throwable]): Unit = {
    defaultHandleException(exceptionInfo)
  }

  override def close() = {
    consumer.close()
  }

  final protected def defaultHandleException(exceptionInfo: EspExceptionInfo[Throwable]) = {
    exceptionInfo.throwable match {
      case transientExceptionExtractor(_) =>
        throw exceptionInfo.throwable
      case nonTransientExceptionExtractor(nonTransient) =>
        consumer.consume(EspExceptionInfo(nonTransient, exceptionInfo.context))
      case other =>
        val nonTransient = NonTransientException(input = other.getMessage, message = "Unknown exception", cause = other)
        consumer.consume(EspExceptionInfo(nonTransient, exceptionInfo.context))
    }
  }

  protected val transientExceptionExtractor: ExceptionExtractor[Unit] =
    DefaultTransientExceptionExtractor

  protected val nonTransientExceptionExtractor: ExceptionExtractor[NonTransientException] =
    DefaultNonTransientExceptionExtractor

}

trait RestartingProcessAfterDelay { self: EspExceptionHandler =>

  import net.ceedubs.ficus.Ficus._

  protected def config: Config

  override val restartStrategy =
    RestartStrategies.fixedDelayRestart(
      Integer.MAX_VALUE,
      config.getOrElse[FiniteDuration]("delayBetweenAttempts", 10.seconds).toMillis
    )
}

trait NotRestartingProcesses { self: EspExceptionHandler =>
  
  override def restartStrategy = RestartStrategies.noRestart()
  
}