package utils

object SchemaInSqlDetector {

  def isMissingRequiredSchema(sqlStatement: String): Boolean = {
    val knownSafeKeywords = Set("SET search_path", "DROP SCHEMA", "CREATE SCHEMA", "ALTER SCHEMA")
    val lowerQuery        = sqlStatement.trim.toLowerCase

    if (knownSafeKeywords.exists(lowerQuery.startsWith)) {
      false
    } else {
      detectUnqualifiedObjects(sqlStatement)
    }
  }

  private def detectUnqualifiedObjects(sqlQuery: String): Boolean = {
    val tokens = tokenizeSql(sqlQuery)

    val schemaRequiredKeywords = Set(
      "select",
      "insert",
      "update",
      "delete",
      "into",
      "from",
      "with",
      "as",
      "create",
      "alter",
      "drop",
      "table",
      "index",
      "sequence",
      "view",
      "materialized",
      "function",
      "procedure",
      "type",
      "domain",
      "extension",
      "trigger",
      "policy",
      "aggregate",
      "operator",
      "collation",
      "join",
      "truncate",
      "on",
      "references",
      "foreign",
    )

    tokens.foldLeft(Option.empty[String]) {
      case (Some(_), token) if !schemaRequiredKeywords.contains(token.toLowerCase) && isLikelyDbObject(token) =>
        if (!hasExplicitSchema(token)) return true
        None
      case (_, token) if schemaRequiredKeywords.contains(token.toLowerCase) =>
        Some(token.toLowerCase)
      case _ =>
        None
    }

    false
  }

  private def tokenizeSql(sql: String): List[String] = {
    val regex = """("[^"]+"(?:\."[^"]+")*|'[^']+'(?:\.'[^']+')*|`[^`]+`(?:\.`[^`]+`)*|\S+)""".r
    regex.findAllIn(sql).toList
  }

  private def isLikelyDbObject(token: String): Boolean = {
    val unquotedPattern = "^[a-zA-Z_][a-zA-Z0-9_]*$"          // Standard SQL object names
    val quotedPattern   = """^"[^"]+"$|^'[^']+'$|^`[^`]+`$""" // Quoted identifiers
    val functionPattern = """^("?[a-zA-Z_][a-zA-Z0-9_]*"?\.)?"?[a-zA-Z_][a-zA-Z0-9_]*"?\(\)$"""

    token.matches(unquotedPattern) || token.matches(quotedPattern) || token.matches(functionPattern)
  }

  private def hasExplicitSchema(token: String): Boolean = {
    val unquoted = token.replaceAll("\"", "")
    val parts    = unquoted.split("\\.")
    parts.length > 1
  }

}
