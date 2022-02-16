package pl.touk.nussknacker.engine.util.exception

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.{MetaData, NamedServiceProvider}
import pl.touk.nussknacker.engine.api.exception.{NonTransientException, NuExceptionInfo}
import pl.touk.nussknacker.engine.api.util.ReflectUtils
import pl.touk.nussknacker.engine.util.logging.LazyLoggingWithTraces

import java.net.ConnectException

trait WithExceptionExtractor extends LazyLoggingWithTraces {

  protected val transientExceptionExtractor: ExceptionExtractor[Exception] = _ => None

  protected val nonTransientExceptionExtractor: ExceptionExtractor[NonTransientException] = _ => None

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

object DefaultWithExceptionExtractor extends WithExceptionExtractor {

  override protected val transientExceptionExtractor: ExceptionExtractor[Exception] =
    new DeeplyCheckingExceptionExtractor({ case a: ConnectException => a: Exception })

  override protected val nonTransientExceptionExtractor: ExceptionExtractor[NonTransientException] =
    new DeeplyCheckingExceptionExtractor({ case a: NonTransientException => a })
}

trait FlinkWithExceptionExtractorProvider extends NamedServiceProvider {

  def create(metaData: MetaData, exceptionHandlerConfig: Config): WithExceptionExtractor

}