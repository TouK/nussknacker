package pl.touk.nussknacker.engine.api.exception

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.NamedServiceProvider
import pl.touk.nussknacker.engine.api.util.ReflectUtils

trait ExceptionExtractor[T] {
  def unapply(ex: Throwable): Option[T]
}

trait WithExceptionExtractor extends NamedServiceProvider with LazyLogging {

  protected val transientExceptionExtractor: ExceptionExtractor[Exception]

  protected val nonTransientExceptionExtractor: ExceptionExtractor[NonTransientException]

  final def extractOrThrow(exceptionInfo: NuExceptionInfo[_ <: Throwable]): NuExceptionInfo[NonTransientException] = {
    exceptionInfo.throwable match {
      case transientExceptionExtractor(_) =>
        throw exceptionInfo.throwable
      case nonTransientExceptionExtractor(nonTransient) =>
        NuExceptionInfo(exceptionInfo.nodeComponentInfo, nonTransient, exceptionInfo.context)
      case other =>
        val exceptionDetails = s"${ReflectUtils.simpleNameWithoutSuffix(other.getClass)}: ${other.getMessage}"
        val nonTransient = NonTransientException(input = exceptionDetails, message = "Unknown exception", cause = other)
        logger.debug(s"Unknown exception $exceptionDetails for ${exceptionInfo.context.id}", other)
        logger.info(s"Unknown exception $exceptionDetails for ${exceptionInfo.context.id}")
        NuExceptionInfo(exceptionInfo.nodeComponentInfo, nonTransient, exceptionInfo.context)
    }
  }

}
