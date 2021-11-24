package pl.touk.nussknacker.engine.util.exception

import pl.touk.nussknacker.engine.api.exception.{NuExceptionInfo, NonTransientException}
import pl.touk.nussknacker.engine.api.util.ReflectUtils
import pl.touk.nussknacker.engine.util.exception.WithExceptionExtractor.{DefaultNonTransientExceptionExtractor, DefaultTransientExceptionExtractor}
import pl.touk.nussknacker.engine.util.logging.LazyLoggingWithTraces

import java.net.ConnectException

object WithExceptionExtractor extends WithExceptionExtractor {

  object DefaultTransientExceptionExtractor
    extends DeeplyCheckingExceptionExtractor({ case a: ConnectException => a: Exception })

  object DefaultNonTransientExceptionExtractor
    extends DeeplyCheckingExceptionExtractor({ case a: NonTransientException => a })

}

trait WithExceptionExtractor extends LazyLoggingWithTraces {

  protected val transientExceptionExtractor: ExceptionExtractor[Exception] =
    DefaultTransientExceptionExtractor
  protected val nonTransientExceptionExtractor: ExceptionExtractor[NonTransientException] =
    DefaultNonTransientExceptionExtractor

  final def extractOrThrow(exceptionInfo: NuExceptionInfo[_ <: Throwable]): NuExceptionInfo[NonTransientException] = {
    exceptionInfo.throwable match {
      case transientExceptionExtractor(_) =>
        throw exceptionInfo.throwable
      case nonTransientExceptionExtractor(nonTransient) =>
        NuExceptionInfo(exceptionInfo.nodeId, nonTransient, exceptionInfo.context)
      case other =>
        val exceptionDetails = s"${ReflectUtils.simpleNameWithoutSuffix(other.getClass)}:${other.getMessage}"
        val nonTransient = NonTransientException(input = exceptionDetails, message = "Unknown exception", cause = other)
        infoWithDebugStack(s"Unknown exception $exceptionDetails for ${exceptionInfo.context.id}", other)
        NuExceptionInfo(exceptionInfo.nodeId, nonTransient, exceptionInfo.context)
    }
  }

}