package pl.touk.nussknacker.engine.util.config

import cats.data.NonEmptyList
import com.typesafe.config.{Config, ConfigException, ConfigRenderOptions}
import io.circe.Decoder
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ValueReader
import pl.touk.nussknacker.engine.api.CirceUtil
import pl.touk.nussknacker.engine.api.component.ScenarioPropertyConfig
import pl.touk.nussknacker.engine.api.definition.{ParameterEditor, ParameterValidator}

import scala.reflect.ClassTag

object FicusReaders {

  def forDecoder[T: Decoder: ClassTag]: ValueReader[T] = ValueReader.relative(config => {
    val json = config.root().render(ConfigRenderOptions.concise().setJson(true))
    CirceUtil.decodeJsonUnsafe[T](json, s"Invalid value of ${implicitly[ClassTag[T]].runtimeClass.getSimpleName}")
  })

  implicit val paramEditorReader: ValueReader[ParameterEditor] = forDecoder

  implicit val paramValidatorReader: ValueReader[ParameterValidator] = forDecoder

  implicit val paramConfigReader: ValueReader[ScenarioPropertyConfig] = forDecoder

  implicit def nonEmptyListValueReader[A: ValueReader]: ValueReader[NonEmptyList[A]] =
    (config: Config, path: String) => {
      NonEmptyList
        .fromList(ValueReader[List[A]].read(config, path))
        .getOrElse(
          throw new ConfigException.BadValue(
            config.origin(),
            path,
            s"Non empty list of values is required"
          )
        )
    }

}
