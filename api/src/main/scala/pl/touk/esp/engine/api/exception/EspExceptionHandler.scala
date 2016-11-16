package pl.touk.esp.engine.api.exception

import pl.touk.esp.engine.api.{Context, MetaData}

trait EspExceptionHandler {

  final def recover[T](block: => T)(context: => Context): Option[T] = {
    try {
      Some(block)
    } catch {
      case ex: Throwable =>
        this.handle(EspExceptionInfo(ex, context))
        None
    }
  }

  protected def handle(exceptionInfo: EspExceptionInfo[Throwable]): Unit


}

trait ExceptionHandlerFactory {}

object ExceptionHandlerFactory {

  def noParams(eh: MetaData => EspExceptionHandler): ExceptionHandlerFactory =
    new NoParamExceptionHandlerFactory(eh)

  class NoParamExceptionHandlerFactory(eh: MetaData => EspExceptionHandler) extends ExceptionHandlerFactory {
    def create(metaData: MetaData): EspExceptionHandler = eh(metaData)
  }

}



case class EspExceptionInfo[T <: Throwable](throwable: T, context: Context) extends Serializable