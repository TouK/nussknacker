package pl.touk.nussknacker.sql.db.ignite

import pl.touk.nussknacker.sql.db.MetaDataProviderUtils
import pl.touk.nussknacker.sql.db.schema._

import java.sql.Connection

class IgniteMetaDataProvider(getConnection: () => Connection) extends JdbcMetaDataProvider(getConnection) {

  private val queryHelper = new IgniteQueryHelper(getConnection)

  // TODO_PAWEL probably it can work for updates, look at implementation of this method for cassandra db
  override def getQueryMetaData(query: String, resultStrategyName: String): TableMetaData =
    throw new NotImplementedError(
      "Generic query typing is not implemented for Ignite"
    )

  override def getTableMetaData(tableName: String): TableMetaData = {
    val tableDefinition =
      queryHelper.fetchTablesMeta.getOrElse(tableName, throw new IllegalArgumentException("Table metadata not present"))
    MetaDataProviderUtils.createTableMetaData(tableName, tableDefinition, getConnection)
  }

  override def getSchemaDefinition(): SchemaDefinition = SchemaDefinition(queryHelper.fetchTablesMeta.keys.toList)
}
