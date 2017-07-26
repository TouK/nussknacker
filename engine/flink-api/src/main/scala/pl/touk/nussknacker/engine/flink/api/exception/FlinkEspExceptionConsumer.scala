package pl.touk.nussknacker.engine.flink.api.exception

import pl.touk.nussknacker.engine.api.exception.EspExceptionConsumer
import pl.touk.nussknacker.engine.flink.api.RuntimeContextLifecycle

trait FlinkEspExceptionConsumer extends EspExceptionConsumer with RuntimeContextLifecycle {

  def close() : Unit = {}

}
