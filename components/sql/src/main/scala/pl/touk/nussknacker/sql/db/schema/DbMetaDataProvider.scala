package pl.touk.nussknacker.sql.db.schema

trait DbMetaDataProvider extends Serializable {
  def getDialectMetaData: DialectMetaData

  def getTableMetaData(tableName: String): TableMetaData

  def getQueryMetaData(query: String): TableMetaData

  def getSchemaDefinition(): SchemaDefinition
}

final case class DialectMetaData(identifierQuote: String)

final case class SchemaDefinition(tables: List[String])

object SchemaDefinition {
  def empty(): SchemaDefinition = SchemaDefinition(List())
}

class SqlDialect(metaData: DialectMetaData) extends Serializable {

  def quoteIdentifier(identifier: String): String =
    metaData.identifierQuote + identifier + metaData.identifierQuote
}

final case class DbParameterMetaData(parameterCount: Int)

final case class TableMetaData(tableDefinition: Option[TableDefinition], dbParameterMetaData: DbParameterMetaData)
