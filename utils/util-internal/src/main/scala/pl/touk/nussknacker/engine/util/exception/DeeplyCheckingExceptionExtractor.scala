package pl.touk.nussknacker.engine.util.exception

import pl.touk.nussknacker.engine.api.exception.ExceptionExtractor

import scala.reflect.ClassTag



class DeeplyCheckingExceptionExtractor[T](pf: PartialFunction[Throwable, T]) extends ExceptionExtractor[T] {
  override def unapply(ex: Throwable): Option[T] =
    ex match {
      case e if pf.isDefinedAt(e) => Some(pf(e))
      case _ if ex.getCause != null && !ex.getCause.eq(ex) => unapply(ex.getCause)
      case _ => None
    }
}

object DeeplyCheckingExceptionExtractor {

  def forClass[T:ClassTag] = new DeeplyCheckingExceptionExtractor[T]({ case e:T => e})

}
