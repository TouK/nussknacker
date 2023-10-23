package pl.touk.nussknacker.engine.api.fixedvaluespresets

import pl.touk.nussknacker.engine.api.definition.FixedExpressionValue

class DefaultFixedValuesPresetProvider(
    fixedValuesPresets: Map[String, List[FixedExpressionValue]]
) extends FixedValuesPresetProvider {
  override def getAll: Map[String, List[FixedExpressionValue]] = fixedValuesPresets
}
