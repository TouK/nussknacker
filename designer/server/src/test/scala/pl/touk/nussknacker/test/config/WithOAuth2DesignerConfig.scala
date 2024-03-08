package pl.touk.nussknacker.test.config

import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.Suite
import pl.touk.nussknacker.engine.util.config.ScalaMajorVersionConfig

trait WithOAuth2DesignerConfig extends WithDesignerConfig {
  this: Suite =>

  override def designerConfig: Config = ScalaMajorVersionConfig.configWithScalaMajorVersion(
    ConfigFactory.parseResources("config/oauth2/oauth2-designer.conf")
  )

}
