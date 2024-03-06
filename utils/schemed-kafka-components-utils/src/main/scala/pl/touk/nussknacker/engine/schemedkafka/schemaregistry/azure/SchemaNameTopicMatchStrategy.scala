package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.azure

import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.azure.SchemaNameTopicMatchStrategy.{
  FullSchemaNameDecomposed,
  toSchemaNamingConvention
}

// TODO: It probable should be configurable: e.g would be nice to have possibility to define static topic -> schemaName map in config
//       Thanks to that it would be possible to use existing schemas that not follow our convention in Nussknacker.
//       Also in case of ambiguity (>1 schemas with only different namespaces), we could pick the correct one schema.
class SchemaNameTopicMatchStrategy(referenceTopicList: List[String]) {

  /**
    * List all reference topics matching schema names.
    */
  def getAllMatchingTopics(fullSchemaNames: List[String], isKey: Boolean): List[String] = {
    val coreSchemaNames = fullSchemaNames.collect { case _ @FullSchemaNameDecomposed(coreName, `isKey`) =>
      coreName
    }
    referenceTopicList.collect {
      case topicName if coreSchemaNames.contains(toSchemaNamingConvention(topicName)) => topicName
    }
  }

}

object SchemaNameTopicMatchStrategy {

  val KeySuffix   = "Key"
  val ValueSuffix = "Value"

  def apply(referenceTopicList: List[String] = Nil): SchemaNameTopicMatchStrategy = new SchemaNameTopicMatchStrategy(
    referenceTopicList
  )

  def valueSchemaNameFromTopicName(topicName: String): String = schemaNameFromTopicName(topicName, isKey = false)

  def schemaNameFromTopicName(topicName: String, isKey: Boolean): String = {
    val suffix = if (isKey) KeySuffix else ValueSuffix
    toSchemaNamingConvention(topicName) + suffix
  }

  /**
    * List all schemas matching reference topics.
    */
  def getMatchingSchemas(topicName: String, fullSchemaNames: List[String], isKey: Boolean): List[String] = {
    fullSchemaNames.collect {
      case fullSchemaName @ FullSchemaNameDecomposed(coreName, `isKey`)
          if toSchemaNamingConvention(topicName) == coreName =>
        fullSchemaName
    }
  }

  /**
    * Transforms topic name to schema naming convention.
    * @see https://nussknacker.io/documentation/docs/integration/KafkaIntegration/#association-between-schema-with-topic
    */
  def toSchemaNamingConvention(topicName: String): String =
    topicName.toLowerCase.replaceAll("\\W+", " ").split(" ").map(_.capitalize).mkString("")

  object FullSchemaNameDecomposed {

    private def fullSchemaNamePattern(suffix: String) = ("^(?:.*\\.)?([^.]*)" + suffix + "$").r
    private val namespaceAndNameKey                   = fullSchemaNamePattern(KeySuffix)
    private val namespaceAndNameValue                 = fullSchemaNamePattern(ValueSuffix)

    /**
      * Extracts core schema name and isKey flag from full schema name.
      *
      * Only schemas ends with Key or Value will be recognized - we assume that other schemas are for other purpose
      * and won't appear on any Nussknacker lists.
      * We remove namespace from topic name because we don't want to force ppl to create technically looking event hub names
      * Also event hubs already has dedicated namespaces.
      * Topics (event hubs) can have capital letters but generally it is a good rule to not use them in topic names to avoid
      * mistakes in interpretation shortcuts and so on - similar like with sql tables.
      *
      * For example: for full schema name "some.optional.namespace.CoreSchemaNameValue"
      * it extracts: name = CoreSchemaName and "isKey" flag = true.
      */
    def unapply(fullSchemaName: String): Option[(String, Boolean)] = {
      fullSchemaName match {
        case namespaceAndNameKey(core)   => Some((core, true))
        case namespaceAndNameValue(core) => Some((core, false))
        case _                           => None
      }
    }

  }

}
