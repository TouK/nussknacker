package pl.touk.nussknacker.sql.db.schema

import pl.touk.nussknacker.sql.db.ignite.IgniteMetaDataProvider
import pl.touk.nussknacker.sql.db.pool.DBPoolConfig
import pl.touk.nussknacker.sql.db.schema.MetaDataProviderFactory.igniteDriverPrefix

import java.sql.{Connection, DriverManager}

object MetaDataProviderFactory {
  private val igniteDriverPrefix = "org.apache.ignite.IgniteJdbc"
}

class MetaDataProviderFactory {

  def create(dbPoolConfig: DBPoolConfig): JdbcMetaDataProvider = {
    val getConnection: () => Connection = () => DriverManager.getConnection(dbPoolConfig.url, dbPoolConfig.username, dbPoolConfig.password)

    dbPoolConfig.driverClassName match {
      case className if className.startsWith(igniteDriverPrefix) => new IgniteMetaDataProvider(getConnection)
      case _  => new JdbcMetaDataProvider(getConnection)
    }
  }
}
