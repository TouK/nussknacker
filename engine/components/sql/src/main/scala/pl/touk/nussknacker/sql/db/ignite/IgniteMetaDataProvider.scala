package pl.touk.nussknacker.sql.db.ignite

import pl.touk.nussknacker.engine.sql.columnmodel.CreateColumnModel.ClazzToSqlType
import pl.touk.nussknacker.engine.sql.{Column, ColumnModel, HsqlSqlQueryableDataBase}
import pl.touk.nussknacker.sql.db.schema._

import java.sql.Connection
import scala.util.Using

class IgniteMetaDataProvider(getConnection: () => Connection) extends JdbcMetaDataProvider(getConnection) {
  private def query(tableName: String) = s"SELECT * FROM $tableName"

  private val queryHelper = new IgniteQueryHelper(getConnection)

  override def getQueryMetaData(query: String): TableMetaData = executeInHsql(query, queryHelper.fetchTablesMeta) {
    db => return TableMetaData(TableDefinition(db.resultSetMetaData), DbParameterMetaData(db.parameterMetaData.getParameterCount))
  }

  override def getTableMetaData(tableName: String): TableMetaData = getQueryMetaData(query(tableName))

  override def getSchemaDefinition(): SchemaDefinition = SchemaDefinition(queryHelper.fetchTablesMeta.map(_.tableName))

  private def executeInHsql(query: String, tablesMeta: List[TableCatalog.TableMeta])(function: HsqlSqlQueryableDataBase => TableMetaData): TableMetaData =
    Using.resource(new HsqlSqlQueryableDataBase(query, columnModel(tablesMeta))) { function }

  private def columnModel(tablesMeta: List[TableCatalog.TableMeta]): Map[String, ColumnModel] = tablesMeta.map(table => (table.tableName,
    ColumnModel(table.columnTyping.map(typing =>
      Column(typing.name,
        ClazzToSqlType.convert(typing.name, typing.typingResult, typing.normalizedClass.getName).get
      )
    )))
  ).toMap
}
