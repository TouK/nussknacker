package pl.touk.nussknacker.engine.api.definition

import pl.touk.nussknacker.engine.api.typed.typing.TypingResult

trait WithExplicitTypesToExtract {
  def typesToExtract: List[TypingResult]
}
