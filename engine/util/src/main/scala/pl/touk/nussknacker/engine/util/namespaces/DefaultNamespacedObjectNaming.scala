package pl.touk.nussknacker.engine.util.namespaces

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.namespaces._

import scala.util.matching.Regex

/*
  This is default ObjectNaming, it assumes that namespace is configured via configuration. If it's not configured - we leave
  object names untouched
 */
object DefaultNamespacedObjectNaming extends ObjectNaming with LazyLogging {

  private final val NamespacePath = "namespace"

  /**
   * We don't want create Regex each time as it's expensive. Instead we store simple mapping.
   * TODO: Consider replacing it by caffeine if we'll need many namespaces
   */
  protected val regexMap = Map.empty[String, Regex]

  override def prepareName(originalName: String, config: Config, namingContext: NamingContext): String =
    namingContext.usageKey match {
      case KafkaUsageKey | FlinkUsageKey if config.hasPath(NamespacePath) =>
        val namespace = config.getString(NamespacePath)
        logger.debug(s"Prepending $namespace to $originalName for ${namingContext.usageKey}")
        s"${namespace}_$originalName"
      case _ =>
        logger.debug(s"Namespace has not been configured, $originalName left")
        originalName
    }

  override def objectNamingParameters(originalName: String, config: Config, namingContext: NamingContext): Option[ObjectNamingParameters] = {
    namingContext.usageKey match {
      case KafkaUsageKey | FlinkUsageKey if config.hasPath(NamespacePath) =>
        val namespace = config.getString(NamespacePath)
        Some(DefaultNamespacedObjectNamingParameters(originalName, namespace))
      case _ => None
    }
  }

  override def decodeName(preparedName: String, config: Config, namingContext: NamingContext): Option[String] =
    namingContext.usageKey match {
      case KafkaUsageKey | FlinkUsageKey if config.hasPath(NamespacePath) =>
        val patternMatcher = namespacePattern(config.getString(NamespacePath))
        preparedName match {
          case patternMatcher(value) => Some(value)
          case _ => Option.empty
        }
      case _ => Option.empty
    }

  private def namespacePattern(namespace: String): Regex =
    regexMap.getOrElse(namespace, s"${namespace}_(.*)".r)

}

case class DefaultNamespacedObjectNamingParameters(originalName: String,
                                                   namespace: String) extends ObjectNamingParameters {
  override def toTags: Map[String, String] = {
    Map(
      "originalProcessName" -> originalName,
      "namespace" -> namespace
    )
  }
}
