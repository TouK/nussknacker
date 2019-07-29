package pl.touk.nussknacker.engine.util.plugins

import com.typesafe.config.Config

trait PluginCreator {

  type PluginInterface

  def pluginInterface: Class[PluginInterface]

  def name: String

  def create(config: Config): PluginInterface

}
