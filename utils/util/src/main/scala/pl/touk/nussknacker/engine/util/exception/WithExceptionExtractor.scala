package pl.touk.nussknacker.engine.util.exception

import pl.touk.nussknacker.engine.api.NamedServiceProvider
import pl.touk.nussknacker.engine.api.exception.{NonTransientException, NuExceptionInfo}
import pl.touk.nussknacker.engine.api.util.ReflectUtils
import pl.touk.nussknacker.engine.util.logging.LazyLoggingWithTraces

trait WithExceptionExtractor extends NamedServiceProvider with LazyLoggingWithTraces {

  protected val transientExceptionExtractor: ExceptionExtractor[Exception]

  protected val nonTransientExceptionExtractor: ExceptionExtractor[NonTransientException]

  final def extractOrThrow(exceptionInfo: NuExceptionInfo[_ <: Throwable]): NuExceptionInfo[NonTransientException] = {
    exceptionInfo.throwable match {
      case transientExceptionExtractor(_) =>
        throw exceptionInfo.throwable
      case nonTransientExceptionExtractor(nonTransient) =>
        NuExceptionInfo(exceptionInfo.nodeComponentInfo, nonTransient, exceptionInfo.context)
      case other =>
        val exceptionDetails = s"${ReflectUtils.simpleNameWithoutSuffix(other.getClass)}:${other.getMessage}"
        val nonTransient = NonTransientException(input = exceptionDetails, message = "Unknown exception", cause = other)
        infoWithDebugStack(s"Unknown exception $exceptionDetails for ${exceptionInfo.context.id}", other)
        NuExceptionInfo(exceptionInfo.nodeComponentInfo, nonTransient, exceptionInfo.context)
    }
  }

}