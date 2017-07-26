package pl.touk.nussknacker.engine.flink.util.exception

trait ExceptionExtractor[T] {
  def unapply(ex: Throwable): Option[T]
}

class DeeplyCheckingExceptionExtractor[T](pf: PartialFunction[Throwable, T]) extends ExceptionExtractor[T] {
  override def unapply(ex: Throwable): Option[T] =
    ex match {
      case e if pf.isDefinedAt(e) => Some(pf(e))
      case _ if ex.getCause != null && !ex.getCause.eq(ex) => unapply(ex.getCause)
      case _ => None
    }
}