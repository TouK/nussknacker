package pl.touk.nussknacker.engine.api.namespaces

import com.typesafe.config.Config

trait ObjectNaming {
  def prepareName(originalName: String, config: Config, namingContext: NamingContext): String
}

case object DefaultObjectNaming extends ObjectNaming {
  override def prepareName(originalName: String, config: Config, namingContext: NamingContext): String =
    originalName
}