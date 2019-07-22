package pl.touk.nussknacker.engine.plugin

import argonaut.Json
import com.typesafe.config.Config

trait FrontendPluginConfigurationProvider {

  def createConfigurations(config: Config): List[FrontendPluginConfiguration]

}

case class FrontendPluginConfiguration(name: String, frontendResourceNames: List[String], frontendConfig: Json)
