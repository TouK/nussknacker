package pl.touk.nussknacker.engine.api.fixedvaluespresets

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.definition.FixedExpressionValue
import pl.touk.nussknacker.engine.api.fixedvaluespresets.FixedValuesPresetProvider.FixedValuesPreset

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
