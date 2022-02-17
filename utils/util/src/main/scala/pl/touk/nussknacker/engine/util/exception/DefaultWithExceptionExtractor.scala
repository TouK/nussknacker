package pl.touk.nussknacker.engine.util.exception

import pl.touk.nussknacker.engine.api.exception.NonTransientException

import java.net.ConnectException

class DefaultWithExceptionExtractor extends WithExceptionExtractor {

  override protected val transientExceptionExtractor: ExceptionExtractor[Exception] =
    new DeeplyCheckingExceptionExtractor({ case a: ConnectException => a: Exception })

  override protected val nonTransientExceptionExtractor: ExceptionExtractor[NonTransientException] =
    new DeeplyCheckingExceptionExtractor({ case a: NonTransientException => a })

  override def name: String = DefaultWithExceptionExtractor.name
}

object DefaultWithExceptionExtractor {
  val name = "Default"
}