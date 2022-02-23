package pl.touk.nussknacker.engine.util.exception

import pl.touk.nussknacker.engine.api.exception.{ExceptionExtractor, NonTransientException, WithExceptionExtractor}

import java.net.ConnectException

class DefaultWithExceptionExtractor extends WithExceptionExtractor {

  override val transientExceptionExtractor: ExceptionExtractor[Exception] =
    new DeeplyCheckingExceptionExtractor({ case a: ConnectException => a: Exception })

  override val nonTransientExceptionExtractor: ExceptionExtractor[NonTransientException] =
    new DeeplyCheckingExceptionExtractor({ case a: NonTransientException => a })

  override def name: String = DefaultWithExceptionExtractor.name
}

object DefaultWithExceptionExtractor {
  val name = "Default"
}