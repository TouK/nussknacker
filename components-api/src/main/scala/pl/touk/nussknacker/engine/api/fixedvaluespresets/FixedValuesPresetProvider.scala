package pl.touk.nussknacker.engine.api.fixedvaluespresets

import pl.touk.nussknacker.engine.api.definition.FixedExpressionValue

trait FixedValuesPresetProvider extends Serializable {

  def getAll: Map[String, List[FixedExpressionValue]] // TODO maybe preset should have information about it's type? (FE preset selection could be narrowed to fitting types)

}

object FixedValuesPresetProvider {
  val empty = new DefaultFixedValuesPresetProvider(Map.empty)
}
