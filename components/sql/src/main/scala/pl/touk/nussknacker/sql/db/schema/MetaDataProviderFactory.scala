package pl.touk.nussknacker.sql.db.schema

import pl.touk.nussknacker.engine.util.ThreadUtils
import pl.touk.nussknacker.sql.DbEnricherName
import pl.touk.nussknacker.sql.db.ignite.IgniteMetaDataProvider
import pl.touk.nussknacker.sql.db.pool.{DBPoolConfig, HikariDataSourceFactory}

import java.sql.Connection

object MetaDataProviderFactory {
  private val igniteDriverPrefix = "org.apache.ignite.IgniteJdbc"

  def create(dbPoolConfig: DBPoolConfig, dbEnricherName: DbEnricherName): JdbcMetaDataProvider = {
    val ds = ThreadUtils.withContextClassLoader(getClass.getClassLoader) {
      HikariDataSourceFactory(
        dbPoolConfig.copy(
          // We always keep one idle connection to decrease the time needed to compile a node
          initialSize = 1,
          // We limit the pool to 1 connection instead of using original dbPoolConfig.maxTotal because we
          // have two separate pools (one for metadata and one for runtime) and we want to reduce the risk
          // that we exceed the limit of connections
          maxTotal = 1
        ),
        poolName = dbEnricherName.value + "-metadata"
      )
    }
    val getConnection: () => Connection = () => {
      val conn = ds.getConnection
      dbPoolConfig.schema.foreach(conn.setSchema)
      conn
    }
    dbPoolConfig.driverClassName match {
      case className if className.startsWith(igniteDriverPrefix) => new IgniteMetaDataProvider(getConnection)
      case _                                                     => new JdbcMetaDataProvider(getConnection)
    }
  }

}
