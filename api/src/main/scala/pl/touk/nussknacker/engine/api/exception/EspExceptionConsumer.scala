package pl.touk.nussknacker.engine.api.exception

import pl.touk.nussknacker.engine.api.Lifecycle

trait EspExceptionConsumer extends Lifecycle {

  def consume(exceptionInfo: EspExceptionInfo[NonTransientException]): Unit


}