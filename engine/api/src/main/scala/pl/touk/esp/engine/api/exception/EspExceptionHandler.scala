package pl.touk.esp.engine.api.exception

import pl.touk.esp.engine.api.{Context, MetaData}

import scala.util.control.NonFatal

trait EspExceptionHandler {

  def handle(exceptionInfo: EspExceptionInfo[_ <: Throwable]): Unit

  def handling[T](nodeId: Option[String], context: Context)(action : => T) : Option[T] =
    try {
      Some(action)
    } catch {
      case NonFatal(e) => handle(EspExceptionInfo(nodeId, e, context))
        None
    }

}

trait ExceptionHandlerFactory {}

object ExceptionHandlerFactory {

  def noParams(eh: MetaData => EspExceptionHandler): ExceptionHandlerFactory =
    new NoParamExceptionHandlerFactory(eh)

  class NoParamExceptionHandlerFactory(eh: MetaData => EspExceptionHandler) extends ExceptionHandlerFactory {
    def create(metaData: MetaData): EspExceptionHandler = eh(metaData)
  }

}



case class EspExceptionInfo[T <: Throwable](nodeId: Option[String], throwable: T, context: Context) extends Serializable