package pl.touk.esp.engine.flink.api.exception

import pl.touk.esp.engine.api.exception.EspExceptionConsumer
import pl.touk.esp.engine.flink.api.RuntimeContextLifecycle

trait FlinkEspExceptionConsumer extends EspExceptionConsumer with RuntimeContextLifecycle {

  def close() : Unit = {}

}
