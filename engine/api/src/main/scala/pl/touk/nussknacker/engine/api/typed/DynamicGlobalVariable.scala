package pl.touk.nussknacker.engine.api.typed

import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult

/**
  * Trait to be implemented by global variable to provide dynamic value and more detailed typing info.
  */
trait DynamicGlobalVariable {
  def value(metadata: MetaData): Any
  def returnType(metadata: MetaData): TypingResult
  def runtimeClass: Class[_]
}
