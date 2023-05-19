package pl.touk.nussknacker.restmodel.component

import io.circe.{Decoder, Encoder}
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.component.ComponentType.ComponentType

case class ScenarioComponentsUsages(value: List[ComponentUsages]) extends AnyVal

@JsonCodec
case class ComponentUsages(componentName: Option[String], componentType: ComponentType, nodeIds: List[NodeId])

object ScenarioComponentsUsages {

  val Empty: ScenarioComponentsUsages = ScenarioComponentsUsages(Nil)

  implicit val decoder: Decoder[ScenarioComponentsUsages] = Decoder.decodeList[ComponentUsages].map(ScenarioComponentsUsages(_))
  implicit val encoder: Encoder[ScenarioComponentsUsages] = Encoder.encodeList[ComponentUsages].contramap[ScenarioComponentsUsages](_.value)

}
