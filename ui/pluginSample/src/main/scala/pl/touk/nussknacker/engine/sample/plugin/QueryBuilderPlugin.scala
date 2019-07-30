package pl.touk.nussknacker.engine.sample.plugin

import argonaut._
import Argonaut._
import ArgonautShapeless._
import com.typesafe.config.Config
import org.apache.commons.io.IOUtils
import pl.touk.nussknacker.engine.plugin.FrontendPlugin

class QueryBuilderPlugin extends FrontendPlugin {

  override def externalResources: List[String] = List()

  override def internalResources: Map[String, Array[Byte]] = Map("queryBuilder.js" -> IOUtils.toByteArray(getClass.getResourceAsStream("/queryBuilder.js")))

  override def name: String = "queryBuilder"

  //TODO: sample depending of configuration
  override def createTypeSpecific(classLoader: ClassLoader, config: Config): Option[Json] = Some(
    Map("fields" -> List(Field("firstName", "First name"), Field("lastName", "Last name"))).asJson
  )

}
case class Field(name: String, label: String)
