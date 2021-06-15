package pl.touk.nussknacker.engine.definition

import pl.touk.nussknacker.engine.api.typed.typing.TypedClass

trait WithExplicitTypesToExtract {
  def typesToExtract: List[TypedClass]
}
