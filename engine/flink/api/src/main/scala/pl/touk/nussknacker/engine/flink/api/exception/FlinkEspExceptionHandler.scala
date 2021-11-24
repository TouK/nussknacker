package pl.touk.nussknacker.engine.flink.api.exception

import org.apache.flink.api.common.restartstrategy.RestartStrategies
import org.apache.flink.api.common.restartstrategy.RestartStrategies.RestartStrategyConfiguration
import pl.touk.nussknacker.engine.api.{Context, Lifecycle, ProcessListener}
import pl.touk.nussknacker.engine.api.exception.EspExceptionInfo
import pl.touk.nussknacker.engine.api.runtimecontext.EngineRuntimeContext

import scala.util.control.NonFatal

object FlinkEspExceptionHandler {

  val empty: FlinkEspExceptionHandler = new FlinkEspExceptionHandler {
    override def restartStrategy: RestartStrategyConfiguration = RestartStrategies.noRestart()
    override def handle(exceptionInfo: EspExceptionInfo[_ <: Throwable]): Unit = ()
  }
}

trait FlinkEspExceptionHandler extends Lifecycle{
  
  /**
    * Be aware that it is invoked prior to opening exception handler.
    */
  def restartStrategy: RestartStrategyConfiguration

  def handle(exceptionInfo: EspExceptionInfo[_ <: Throwable]): Unit

  def handling[T](nodeId: Option[String], context: Context)(action: => T): Option[T] =
    try {
      Some(action)
    } catch {
      case NonFatal(e) => handle(EspExceptionInfo(nodeId, e, context))
        None
    }
}

abstract class DelegatingFlinkEspExceptionHandler(protected val delegate: FlinkEspExceptionHandler) extends FlinkEspExceptionHandler {

  override def open(context: EngineRuntimeContext): Unit = delegate.open(context)

  override def close(): Unit = delegate.close()

  override def restartStrategy: RestartStrategyConfiguration = delegate.restartStrategy
}

class ListeningExceptionHandler(listeners: Seq[ProcessListener], exceptionHandler: FlinkEspExceptionHandler)
  extends DelegatingFlinkEspExceptionHandler(exceptionHandler) {

  override def handle(exceptionInfo: EspExceptionInfo[_ <: Throwable]): Unit = {
    listeners.foreach(_.exceptionThrown(exceptionInfo))
    delegate.handle(exceptionInfo)
  }
}
