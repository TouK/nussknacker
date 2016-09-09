package pl.touk.esp.engine.api.exception

import org.apache.flink.api.common.functions.RuntimeContext
import org.apache.flink.api.common.restartstrategy.RestartStrategies.RestartStrategyConfiguration
import pl.touk.esp.engine.api.{Context, MetaData}

trait EspExceptionHandler extends Serializable {

  final def recover[T](block: => T)(context: Context, processMetaData: MetaData): Option[T] = {
    try {
      Some(block)
    } catch {
      case ex: Throwable =>
        this.handle(EspExceptionInfo(ex, context, processMetaData))
        None
    }
  }

  def restartStrategy: RestartStrategyConfiguration

  def open(runtimeContext: RuntimeContext): Unit = {}
  protected def handle(exceptionInfo: EspExceptionInfo[Throwable]): Unit
  def close(): Unit = {}
}

case class EspExceptionInfo[T <: Throwable](throwable: T, context: Context, processMetaData: MetaData) extends Serializable