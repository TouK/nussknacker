package pl.touk.nussknacker.engine.api.typed

import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult

/**
  * Trait to be implemented by global variable to provide value based on metadata and more detailed typing info.
  */
trait TypedGlobalVariable {
  def value(metadata: MetaData): Any
  def returnType(metadata: MetaData): TypingResult
  def initialReturnType: TypingResult
}
