package pl.touk.nussknacker.sql.db.cassandra

import pl.touk.nussknacker.sql.db.MetaDataProviderUtils
import pl.touk.nussknacker.sql.db.query.UpdateResultStrategy
import pl.touk.nussknacker.sql.db.schema._

import java.sql.Connection
import scala.util.Using

class CassandraMetaDataProvider(getConnection: () => Connection) extends JdbcMetaDataProvider(getConnection) {

  private val queryHelper = new CassandraQueryHelper(getConnection)

  override def getQueryMetaData(query: String, resultStrategyName: String): TableMetaData = {
    val updateResultStrategyName = UpdateResultStrategy.name
    if (resultStrategyName != updateResultStrategyName) {
      // TODO_PAWEL maybe only this one option should be available in dropdown in ui in this case?
      throw new NotImplementedError(
        s"Generic query typing is not implemented for Cassandra. You can use only '$updateResultStrategyName' result strategy because it does not require typing"
      )
    }
    Using.resource(getConnection()) { connection =>
      Using.resource(connection.prepareStatement(query)) { statement =>
        TableMetaData(
          None, // standard implementation also puts here none when query is update
          DbParameterMetaData(statement.getParameterMetaData.getParameterCount)
        )
      }
    }
  }

  override def getTableMetaData(tableName: String): TableMetaData = {
    val tableDefinition = queryHelper.fetchTableMeta(tableName)
    MetaDataProviderUtils.createTableMetaData(tableName, tableDefinition, getConnection)
  }

}
