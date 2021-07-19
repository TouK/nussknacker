package pl.touk.nussknacker.sql.db.schema

import java.sql.Connection

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
}

case class DialectMetaData(identifierQuote: String)

class SqlDialect(metaData: DialectMetaData) {

  def quoteIdentifier(identifier: String): String =
    metaData.identifierQuote + identifier + metaData.identifierQuote
}

case class DbParameterMetaData(parameterCount: Int)

case class QueryMetaData(tableDefinition: TableDefinition, dbParameterMetaData: DbParameterMetaData)