package pl.touk.nussknacker.sql.db.schema

import pl.touk.nussknacker.sql.db.pool.DBPoolConfig

import java.sql.{Connection, DriverManager}

class JdbcMetaDataProviderFactory extends MetaDataProviderFactory {

  override def create(dbPoolConfig: DBPoolConfig): JdbcMetaDataProvider = {
    val connection: () => Connection = () => DriverManager.getConnection(dbPoolConfig.url, dbPoolConfig.username, dbPoolConfig.password)
    new JdbcMetaDataProvider(connection)
  }
}
