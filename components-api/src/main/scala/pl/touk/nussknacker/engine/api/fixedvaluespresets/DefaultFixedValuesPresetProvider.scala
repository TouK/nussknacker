package pl.touk.nussknacker.engine.api.fixedvaluespresets

import pl.touk.nussknacker.engine.api.fixedvaluespresets.FixedValuesPresetProvider.FixedValuesPreset

class DefaultFixedValuesPresetProvider(
    fixedValuesPresets: Map[String, FixedValuesPreset]
) extends FixedValuesPresetProvider {
  override def getAll: Map[String, FixedValuesPreset] = fixedValuesPresets
}
