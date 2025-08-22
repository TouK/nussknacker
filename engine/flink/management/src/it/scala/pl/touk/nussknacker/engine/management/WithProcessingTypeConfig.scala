package pl.touk.nussknacker.engine.management

import com.typesafe.config.{Config, ConfigFactory}
import org.apache.commons.io.IOUtils
import pl.touk.nussknacker.engine.{ConfigWithUnresolvedVersion, ProcessingTypeConfig}

import java.nio.charset.StandardCharsets

trait WithProcessingTypeConfig {

  protected lazy val rawProcessingTypeConfig: Config = {
    val fileConfig =
      ConfigFactory
        .parseString(IOUtils.resourceToString(s"/application.conf", StandardCharsets.UTF_8))
        .resolve()

    resolveProcessingTypeConfig(fileConfig)
  }

  protected def resolveProcessingTypeConfig(config: Config): Config = config

  def processingTypeConfig: ProcessingTypeConfig =
    ProcessingTypeConfig.read(ConfigWithUnresolvedVersion(rawProcessingTypeConfig))

}
