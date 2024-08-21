package pl.touk.nussknacker.engine.util.config

import com.typesafe.config.ConfigRenderOptions
import io.circe.Decoder
import net.ceedubs.ficus.readers.ValueReader
import pl.touk.nussknacker.engine.api.CirceUtil
import pl.touk.nussknacker.engine.api.definition.{ParameterEditor, ParameterValidator}
import pl.touk.nussknacker.engine.api.component.ScenarioPropertyConfig

import scala.reflect.ClassTag

object FicusReaders {

  def forDecoder[T: Decoder: ClassTag]: ValueReader[T] = ValueReader.relative(config => {
    val json = config.root().render(ConfigRenderOptions.concise().setJson(true))
    CirceUtil.decodeJsonUnsafe[T](json, s"Invalid value of ${implicitly[ClassTag[T]].runtimeClass.getSimpleName}")
  })

  implicit val paramEditorReader: ValueReader[ParameterEditor] = forDecoder

  implicit val paramValidatorReader: ValueReader[ParameterValidator] = forDecoder

  implicit val paramConfigReader: ValueReader[ScenarioPropertyConfig] = forDecoder
}
