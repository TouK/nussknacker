package pl.touk.nussknacker.sql.db.schema

import java.sql.{Connection, DatabaseMetaData}

class DbMetaDataProvider(getConnection: () => Connection) {

  def getDialectMetaData(): DialectMetaData = {
    val conn = getConnection()
    try {
      val metaData = conn.getMetaData
      DialectMetaData(metaData.getIdentifierQuoteString)
    } finally conn.close()
  }

  def getQueryMetaData(query: String): QueryMetaData = {
    val conn = getConnection()
    try {
      val statement = conn.prepareStatement(query)
      try {
        QueryMetaData(
          TableDefinition(statement.getMetaData),
          DbParameterMetaData(statement.getParameterMetaData.getParameterCount))
      } finally statement.close()
    } finally conn.close()
  }

  def getSchemaDefinition(): SchemaDefinition = {
    val connection = getConnection()
    try {
      val metaData = connection.getMetaData
      val tables = metaData.getTables(null, getSchemaName(metaData), "%", Array("TABLE").map(_.toString))
      var results = List[String]()
      val columnNameIndex = 3
      while (tables.next()) {
        val str: String = tables.getString(columnNameIndex)
        results = results :+ str
      }
      SchemaDefinition(results)
    } finally connection.close()
  }

  private def getSchemaName(metaData: DatabaseMetaData): String = {
    val resultSet = metaData.getSchemas
    val schemaNameIndex = 2
    //todo here we take the first schema, make it configurable in the future
    if (resultSet.next())
      resultSet.getString(schemaNameIndex)
    else
      null
  }
}

case class DialectMetaData(identifierQuote: String)

case class SchemaDefinition(tables: List[String])

object SchemaDefinition {
  def empty(): SchemaDefinition = SchemaDefinition(List())
}

class SqlDialect(metaData: DialectMetaData) {

  def quoteIdentifier(identifier: String): String =
    metaData.identifierQuote + identifier + metaData.identifierQuote
}

case class DbParameterMetaData(parameterCount: Int)

case class QueryMetaData(tableDefinition: TableDefinition, dbParameterMetaData: DbParameterMetaData)
