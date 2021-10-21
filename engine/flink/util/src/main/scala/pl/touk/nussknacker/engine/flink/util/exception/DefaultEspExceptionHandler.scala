package pl.touk.nussknacker.engine.flink.util.exception

import com.typesafe.config.Config
import org.apache.flink.api.common.functions.RuntimeContext
import org.apache.flink.api.common.restartstrategy.RestartStrategies
import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.api.exception.EspExceptionInfo
import pl.touk.nussknacker.engine.flink.api.exception.{FlinkEspExceptionConsumer, FlinkEspExceptionHandler}
import pl.touk.nussknacker.engine.util.exception.WithExceptionExtractor

import scala.concurrent.duration._

@deprecated("use ConfigurableExceptionHandlerFactory", "0.4.0")
case class BrieflyLoggingExceptionHandler(processMetaData: MetaData, params: Map[String, String] = Map.empty)
  extends FlinkEspExceptionHandler
    with ConsumingNonTransientExceptions
    with NotRestartingProcesses {

  override protected val consumer = new RateMeterExceptionConsumer(BrieflyLoggingExceptionConsumer(processMetaData, params))

}
@deprecated("use ConfigurableExceptionHandlerFactory", "0.4.0")
case class BrieflyLoggingRestartingExceptionHandler(processMetaData: MetaData, config: Config, params: Map[String, String] = Map.empty)
  extends FlinkEspExceptionHandler
    with ConsumingNonTransientExceptions
    with RestartingProcessAfterDelay {

  override protected val consumer = BrieflyLoggingExceptionConsumer(processMetaData, params)

}

@deprecated("use ConfigurableExceptionHandlerFactory", "0.4.0")
case class VerboselyLoggingExceptionHandler(processMetaData: MetaData, params: Map[String, String] = Map.empty)
  extends FlinkEspExceptionHandler
    with ConsumingNonTransientExceptions
    with NotRestartingProcesses {
  override protected val consumer = new RateMeterExceptionConsumer(VerboselyLoggingExceptionConsumer(processMetaData, params))
}

@deprecated("use ConfigurableExceptionHandlerFactory", "0.4.0")
case class VerboselyLoggingRestartingExceptionHandler(processMetaData: MetaData, config: Config, params: Map[String, String] = Map.empty)
  extends FlinkEspExceptionHandler
    with RestartingProcessAfterDelay
    with ConsumingNonTransientExceptions {
  override protected val consumer = VerboselyLoggingExceptionConsumer(processMetaData, params)
}


trait ConsumingNonTransientExceptions extends FlinkEspExceptionHandler with WithExceptionExtractor {

  override def open(runtimeContext: RuntimeContext): Unit = {
    consumer.open(runtimeContext)
  }

  override def close(): Unit = {
    consumer.close()
  }

  override def handle(exceptionInfo: EspExceptionInfo[_ <: Throwable]): Unit = {
    consumer.consume(extractOrThrow(exceptionInfo))
  }

  protected def consumer: FlinkEspExceptionConsumer

}

@deprecated("use ConfigurableExceptionHandlerFactory", "0.4.0")
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

@deprecated("use ConfigurableExceptionHandlerFactory", "0.4.0")
trait NotRestartingProcesses {
  self: FlinkEspExceptionHandler =>

  override def restartStrategy = RestartStrategies.noRestart()

}