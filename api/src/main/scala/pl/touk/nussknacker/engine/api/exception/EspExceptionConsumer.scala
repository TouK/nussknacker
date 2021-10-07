package pl.touk.nussknacker.engine.api.exception

trait EspExceptionConsumer {


  def consume(exceptionInfo: EspExceptionInfo[NonTransientException]): Unit


}