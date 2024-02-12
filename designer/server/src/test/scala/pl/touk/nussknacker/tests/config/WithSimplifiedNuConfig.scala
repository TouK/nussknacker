package pl.touk.nussknacker.tests.config

import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.Suite
import pl.touk.nussknacker.engine.util.config.ScalaMajorVersionConfig
import pl.touk.nussknacker.tests.base.it.NuItTest2

trait WithSimplifiedNuConfig extends NuItTest2 {
  this: Suite =>

  override def nuTestConfig: Config = ScalaMajorVersionConfig.configWithScalaMajorVersion(
    ConfigFactory.parseResources("config/simple/simple-streaming-use-case-designer.conf")
  )

}
