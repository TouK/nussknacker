package pl.touk.nussknacker.engine.api.namespaces

import com.typesafe.config.Config

trait ObjectNaming extends Serializable {
  def prepareName(originalName: String, config: Config, namingContext: NamingContext): String = {
    val separator = getSeparator(config,namingContext)
    getNamespace(config, namingContext) match {
      case None => originalName
      case Some(ns) => s"$ns$separator$originalName"
    }
  }
  def getNamespace(config: Config, namingContext: NamingContext): Option[String] = None
  def getSeparator(config: Config, namingContext: NamingContext): String = "_"
}

case object DefaultObjectNaming extends ObjectNaming