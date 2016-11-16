package pl.touk.esp.engine.flink.api.exception

import org.apache.flink.api.common.functions.RuntimeContext
import pl.touk.esp.engine.api.exception.EspExceptionConsumer

trait FlinkEspExceptionConsumer extends EspExceptionConsumer {

  def open(runtimeContext: RuntimeContext): Unit = {}

  def close(): Unit = {}

}
