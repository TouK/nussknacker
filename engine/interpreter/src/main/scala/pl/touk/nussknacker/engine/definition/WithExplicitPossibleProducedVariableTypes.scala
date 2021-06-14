package pl.touk.nussknacker.engine.definition

import pl.touk.nussknacker.engine.api.typed.typing.TypedClass

trait WithExplicitPossibleProducedVariableTypes {
  def possibleVariableClasses: Set[TypedClass]
}
