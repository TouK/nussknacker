package pl.touk.nussknacker.engine.util.config

import java.io.File
import java.net.{URI, URL}

import com.typesafe.config.ConfigRenderOptions
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ValueReader
import pl.touk.nussknacker.engine.api.CirceUtil
import pl.touk.nussknacker.engine.api.definition.{ParameterEditor, ParameterValidator}
import pl.touk.nussknacker.engine.api.process.{AdditionalPropertyConfig, ParameterConfig}

import scala.util.Try

object FicusReaders {

  implicit val urlValueReader: ValueReader[URL] = ValueReader[String]
    .map(value => Try(new URL(value)).getOrElse(new File(value).toURI.toURL))

  implicit val uriValueReader: ValueReader[URI] = ValueReader[String]
    .map(value => Try(new URI(value)).getOrElse(new File(value).toURI))

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
    CirceUtil.decodeJsonUnsafe[AdditionalPropertyConfig](json, "invalid parameter validator config")
  })
}
