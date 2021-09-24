package pl.touk.nussknacker.sql.db.ignite

import pl.touk.nussknacker.engine.sql.{ColumnModel, HsqlSqlQueryableDataBase}
import pl.touk.nussknacker.sql.db.schema._

import java.sql.Connection
import scala.util.Using

class IgniteMetaDataProvider(getConnection: () => Connection) extends JdbcMetaDataProvider(getConnection) {
  private def query(tableName: String) = s"SELECT * FROM $tableName"

  private val queryHelper = new IgniteQueryHelper(getConnection)

  override def getQueryMetaData(query: String): TableMetaData = executeInHsql(query, queryHelper.fetchTablesMeta) {
    db => return TableMetaData(TableDefinition(db.getTypedMap), DbParameterMetaData(db.parameterMetaData.getParameterCount))
  }

  override def getTableMetaData(tableName: String): TableMetaData = getQueryMetaData(query(tableName))

  override def getSchemaDefinition(): SchemaDefinition = SchemaDefinition(queryHelper.fetchTablesMeta.keys.toList)

  private def executeInHsql(query: String, tables: Map[String, ColumnModel])(function: HsqlSqlQueryableDataBase => TableMetaData): TableMetaData =
    Using.resource(new HsqlSqlQueryableDataBase(query, tables)) { function }

}
