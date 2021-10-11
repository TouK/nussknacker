package pl.touk.nussknacker.engine.api.component
import io.circe.{Decoder, Encoder}

case class ComponentGroupName(value: String) {
  def toLowerCase: String = value.toLowerCase
}

object ComponentGroupName {

  implicit val encoder: Encoder[ComponentGroupName] = Encoder.encodeString.contramap(_.value)
  implicit val decoder: Decoder[ComponentGroupName] = Decoder.decodeString.map(ComponentGroupName(_))

  val Base: ComponentGroupName = ComponentGroupName("base")
  val Services: ComponentGroupName = ComponentGroupName("services")
  val Enrichers: ComponentGroupName = ComponentGroupName("enrichers")
  val Custom: ComponentGroupName = ComponentGroupName("custom")
  val OptionalEndingCustom: ComponentGroupName = ComponentGroupName("optionalEndingCustom")
  val Sinks: ComponentGroupName = ComponentGroupName("sinks")
  val Sources: ComponentGroupName = ComponentGroupName("sources")
  val Fragments: ComponentGroupName = ComponentGroupName("fragments")
  val FragmentsDefinition: ComponentGroupName = ComponentGroupName("fragmentDefinition")

}
