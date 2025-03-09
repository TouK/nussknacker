package pl.touk.nussknacker.engine.util.exception

import pl.touk.nussknacker.engine.api.exception.{
  DeeplyCheckingExceptionExtractor,
  ExceptionExtractor,
  WithExceptionExtractor
}

import java.net.ConnectException

class DefaultWithExceptionExtractor extends WithExceptionExtractor {

  override val transientExceptionExtractor: ExceptionExtractor[Throwable] =
    DeeplyCheckingExceptionExtractor.forClass[ConnectException]

  override def name: String = DefaultWithExceptionExtractor.name
}

object DefaultWithExceptionExtractor {
  val name = "Default"
}
