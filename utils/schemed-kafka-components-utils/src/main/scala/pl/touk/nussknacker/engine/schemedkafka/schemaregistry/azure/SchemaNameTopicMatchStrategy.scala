package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.azure

import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.azure.SchemaNameTopicMatchStrategy.{
  FullSchemaNameDecomposed,
  sanitize
}

// TODO: It probable should be configurable: e.g would be nice to have possibility to define static topic -> schemaName map in config
//       Thanks to that it would be possible to use existing schemas that not follow our convention in Nussknacker.
//       Also in case of ambiguity (>1 schemas with only different namespaces), we could pick the correct one schema.
// TODO: Return validated matches: reject duplicate matches, e.g. schema FooBarValue matches topics foo-bar and foo.baz
class SchemaNameTopicMatchStrategy(referenceTopicList: List[String]) {

  /**
    * List all reference topics matching schema names.
    */
  def matchAllTopics(fullSchemaNames: List[String], isKey: Boolean): List[String] = {
    fullSchemaNames.collect { case _ @FullSchemaNameDecomposed(coreName, _, `isKey`) =>
      referenceTopicList.collectFirst { case topicName if sanitize(topicName) == coreName => topicName }
    }.flatten
  }

  /**
    * List all schemas matching reference topics.
    */
  def matchAllSchemas(fullSchemaNames: List[String], isKey: Boolean): List[String] = {
    fullSchemaNames.collect {
      case fullSchemaName @ FullSchemaNameDecomposed(coreName, _, `isKey`)
          if referenceTopicList.map(sanitize).contains(coreName) =>
        fullSchemaName
    }
  }

}

object SchemaNameTopicMatchStrategy {

  val KeySuffix   = "Key"
  val ValueSuffix = "Value"

  def apply(referenceTopicList: List[String] = Nil): SchemaNameTopicMatchStrategy = new SchemaNameTopicMatchStrategy(
    referenceTopicList
  )

  def keySchemaNameFromTopicName(topicName: String): String = schemaNameFromTopicName(topicName, isKey = true)

  def valueSchemaNameFromTopicName(topicName: String): String = schemaNameFromTopicName(topicName, isKey = false)

  def schemaNameFromTopicName(topicName: String, isKey: Boolean): String = {
    val suffix = if (isKey) KeySuffix else ValueSuffix
    sanitize(topicName) + suffix
  }

  def sanitize(name: String): String = name.replaceAll("\\W", " ").toLowerCase.split(" ").map(_.capitalize).mkString("")

  object FullSchemaNameDecomposed {

    private def fullSchemaNamePattern(suffix: String) = ("^(.*\\.)?([^.]*)" + suffix + "$").r
    private val namespaceAndNameKey                   = fullSchemaNamePattern(KeySuffix)
    private val namespaceAndNameValue                 = fullSchemaNamePattern(ValueSuffix)

    /**
      * Decompose schema (full) name to: topicName, namespace (optional) and isKey.
      *
      * Only schemas ends with Key or Value will be recognized - we assume that other schemas are for other purpose
      * and won't appear on any Nussknacker lists.
      * We remove namespace from topic name because we don't want to force ppl to create technically looking event hub names
      * Also event hubs already has dedicated namespaces.
      * Topics (event hubs) can have capital letters but generally it is a good rule to not use them in topic names to avoid
      * mistakes in interpretation shortcuts and so on - similar like with sql tables.
      *
      * For example: full schema name "some.optional.namespace.CoreSchemaNameValue" is decomposed into
      * <ul>
      * <li>name = CoreSchemaName</li>
      * <li>namespace = some.optional.namespace</li>
      * <li>"isKey" flag = true</li>
      * </ul>
      */
    def unapply(fullSchemaName: String): Option[(String, Option[String], Boolean)] = {
      fullSchemaName match {
        case namespaceAndNameKey(null, core)        => Some((core, None, true))
        case namespaceAndNameKey(namespace, core)   => Some((core, Some(namespace.replaceAll("\\.$", "")), true))
        case namespaceAndNameValue(null, core)      => Some((core, None, false))
        case namespaceAndNameValue(namespace, core) => Some((core, Some(namespace.replaceAll("\\.$", "")), false))
        case _                                      => None
      }
    }

  }

}
