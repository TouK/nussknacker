package pl.touk.nussknacker.engine.api.namespaces

import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._
import pl.touk.nussknacker.engine.api.namespaces.NamingStrategy.namespaceSeparator

final case class NamingStrategy(namespace: Option[String]) {

  private val namespacePattern = namespace.map(ns => s"^$ns$namespaceSeparator(.*)".r)

  def prepareName(name: String): String = namespace match {
    case Some(value) => s"$value$namespaceSeparator$name"
    case None        => name
  }

  def decodeName(name: String): Option[String] = namespacePattern match {
    case Some(pattern) =>
      name match {
        case pattern(originalName) => Some(originalName)
        case _                     => None
      }
    case None => Some(name)
  }

}

object NamingStrategy {
  private val namespaceSeparator = "_"
  private val namespacePath      = "namespace"

  def fromConfig(modelConfig: Config): NamingStrategy = NamingStrategy(modelConfig.getAs[String](namespacePath))

}
