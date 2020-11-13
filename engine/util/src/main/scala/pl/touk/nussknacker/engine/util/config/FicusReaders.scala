package pl.touk.nussknacker.engine.util.config

import com.typesafe.config.ConfigRenderOptions
import net.ceedubs.ficus.readers.ValueReader
import pl.touk.nussknacker.engine.api.CirceUtil
import pl.touk.nussknacker.engine.api.definition.{ParameterEditor, ParameterValidator}
import pl.touk.nussknacker.engine.api.process.AdditionalPropertyConfig

object FicusReaders {

  implicit val paramEditorReader: ValueReader[ParameterEditor] = ValueReader.relative(config => {
    val json = config.root().render(ConfigRenderOptions.concise().setJson(true))
    CirceUtil.decodeJsonUnsafe[ParameterEditor](json, "invalid parameter editor config")
  })

  implicit val paramValidatorReader: ValueReader[ParameterValidator] = ValueReader.relative(config => {
    val json = config.root().render(ConfigRenderOptions.concise().setJson(true))
    CirceUtil.decodeJsonUnsafe[ParameterValidator](json, "invalid parameter validator config")
  })

  implicit val paramConfigReader: ValueReader[AdditionalPropertyConfig] = ValueReader.relative(config => {
    val json = config.root().render(ConfigRenderOptions.concise().setJson(true))
    CirceUtil.decodeJsonUnsafe[AdditionalPropertyConfig](json, "invalid additional property config")
  })
}
