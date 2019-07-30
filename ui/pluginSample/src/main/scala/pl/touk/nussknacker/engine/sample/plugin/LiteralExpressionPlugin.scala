package pl.touk.nussknacker.engine.sample.plugin

import argonaut._
import Argonaut._
import com.typesafe.config.Config
import org.apache.commons.io.IOUtils
import pl.touk.nussknacker.engine.plugin.FrontendPlugin
import net.ceedubs.ficus.Ficus._

class LiteralExpressionPlugin extends FrontendPlugin {

  override def externalResources: List[String] = List()

  override def internalResources: Map[String, Array[Byte]] = Map("literalExpressions.js" -> IOUtils.toByteArray(getClass.getResourceAsStream("/literalExpressions.js")))

  override def name: String = "literalExpressions"

  override def createTypeSpecific(classLoader: ClassLoader, config: Config): Option[Json] = Some(
    Map("defaultValue" -> config.getAs[String]("defaultValue").getOrElse("")).asJson
  )

}
case class Field(name: String, label: String)
