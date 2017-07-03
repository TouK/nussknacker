package pl.touk.esp.engine.api.exception

trait EspExceptionConsumer {


  def consume(exceptionInfo: EspExceptionInfo[NonTransientException]): Unit


}