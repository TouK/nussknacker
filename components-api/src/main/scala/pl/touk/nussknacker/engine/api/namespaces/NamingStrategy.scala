package pl.touk.nussknacker.engine.api.namespaces

import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._

final case class Namespace(value: String, separator: String)

final case class NamingStrategy(namespace: Option[Namespace]) {

  def prepareName(name: String): String = namespace match {
    case Some(Namespace(value, separator)) => s"$value$separator$name"
    case None                              => name
  }

  def decodeName(name: String): Option[String] = namespace match {
    case Some(Namespace(value, separator)) if name.startsWith(s"$value$separator") =>
      Some(name.stripPrefix(s"$value$separator"))
    case Some(Namespace(_, _)) => None
    case None                  => Some(name)
  }

}

object NamingStrategy {
  private val defaultNamespaceSeparator = "_"
  private val namespacePath             = "namespace"
  private val namespaceSeparatorPath    = "namespaceSeparator"

  val Disabled: NamingStrategy = NamingStrategy(None)

  def fromConfig(modelConfig: Config): NamingStrategy = {
    if (modelConfig.hasPath(namespacePath)) {
      readNamespaceConfig(modelConfig)
    } else {
      Disabled
    }
  }

  private def readNamespaceConfig(modelConfig: Config): NamingStrategy = {
    val value     = modelConfig.as[String](namespacePath)
    val separator = modelConfig.getAs[String](namespaceSeparatorPath).getOrElse(defaultNamespaceSeparator)
    val namespace = Namespace(value, separator)
    NamingStrategy(Some(namespace))
  }

}
