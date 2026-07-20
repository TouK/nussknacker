package utils

/**
 * Utility to detect SQL statements that may be missing explicit schema qualifiers.
 * 
 * This detector uses heuristic pattern matching to identify SQL statements where
 * database objects (tables, views, etc.) appear to be referenced without schema
 * qualification. It analyzes SQL tokens to find object references that follow 
 * certain keywords (like SELECT, FROM, JOIN etc.) and checks if they include
 * an explicit schema prefix.
 * 
 * Note: This is a best-effort detection mechanism and may produce:
 * - False positives: Some valid SQL constructs may be incorrectly flagged as
 *   missing schema qualifiers
 * - False negatives: Some unqualified object references may not be detected
 *   due to SQL complexity or edge cases
 * 
 * The detection logic may need periodic updates and tweaks as new SQL patterns
 * and edge cases are discovered in production use.
 */

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
      case (Some("update"), token) if token.toLowerCase == "set" => None
      case (Some("create"), token) if token.toLowerCase == "or"  => Some("or")
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

  // match "name", "schema"."name" or schema.name() (without ", with ", with ' or `)
  private val dbObjectPattern = (
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

  private def isLikelyDbObject(token: String): Boolean = {
    dbObjectPattern.pattern.matcher(token).matches()
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
    "replace",
    "conflict",
  )

}
