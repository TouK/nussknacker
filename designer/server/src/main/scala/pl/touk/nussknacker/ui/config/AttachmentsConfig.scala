package pl.touk.nussknacker.ui.config

import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import pl.touk.nussknacker.ui.config.Implicits.ConfigExt

object AttachmentsConfig {
  private val default = AttachmentsConfig(10 * 1024 * 1024) // 10mb

  def parse(config: Config): AttachmentsConfig = {
    config.getAsWithLogging[AttachmentsConfig]("attachments").getOrElse(default)
  }

}

final case class AttachmentsConfig(maxSizeInBytes: Long)
