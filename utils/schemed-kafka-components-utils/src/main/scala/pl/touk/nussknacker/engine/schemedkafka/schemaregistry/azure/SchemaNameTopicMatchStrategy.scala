package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.azure

import com.google.common.base.CaseFormat

// TODO: It probable should be configurable: e.g would be nice to have possibility to define static topic -> schemaName map in config
//       Thanks to that would be possible to use existing schemas that not follow our convention in Nussknacker.
//       Also in case of ambiguity (>1 schemas with only different namespaces), we could pick the correct one schema.
object SchemaNameTopicMatchStrategy {

  val KeySuffix = "Key"
  val ValueSuffix = "Value"
  def topicNameFromKeySchemaName(schemaName: String): Option[String] = FullSchemaNameDecomposed.unapply(schemaName).filter(_._3).map(_._1)

  def topicNameFromValueSchemaName(schemaName: String): Option[String] = FullSchemaNameDecomposed.unapply(schemaName).filterNot(_._3).map(_._1)

  def keySchemaNameFromTopicName(topicName: String): String = CaseFormat.LOWER_HYPHEN.to(CaseFormat.UPPER_CAMEL, topicName) + KeySuffix

  def valueSchemaNameFromTopicName(topicName: String): String = CaseFormat.LOWER_HYPHEN.to(CaseFormat.UPPER_CAMEL, topicName) + ValueSuffix

  object FullSchemaNameDecomposed {

    private val fullyQualifiedRegex = "^(.*)\\.[^.]*$".r

    // Decompose schema (full) name to: topicName, namespace and isKey
    def unapply(schemaFullName: String): Option[(String, Option[String], Boolean)] = {
      // Only schemas ends with Key or Value will be recognized - we assume that other schemas are for other purpose
      // and won't appear on any Nussknacker lists.
      if (schemaFullName.endsWith(KeySuffix)) {
        val (topicName, namespace) = extractTopicNameAndNamespace(schemaFullName, KeySuffix)
        Some(topicName, namespace, true)
      } else if (schemaFullName.endsWith(ValueSuffix)) {
        val (topicName, namespace) = extractTopicNameAndNamespace(schemaFullName, ValueSuffix)
        Some(topicName, namespace, false)
      } else {
        None
      }
    }

    private def extractTopicNameAndNamespace(schemaFullName: String, suffix: String): (String, Option[String]) = {
      val schemaFullNameWithoutSuffix = schemaFullName.replaceFirst(suffix + "$", "")
      // We remove namespace from topic name because we don't want to force ppl to create technically looking event hub names
      // Also event hubs already has dedicated namespaces
      val cleanedSchemaName = schemaFullNameWithoutSuffix.replaceFirst("^.*\\.", "")
      // Topics (event hubs) can have capital letters but generally it is a good rule to not use them in topic names to avoid
      // mistakes in interpretation shortcuts and so on - similar like with sql tables
      val topicName = CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_HYPHEN, cleanedSchemaName)
      val namespace = fullyQualifiedRegex.findFirstMatchIn(schemaFullNameWithoutSuffix).map { m =>
        m.group(1)
      }
      (topicName, namespace)
    }

  }

}
