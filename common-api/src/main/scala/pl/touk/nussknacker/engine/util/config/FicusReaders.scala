package pl.touk.nussknacker.engine.util.config

import com.typesafe.config.ConfigRenderOptions
import io.circe.Decoder
import net.ceedubs.ficus.readers.ValueReader
import pl.touk.nussknacker.engine.api.CirceUtil

import scala.reflect.ClassTag

object FicusReaders extends FicusReaders

trait FicusReaders {

  def forDecoder[T: Decoder : ClassTag]: ValueReader[T] = ValueReader.relative(config => {
    val json = config.root().render(ConfigRenderOptions.concise().setJson(true))
    CirceUtil.decodeJsonUnsafe[T](json, s"Invalid value of ${implicitly[ClassTag[T]].runtimeClass.getSimpleName}")
  })

}
