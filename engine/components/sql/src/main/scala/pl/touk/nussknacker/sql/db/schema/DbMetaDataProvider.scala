package pl.touk.nussknacker.sql.db.schema

import java.sql.Connection
import java.util
import java.util.Arrays.asList

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
    val tables = connection.getMetaData.getTables(null, "PUBLIC", "%", Array("TABLE").map(_.toString))
    val results = new util.ArrayList[String]()
    val columnNameIndex = 3
    while (tables.next()) {
      val str: String = tables.getString(columnNameIndex)
      results add str
    }
    SchemaDefinition(results)
  }
}

case class DialectMetaData(identifierQuote: String)

case class SchemaDefinition(tables: java.util.List[String])

object SchemaDefinition {
  def empty(): SchemaDefinition = SchemaDefinition(asList())
}

class SqlDialect(metaData: DialectMetaData) {

  def quoteIdentifier(identifier: String): String =
    metaData.identifierQuote + identifier + metaData.identifierQuote
}

case class DbParameterMetaData(parameterCount: Int)

case class QueryMetaData(tableDefinition: TableDefinition, dbParameterMetaData: DbParameterMetaData)
