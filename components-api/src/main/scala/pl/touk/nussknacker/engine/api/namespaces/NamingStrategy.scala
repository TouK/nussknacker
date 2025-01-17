package pl.touk.nussknacker.engine.api.namespaces

import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._

final case class Namespace(value: String, separator: String)

final case class NamingStrategy(
    private val defaultNamespace: Option[Namespace],
    private val overrides: Map[NamespaceContext, Namespace]
) {

  if (overrides.nonEmpty) {
    require(defaultNamespace.nonEmpty, "Overrides cannot be configured without default namespace")
  }

  def prepareName(name: String, namespaceContext: NamespaceContext): String = findNamespace(namespaceContext) match {
    case Some(Namespace(value, separator)) => s"$value$separator$name"
    case None                              => name
  }

  def decodeName(name: String, namespaceContext: NamespaceContext): Option[String] = findNamespace(
    namespaceContext
  ) match {
    case Some(Namespace(value, separator)) if name.startsWith(s"$value$separator") =>
      Some(name.stripPrefix(s"$value$separator"))
    case Some(Namespace(_, _)) => None
    case None                  => Some(name)
  }

  def findNamespace(namespaceContext: NamespaceContext): Option[Namespace] = {
    overrides.get(namespaceContext).orElse(defaultNamespace)
  }

}

object NamingStrategy {
  private val defaultNamespaceSeparator = "_"
  private val namespacePath             = "namespace"
  private val namespaceSeparatorPath    = "namespaceSeparator"
  private val valueKey                  = "value"
  private val separatorKey              = "separator"
  private val overridesKey              = "overrides"

  val Disabled: NamingStrategy = NamingStrategy(None, Map.empty)

  def fromConfig(modelConfig: Config): NamingStrategy = {
    if (modelConfig.hasPath(s"$namespacePath.$valueKey")) {
      readWithOverrides(modelConfig.getConfig(namespacePath))
    } else if (modelConfig.hasPath(namespacePath)) {
      readSimple(modelConfig) q
    } else {
      Disabled
    }
  }

  private def readWithOverrides(namespaceConfig: Config): NamingStrategy = {
    def readNamespace(config: Config) = {
      val value     = config.as[String](valueKey)
      val separator = config.getAs[String](separatorKey).getOrElse(defaultNamespaceSeparator)
      Namespace(value, separator)
    }

    val defaultNamespace = readNamespace(namespaceConfig)
    val overrides = if (namespaceConfig.hasPath(overridesKey)) {
      NamespaceContext.values
        .filter(nc => namespaceConfig.hasPath(s"$overridesKey.${nc.entryName}"))
        .map(nc => nc -> readNamespace(namespaceConfig.getConfig(s"$overridesKey.${nc.entryName}")))
        .toMap
    } else {
      Map.empty[NamespaceContext, Namespace]
    }
    NamingStrategy(Some(defaultNamespace), overrides)
  }

  private def readSimple(modelConfig: Config): NamingStrategy = {
    val value     = modelConfig.as[String](namespacePath)
    val separator = modelConfig.getAs[String](namespaceSeparatorPath).getOrElse(defaultNamespaceSeparator)
    val namespace = Namespace(value, separator)
    NamingStrategy(Some(namespace), Map.empty)
  }

}
