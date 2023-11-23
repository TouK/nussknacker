package pl.touk.nussknacker.engine.api.fixedvaluespresets

import pl.touk.nussknacker.engine.api.definition.FixedExpressionValue

trait FixedValuesPresetProvider extends Serializable {

  def getAll: Map[String, List[FixedExpressionValue]]

}

object FixedValuesPresetProvider {
  val empty = new DefaultFixedValuesPresetProvider(Map.empty)
}
