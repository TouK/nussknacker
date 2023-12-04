package pl.touk.nussknacker.engine.api.fixedvaluespresets

import pl.touk.nussknacker.engine.api.definition.FixedExpressionValue
import pl.touk.nussknacker.engine.api.fixedvaluespresets.FixedValuesPresetProvider.FixedValuesPreset

object TestFixedValuesPresetProvider extends FixedValuesPresetProvider {

  val fixedValuesPresets: Map[String, FixedValuesPreset] = Map(
    "presetBoolean" -> FixedValuesPreset(
      "java.lang.Boolean",
      List(FixedExpressionValue("true", "ON"), FixedExpressionValue("false", "OFF"))
    ),
    "presetString" -> FixedValuesPreset(
      "java.lang.String",
      List(
        FixedExpressionValue("'someOtherString'", "string1"),
        FixedExpressionValue("'yetAnotherString'", "string2")
      )
    )
  )

  override def getAll: Map[String, FixedValuesPreset] = fixedValuesPresets
}
