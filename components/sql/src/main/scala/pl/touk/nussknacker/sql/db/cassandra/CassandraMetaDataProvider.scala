package pl.touk.nussknacker.sql.db.cassandra

import pl.touk.nussknacker.sql.db.MetaDataProviderUtils
import pl.touk.nussknacker.sql.db.schema._

import java.sql.Connection

class CassandraMetaDataProvider(getConnection: () => Connection) extends JdbcMetaDataProvider(getConnection) {

  private val queryHelper = new CassandraQueryHelper(getConnection)

  override def getQueryMetaData(query: String): TableMetaData = throw new NotImplementedError(
    "Generic query typing is not implemented for Cassandra"
  )

  override def getTableMetaData(tableName: String): TableMetaData = {
    val tableDefinition = queryHelper.fetchTableMeta(tableName)
    MetaDataProviderUtils.createTableMetaData(tableName, tableDefinition, getConnection)
  }

}
