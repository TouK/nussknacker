package pl.touk.nussknacker.sql.db.schema

import com.zaxxer.hikari.util.DriverDataSource
import pl.touk.nussknacker.engine.util.ThreadUtils
import pl.touk.nussknacker.sql.db.ignite.IgniteMetaDataProvider
import pl.touk.nussknacker.sql.db.pool.DBPoolConfig
import pl.touk.nussknacker.sql.db.schema.MetaDataProviderFactory.igniteDriverPrefix

import java.sql.Connection
import java.util.Properties

object MetaDataProviderFactory {
  private val igniteDriverPrefix = "org.apache.ignite.IgniteJdbc"
}

class MetaDataProviderFactory {

  def create(dbPoolConfig: DBPoolConfig): JdbcMetaDataProvider = {
    val props = new Properties()
    dbPoolConfig.connectionProperties.foreach {
      case (k, v) => props.put(k, v)
    }
    val ds = ThreadUtils.withThisAsContextClassLoader(getClass.getClassLoader) {
      new DriverDataSource(dbPoolConfig.url, dbPoolConfig.driverClassName, props, dbPoolConfig.username, dbPoolConfig.password)
    }
    val getConnection: () => Connection = () => ds.getConnection
    dbPoolConfig.driverClassName match {
      case className if className.startsWith(igniteDriverPrefix) => new IgniteMetaDataProvider(getConnection)
      case _  => new JdbcMetaDataProvider(getConnection)
    }
  }
}
