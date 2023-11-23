package pl.touk.nussknacker.engine.api.fixedvaluespresets

import pl.touk.nussknacker.engine.api.definition.FixedExpressionValue

object TestFixedValuesPresetProvider extends FixedValuesPresetProvider {

  val fixedValuesPresets: Map[String, List[FixedExpressionValue]] = Map(
    "presetBoolean" -> List(FixedExpressionValue("true", "ON"), FixedExpressionValue("false", "OFF")),
    "presetString" -> List(
      FixedExpressionValue("'someOtherString'", "string1"),
      FixedExpressionValue("'yetAnotherString'", "string2")
    )
  )

  override def getAll: Map[String, List[FixedExpressionValue]] = fixedValuesPresets
}
