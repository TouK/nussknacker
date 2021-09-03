package pl.touk.nussknacker.sql.db.schema

trait DbMetaDataProvider {
  def getDialectMetaData: DialectMetaData

  def getTableMetaData(tableName: String): TableMetaData

  def getQueryMetaData(query: String): TableMetaData

  def getSchemaDefinition(): SchemaDefinition
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

case class TableMetaData(tableDefinition: TableDefinition, dbParameterMetaData: DbParameterMetaData)
