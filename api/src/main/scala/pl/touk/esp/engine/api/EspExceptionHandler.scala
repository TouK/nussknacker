package pl.touk.esp.engine.api

import com.typesafe.scalalogging.LazyLogging

trait EspExceptionHandler extends Serializable {
  final def recover[T](block: => T)(context: Context, processMetaData: MetaData): Option[T] = {
    try {
      Some(block)
    } catch {
      case ex: Throwable =>
        this.handle(EspExceptionInfo(ex, context, processMetaData))
        None
    }
  }

  def open(): Unit
  protected def handle(exceptionInfo: EspExceptionInfo): Unit
  def close(): Unit
}

case class EspExceptionInfo(throwable: Throwable, context: Context, processMetaData: MetaData) extends Serializable

object BrieflyLoggingExceptionHandler extends EspExceptionHandler with LazyLogging {

  override def open(): Unit = {}
  override protected def handle(e: EspExceptionInfo): Unit = {
    logger.warn(s"${e.processMetaData.id}: Exception: ${e.throwable.getMessage} (${e.throwable.getClass.getName})")
  }
  override def close(): Unit = {}
}


object VerboselyLoggingExceptionHandler extends EspExceptionHandler with LazyLogging {

  override def open(): Unit = {}
  override protected def handle(e: EspExceptionInfo): Unit = {
    logger.error(s"${e.processMetaData.id}: Exception during processing job, context: ${e.context}", e.throwable)
  }
  override def close(): Unit = {}
}