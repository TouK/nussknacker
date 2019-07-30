package pl.touk.nussknacker.engine.util.plugins

import com.typesafe.config.{Config, ConfigFactory}
import pl.touk.nussknacker.engine.util.loader.ScalaServiceLoader
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._

import scala.reflect.ClassTag

trait Plugin {

  type ProcessingTypeSpecific

  def name: String

  def createTypeSpecific(classLoader: ClassLoader, config: Config): Option[ProcessingTypeSpecific]

}

object Plugin {

  def load[T<:Plugin](cl: ClassLoader)(implicit ct: ClassTag[T]): List[T] = {
    ScalaServiceLoader.load[T](cl)
  }

  def loadForProcessingType[T<:Plugin](plugin: T, classLoader: ClassLoader, config: Config): Option[plugin.ProcessingTypeSpecific] = {
    val pluginConfigs = config.getAs[Map[String, Config]]("plugins").getOrElse(Map())
    val pluginConfig =  pluginConfigs.getOrElse(plugin.name, ConfigFactory.empty())
    plugin.createTypeSpecific(classLoader, pluginConfig)
  }

}
