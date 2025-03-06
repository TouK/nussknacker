package utils

object SchemaInSqlDetector {

  def isMissingRequiredSchema(sqlStatement: String): Boolean = {
    val knownSafeKeywords = Set("SET search_path", "DROP SCHEMA", "CREATE SCHEMA", "ALTER SCHEMA")
    val lowerQuery        = sqlStatement.trim.toLowerCase

    if (lowerQuery.isEmpty) {
      false
    } else if (knownSafeKeywords.exists(lowerQuery.startsWith)) {
      false
    } else {
      detectUnqualifiedObjects(sqlStatement)
    }
  }

  private def detectUnqualifiedObjects(sqlQuery: String): Boolean = {
    val tokens = tokenizeSql(sqlQuery)

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
    sql.split("\\s+").toList
  }

  private def isLikelyDbObject(token: String): Boolean = {
    // match "name", "schema"."name" or schema.name() (without ", with ", with ' or `)
    val pattern = (
      "^(" +
        // 1) Unquoted identifier(s)
        "[a-zA-Z_][a-zA-Z0-9_]*(?:\\.[a-zA-Z_][a-zA-Z0-9_]*)*" +
        "|" +
        // 2) Double-quoted identifier(s)
        "\"[a-zA-Z_][a-zA-Z0-9_]*\"(?:\\.\"[a-zA-Z_][a-zA-Z0-9_]*\")*" +
        "|" +
        // 3) Single-quoted identifier(s)
        "'[a-zA-Z_][a-zA-Z0-9_]*'(?:\\.'[a-zA-Z_][a-zA-Z0-9_]*')*" +
        "|" +
        // 4) Backtick-quoted identifier(s)
        "`[a-zA-Z_][a-zA-Z0-9_]*`(?:\\.`[a-zA-Z_][a-zA-Z0-9_]*`)*" +
        ")" +
        // Optionally allow empty parentheses "()"
        "(?:\\(\\))?" +
        "$"
    ).r
    pattern.matches(token)
  }

  private def hasExplicitSchema(token: String): Boolean = {
    val unquoted = token.replaceAll("\"", "")
    val parts    = unquoted.split("\\.")
    parts.length > 1
  }

  private val schemaRequiredKeywords = Set(
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
    "or",
    "replace"
  )

}
