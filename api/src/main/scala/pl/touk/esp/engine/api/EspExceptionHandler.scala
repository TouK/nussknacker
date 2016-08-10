package pl.touk.esp.engine.api

trait EspExceptionHandler extends Serializable {
  final def recover[T](block: => T)(context: Context): Option[T] = {
    try {
      Some(block)
    } catch {
      case ex: Throwable =>
        this.handle(EspExceptionInfo(ex, context))
        None
    }
  }

  def open(): Unit
  protected def handle(exceptionInfo: EspExceptionInfo): Unit
  def close(): Unit
}

case class EspExceptionInfo(throwable: Throwable, context: Context) extends Serializable

object SkipExceptionHandler extends EspExceptionHandler {
  override def open(): Unit = {}
  override protected def handle(exceptionInfo: EspExceptionInfo): Unit = {}
  override def close(): Unit = {}
}