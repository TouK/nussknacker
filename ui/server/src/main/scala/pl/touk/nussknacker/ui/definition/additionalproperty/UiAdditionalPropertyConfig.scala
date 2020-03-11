package pl.touk.nussknacker.ui.definition.additionalproperty

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.definition.{ParameterEditor, ParameterValidator}
import pl.touk.nussknacker.engine.api.process.AdditionalPropertyConfig

@JsonCodec case class UiAdditionalPropertyConfig(defaultValue: Option[String],
                                                 editor: ParameterEditor,
                                                 validators: List[ParameterValidator],
                                                 label: Option[String])

object UiAdditionalPropertyConfig {
  def apply(config: AdditionalPropertyConfig): UiAdditionalPropertyConfig = {
    val editor = UiAdditionalPropertyEditorDeterminer.determine(config)
    val determined = AdditionalPropertyValidatorDeterminerChain(config).determine()
    new UiAdditionalPropertyConfig(config.defaultValue, editor, determined, config.label)
  }
}
