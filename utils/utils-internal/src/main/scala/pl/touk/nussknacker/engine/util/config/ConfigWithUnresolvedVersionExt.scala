package pl.touk.nussknacker.engine.util.config

import pl.touk.nussknacker.engine.ConfigWithUnresolvedVersion

import scala.jdk.CollectionConverters._
import scala.language.implicitConversions

class ConfigWithUnresolvedVersionExt(val config: ConfigWithUnresolvedVersion) {

  def readMap(path: String): Option[Map[String, ConfigWithUnresolvedVersion]] = {
    if (config.resolved.hasPath(path)) {
      val nestedConfig = config.getConfig(path)
      Some(
        nestedConfig.resolved
          .root()
          .entrySet()
          .asScala
          .map(_.getKey)
          .map { key => key -> nestedConfig.getConfig(key) }
          .toMap
      )
    } else {
      None
    }
  }

  def readStringList(path: String): Option[List[String]] = {
    if (config.resolved.hasPath(path)) {
      Some(config.resolved.getStringList(path).asScala.toList)
    } else {
      None
    }
  }

}

object ConfigWithUnresolvedVersionExt {
  implicit def toConfigWithUnresolvedVersionExt(config: ConfigWithUnresolvedVersion): ConfigWithUnresolvedVersionExt =
    new ConfigWithUnresolvedVersionExt(config)
}
