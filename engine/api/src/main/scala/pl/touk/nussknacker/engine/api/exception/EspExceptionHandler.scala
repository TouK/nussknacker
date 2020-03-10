package pl.touk.nussknacker.engine.api.exception

import pl.touk.nussknacker.engine.api.{Context, Lifecycle, MetaData, MethodToInvoke}

import scala.util.control.NonFatal

trait EspExceptionHandler extends Lifecycle {

  def handle(exceptionInfo: EspExceptionInfo[_ <: Throwable]): Unit

  def handling[T](nodeId: Option[String], context: Context)(action: => T): Option[T] =
    try {
      Some(action)
    } catch {
      case NonFatal(e) => handle(EspExceptionInfo(nodeId, e, context))
        None
    }

}

object EspExceptionHandler {
  val empty = new EspExceptionHandler {
    override def handle(exceptionInfo: EspExceptionInfo[_ <: Throwable]): Unit = {}
  }
}

/**
  * [[pl.touk.nussknacker.engine.api.exception.ExceptionHandlerFactory]] has to have method annotated with [[pl.touk.nussknacker.engine.api.MethodToInvoke]]
  * that returns [[pl.touk.nussknacker.engine.api.exception.EspExceptionHandler]]
  *
  * IMPORTANT lifecycle notice:
  * Implementations of this class *must not* allocate resources (connections, file handles etc.)
  **/
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