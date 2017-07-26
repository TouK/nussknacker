package pl.touk.nussknacker.engine.api.exception

import pl.touk.nussknacker.engine.api.{Context, MetaData, MethodToInvoke}

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

/**
  * [[pl.touk.nussknacker.engine.api.exception.ExceptionHandlerFactory]] has to have method annotated with [[pl.touk.nussknacker.engine.api.MethodToInvoke]]
  * that returns [[pl.touk.nussknacker.engine.api.exception.EspExceptionHandler]]
* */
trait ExceptionHandlerFactory {}

object ExceptionHandlerFactory {

  def noParams(eh: MetaData => EspExceptionHandler): ExceptionHandlerFactory =
    new NoParamExceptionHandlerFactory(eh)

  class NoParamExceptionHandlerFactory(eh: MetaData => EspExceptionHandler) extends ExceptionHandlerFactory {
    @MethodToInvoke
    def create(metaData: MetaData): EspExceptionHandler = eh(metaData)
  }

}



case class EspExceptionInfo[T <: Throwable](nodeId: Option[String], throwable: T, context: Context) extends Serializable