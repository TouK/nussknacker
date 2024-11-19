package pl.touk.nussknacker.engine.deployment

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.component.{ComponentAdditionalConfig, DesignerWideComponentId}

@JsonCodec
final case class AdditionalModelConfigs(
    additionalConfigsFromProvider: Map[DesignerWideComponentId, ComponentAdditionalConfig]
)

object AdditionalModelConfigs {

  def empty: AdditionalModelConfigs = AdditionalModelConfigs(Map.empty)

}
