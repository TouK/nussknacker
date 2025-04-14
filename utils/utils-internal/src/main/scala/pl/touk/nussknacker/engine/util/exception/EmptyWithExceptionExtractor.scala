package pl.touk.nussknacker.engine.util.exception

import pl.touk.nussknacker.engine.api.exception.{
  DeeplyCheckingExceptionExtractor,
  ExceptionExtractor,
  WithExceptionExtractor
}

class EmptyWithExceptionExtractor extends WithExceptionExtractor {

  override val transientExceptionExtractor: ExceptionExtractor[Throwable] =
    new DeeplyCheckingExceptionExtractor(PartialFunction.empty)

  override def name: String = EmptyWithExceptionExtractor.name
}

object EmptyWithExceptionExtractor {
  val name = "Empty"
}
