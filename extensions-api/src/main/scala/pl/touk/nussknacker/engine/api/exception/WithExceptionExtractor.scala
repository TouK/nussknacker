package pl.touk.nussknacker.engine.api.exception

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.NamedServiceProvider

trait ExceptionExtractor[+T] {
  def unapply(ex: Throwable): Option[T]
}

trait WithExceptionExtractor extends NamedServiceProvider with LazyLogging {

  protected val transientExceptionExtractor: ExceptionExtractor[Throwable]

  private val nonTransientExceptionExtractor = DeeplyCheckingExceptionExtractor.forClass[NonTransientException]

  final def extractOrThrow(exceptionInfo: NuExceptionInfo): NuExceptionInfo = {
    exceptionInfo.throwable match {
      case transientExceptionExtractor(_) =>
        throw exceptionInfo.throwable
      case nonTransientExceptionExtractor(nonTransient) =>
        NuExceptionInfo.fromNonTransient(exceptionInfo.nodeComponentInfo, nonTransient, exceptionInfo.context)
      case _ => exceptionInfo
    }
  }

}
