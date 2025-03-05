package pl.touk.nussknacker.ui.config

import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import pl.touk.nussknacker.ui.config.Implicits.parseOptionalConfig

object AttachmentsConfig {
  val default = AttachmentsConfig(10 * 1024 * 1024) // 10mb

  def create(config: Config): AttachmentsConfig = {
    parseOptionalConfig[AttachmentsConfig](config, "attachments").getOrElse(default)
  }

}

final case class AttachmentsConfig(maxSizeInBytes: Long)
