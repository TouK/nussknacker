package pl.touk.nussknacker.sql.db.schema

import com.zaxxer.hikari.util.DriverDataSource
import pl.touk.nussknacker.engine.util.ThreadUtils
import pl.touk.nussknacker.sql.db.cassandra.CassandraMetaDataProvider
import pl.touk.nussknacker.sql.db.ignite.IgniteMetaDataProvider
import pl.touk.nussknacker.sql.db.pool.DBPoolConfig
import pl.touk.nussknacker.sql.db.schema.MetaDataProviderFactory.{cassandraDriverName, igniteDriverPrefix}

import java.sql.Connection
import java.util.Properties

object MetaDataProviderFactory {
  private val igniteDriverPrefix  = "org.apache.ignite.IgniteJdbc"
  private val cassandraDriverName = "com.ing.data.cassandra.jdbc.CassandraDriver"
}

class MetaDataProviderFactory {

  def create(dbPoolConfig: DBPoolConfig): JdbcMetaDataProvider = {
    val props = new Properties()
    dbPoolConfig.dataSourceProperties.foreach { case (k, v) =>
      props.put(k, v)
    }
    val ds = ThreadUtils.withThisAsContextClassLoader(getClass.getClassLoader) {
      new DriverDataSource(
        dbPoolConfig.url,
        dbPoolConfig.driverClassName,
        props,
        dbPoolConfig.username,
        dbPoolConfig.password
      )
    }
    val getConnection: () => Connection = () => {
      val conn = ds.getConnection
      dbPoolConfig.schema.foreach(conn.setSchema)
      conn
    }
    dbPoolConfig.driverClassName match {
      case className if className.startsWith(igniteDriverPrefix) => new IgniteMetaDataProvider(getConnection)
      case className if className == cassandraDriverName         => new CassandraMetaDataProvider(getConnection)
      case _                                                     => new JdbcMetaDataProvider(getConnection)
    }
  }

}
