package pl.touk.nussknacker.engine.util.exception

import pl.touk.nussknacker.engine.api.exception.{EspExceptionConsumer, EspExceptionInfo, NonTransientException}

trait GenericRateMeterExceptionConsumer extends EspExceptionConsumer with ExceptionRateMeter {

  def underlying: EspExceptionConsumer

  override def consume(exceptionInfo: EspExceptionInfo[NonTransientException]): Unit = {
    try {
      underlying.consume(exceptionInfo)
    } finally {
      markException(exceptionInfo)
    }
  }

}
