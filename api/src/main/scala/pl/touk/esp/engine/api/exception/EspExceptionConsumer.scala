package pl.touk.esp.engine.api.exception

import org.apache.flink.api.common.functions.RuntimeContext

trait EspExceptionConsumer {

  def open(runtimeContext: RuntimeContext): Unit = {}

  def consume(exceptionInfo: EspExceptionInfo[NonTransientException]): Unit

  def close(): Unit = {}

}