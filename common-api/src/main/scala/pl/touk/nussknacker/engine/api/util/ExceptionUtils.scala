package pl.touk.nussknacker.engine.api.util

import java.io.UncheckedIOException
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.{CompletionException, ExecutionException}
import scala.annotation.tailrec

object ExceptionUtils {

  @tailrec
  def unwrapCommonWrappingExceptions(ex: Throwable): Throwable = ex match {
    case _: ExecutionException | _: CompletionException | _: UncheckedIOException if ex.getCause != null =>
      unwrapCommonWrappingExceptions(ex.getCause)
    case invocationTargetEx: InvocationTargetException =>
      unwrapCommonWrappingExceptions(invocationTargetEx.getTargetException)
    case other => other
  }

}
