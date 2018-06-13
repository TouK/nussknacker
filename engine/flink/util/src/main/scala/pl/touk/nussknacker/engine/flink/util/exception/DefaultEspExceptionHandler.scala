package pl.touk.nussknacker.engine.flink.util.exception

import java.net.ConnectException

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.functions.RuntimeContext
import org.apache.flink.api.common.restartstrategy.RestartStrategies
import org.apache.flink.api.common.restartstrategy.RestartStrategies.FailureRateRestartStrategyConfiguration
import org.apache.flink.api.common.time.Time
import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.api.exception.{EspExceptionInfo, NonTransientException}
import pl.touk.nussknacker.engine.flink.api.exception.{FlinkEspExceptionConsumer, FlinkEspExceptionHandler}
import pl.touk.nussknacker.engine.flink.util.exception.DefaultEspExceptionHandler.{DefaultNonTransientExceptionExtractor, DefaultTransientExceptionExtractor}
import pl.touk.nussknacker.engine.util.ReflectUtils

import scala.concurrent.duration._

trait DefaultEspExceptionHandler
  extends FlinkEspExceptionHandler
    with ConsumingNonTransientExceptions
    with RestartingProcessAfterDelay

object DefaultEspExceptionHandler {

  object DefaultTransientExceptionExtractor
    extends DeeplyCheckingExceptionExtractor({ case a: ConnectException => a: Exception })

  object DefaultNonTransientExceptionExtractor
    extends DeeplyCheckingExceptionExtractor({ case a: NonTransientException => a })

}

case class BrieflyLoggingExceptionHandler(processMetaData: MetaData)
  extends FlinkEspExceptionHandler
    with ConsumingNonTransientExceptions
    with NotRestartingProcesses
    with LazyLogging {

  override protected val consumer = new FlinkEspExceptionConsumer {
    override def consume(e: EspExceptionInfo[NonTransientException]) = {
      logger.warn(s"${processMetaData.id}: Exception: ${e.throwable.getMessage} (${e.throwable.getClass.getName})")
    }
  }

}

case class VerboselyLoggingExceptionHandler(processMetaData: MetaData, params: Map[String, String] = Map.empty)
  extends FlinkEspExceptionHandler
    with ConsumingNonTransientExceptions
    with NotRestartingProcesses {
  override protected val consumer = new RateMeterExceptionConsumer(VerboselyLoggingExceptionConsumer(processMetaData, params))
}

case class VerboselyLoggingRestartingExceptionHandler(processMetaData: MetaData, config: Config, params: Map[String, String] = Map.empty)
  extends FlinkEspExceptionHandler
    with RestartingProcessAfterDelay
    with ConsumingNonTransientExceptions {
  override protected val consumer = VerboselyLoggingExceptionConsumer(processMetaData, params)
}

case class VerboselyLoggingExceptionConsumer(processMetaData: MetaData, params: Map[String, String] = Map.empty)
  extends FlinkEspExceptionConsumer
    with LazyLogging {
  override def consume(e: EspExceptionInfo[NonTransientException]) = {
    logger.error(s"${processMetaData.id}: Exception during processing job, params: $params, context: ${e.context}", e.throwable)
  }
}

trait ConsumingNonTransientExceptions extends LazyLogging {
  self: FlinkEspExceptionHandler =>

  protected val transientExceptionExtractor: ExceptionExtractor[Exception] =
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
        val exceptionDetails = s"${ReflectUtils.fixedClassSimpleNameWithoutParentModule(other.getClass)}:${other.getMessage}"
        val nonTransient = NonTransientException(input = exceptionDetails, message = "Unknown exception", cause = other)
        logger.warn(s"Unknown exception $exceptionDetails for ${exceptionInfo.context}", other)
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