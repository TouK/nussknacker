package pl.touk.nussknacker.engine.util.config

import com.typesafe.config.{Config, ConfigFactory}
import net.ceedubs.ficus.SimpleFicusConfig
import net.ceedubs.ficus.readers.ValueReader

object ConfigEnrichments {

  implicit class RichConfig(config: Config) {
    def rootAs[A](implicit reader: ValueReader[A]): A = {
      val wrappedConfig = ConfigFactory.empty().withValue("root", config.root())
      SimpleFicusConfig(wrappedConfig).as[A]("root")
    }
  }
}
