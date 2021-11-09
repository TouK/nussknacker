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

case class EspExceptionInfo[T <: Throwable](nodeId: Option[String], throwable: T, context: Context) extends Serializable