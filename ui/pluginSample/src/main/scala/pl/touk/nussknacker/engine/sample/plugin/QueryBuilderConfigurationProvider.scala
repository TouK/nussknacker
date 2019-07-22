package pl.touk.nussknacker.engine.sample.plugin

import argonaut._
import Argonaut._
import ArgonautShapeless._

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.plugin.{FrontendPluginConfiguration, FrontendPluginConfigurationProvider}

class QueryBuilderConfigurationProvider extends FrontendPluginConfigurationProvider {

  override def createConfigurations(config: Config): List[FrontendPluginConfiguration] = {
    List(FrontendPluginConfiguration(
      "queryBuilder", List("customField.js"), Map("fields" -> List(Field("firstName", "First name"), Field("lastName", "Last name"))).asJson
    ))
  }

}
case class Field(name: String, label: String)
