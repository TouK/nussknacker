package pl.touk.nussknacker.engine.api.fixedvaluespresets

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.definition.FixedExpressionValue
import pl.touk.nussknacker.engine.api.fixedvaluespresets.FixedValuesPresetProvider.FixedValuesPreset

/*
 * TODO: Using fixed values presets is currently only supported for fragment parameters (resolved in FragmentComponentDefinitionExtractor.toParameter)
 * Supporting presets in other types of parameters would require adding resolution logic in more places (or some common place, though I think this would require prior refactoring)
 */
trait FixedValuesPresetProvider extends Serializable {

  def getAll: Map[String, FixedValuesPreset]

}

object FixedValuesPresetProvider {

  @JsonCodec
  case class FixedValuesPreset(
      refClazzName: String,
      values: List[FixedExpressionValue],
  )

  val empty = new DefaultFixedValuesPresetProvider(Map.empty)
}
