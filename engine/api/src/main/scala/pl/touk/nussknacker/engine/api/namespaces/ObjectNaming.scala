package pl.touk.nussknacker.engine.api.namespaces

import com.typesafe.config.Config

trait ObjectNaming extends Serializable {
  def prepareName(originalName: String, config: Config, namingContext: NamingContext): String
  def objectNamingParameters(originalName: String, config: Config, namingContext: NamingContext): ObjectNamingParameters
}

trait ObjectNamingParameters {
  def toTags: Map[String, String]
}

case object DefaultObjectNaming extends ObjectNaming {
  override def prepareName(originalName: String, config: Config, namingContext: NamingContext): String = originalName

  override def objectNamingParameters(originalName: String, config: Config, namingContext: NamingContext): DefaultObjectNamingParameters
  = DefaultObjectNamingParameters()
}

case class DefaultObjectNamingParameters() extends ObjectNamingParameters {
  override def toTags: Map[String, String] = Map()
}