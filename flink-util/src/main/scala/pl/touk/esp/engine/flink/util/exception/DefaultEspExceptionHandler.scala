package pl.touk.esp.engine.flink.util.exception

import java.net.ConnectException

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.functions.RuntimeContext
import org.apache.flink.api.common.restartstrategy.RestartStrategies
import org.apache.flink.api.common.restartstrategy.RestartStrategies.FailureRateRestartStrategyConfiguration
import org.apache.flink.api.common.time.Time
import org.apache.flink.runtime.executiongraph.restart.FailureRateRestartStrategy
import org.apache.flink.runtime.executiongraph.restart.FailureRateRestartStrategy.FailureRateRestartStrategyFactory
import pl.touk.esp.engine.api.MetaData
import pl.touk.esp.engine.api.exception.{EspExceptionInfo, NonTransientException}
import pl.touk.esp.engine.flink.api.exception.{FlinkEspExceptionConsumer, FlinkEspExceptionHandler}
import pl.touk.esp.engine.flink.util.exception.DefaultEspExceptionHandler.{DefaultNonTransientExceptionExtractor, DefaultTransientExceptionExtractor}

import scala.concurrent.duration._

trait DefaultEspExceptionHandler
  extends FlinkEspExceptionHandler
    with ConsumingNonTransientExceptions
    with RestartingProcessAfterDelay

object DefaultEspExceptionHandler {

  object DefaultTransientExceptionExtractor
    extends DeeplyCheckingExceptionExtractor({ case _: ConnectException => })

  object DefaultNonTransientExceptionExtractor
    extends DeeplyCheckingExceptionExtractor({ case a: NonTransientException => a })

}

case class BrieflyLoggingExceptionHandler(processMetaData: MetaData)
  extends FlinkEspExceptionHandler
    with ConsumingNonTransientExceptions
    with NotRestartingProcesses
    with LazyLogging {

  override protected def consumer = new FlinkEspExceptionConsumer {
    override def consume(e: EspExceptionInfo[NonTransientException]) = {
      logger.warn(s"${processMetaData.id}: Exception: ${e.throwable.getMessage} (${e.throwable.getClass.getName})")
    }
  }

}

case class VerboselyLoggingExceptionHandler(processMetaData: MetaData)
  extends FlinkEspExceptionHandler
    with ConsumingNonTransientExceptions
    with NotRestartingProcesses {
  override protected def consumer = VerboselyLoggingExceptionConsumer(processMetaData)

}

case class VerboselyLoggingRestartingExceptionHandler(processMetaData: MetaData)
  extends FlinkEspExceptionHandler
    with ConsumingNonTransientExceptions {
  override protected def consumer = VerboselyLoggingExceptionConsumer(processMetaData)

  override def restartStrategy = new FailureRateRestartStrategyConfiguration(10, Time.seconds(1), Time.seconds(1))
}

case class VerboselyLoggingExceptionConsumer(processMetaData: MetaData) extends FlinkEspExceptionConsumer with LazyLogging {
  override def consume(e: EspExceptionInfo[NonTransientException]) = {
    logger.error(s"${processMetaData.id}: Exception during processing job, context: ${e.context}", e.throwable)
  }
}

trait ConsumingNonTransientExceptions extends LazyLogging {
  self: FlinkEspExceptionHandler =>

  protected val transientExceptionExtractor: ExceptionExtractor[Unit] =
    DefaultTransientExceptionExtractor
  protected val nonTransientExceptionExtractor: ExceptionExtractor[NonTransientException] =
    DefaultNonTransientExceptionExtractor

  override def open(runtimeContext: RuntimeContext) = {
    consumer.open(runtimeContext)
  }

  override def handle(exceptionInfo: EspExceptionInfo[_ <: Throwable]): Unit = {
    defaultHandleException(exceptionInfo)
  }

  final protected def defaultHandleException(exceptionInfo: EspExceptionInfo[_ <: Throwable]) = {
    exceptionInfo.throwable match {
      case transientExceptionExtractor(_) =>
        throw exceptionInfo.throwable
      case nonTransientExceptionExtractor(nonTransient) =>
        consumer.consume(EspExceptionInfo(exceptionInfo.nodeId, nonTransient, exceptionInfo.context))
      case other =>
        val nonTransient = NonTransientException(input = other.getMessage, message = "Unknown exception", cause = other)
        logger.warn(s"Unknown exception ${other.getMessage} for ${exceptionInfo.context}", other)
        consumer.consume(EspExceptionInfo(exceptionInfo.nodeId, nonTransient, exceptionInfo.context))
    }
  }

  override def close() = {
    consumer.close()
  }

  protected def consumer: FlinkEspExceptionConsumer

}

trait RestartingProcessAfterDelay {
  self: FlinkEspExceptionHandler =>

  import net.ceedubs.ficus.Ficus._

  override val restartStrategy =
    RestartStrategies.fixedDelayRestart(
      Integer.MAX_VALUE,
      config.getOrElse[FiniteDuration]("delayBetweenAttempts", 10.seconds).toMillis
    )

  protected def config: Config
}

trait NotRestartingProcesses {
  self: FlinkEspExceptionHandler =>

  override def restartStrategy = RestartStrategies.noRestart()

}