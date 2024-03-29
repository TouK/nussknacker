package pl.touk.nussknacker.engine.api.async

import pl.touk.nussknacker.engine.api.process.AsyncExecutionContextPreparer

object DefaultAsyncInterpretationValueDeterminer {

  // If default is not configured, will be used false value, but in the future this default will be changed to true.
  val DefaultValue: DefaultAsyncInterpretationValue = DefaultAsyncInterpretationValue(false)

  def determine(asyncExecutionConfig: AsyncExecutionContextPreparer): DefaultAsyncInterpretationValue =
    asyncExecutionConfig.defaultUseAsyncInterpretation
      .map(DefaultAsyncInterpretationValue)
      .getOrElse(DefaultValue)

}
