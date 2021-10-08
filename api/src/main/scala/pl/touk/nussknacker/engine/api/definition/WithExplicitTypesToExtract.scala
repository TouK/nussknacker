package pl.touk.nussknacker.engine.api.definition

import pl.touk.nussknacker.engine.api.typed.typing.TypedClass

trait WithExplicitTypesToExtract {
  def typesToExtract: List[TypedClass]
}
