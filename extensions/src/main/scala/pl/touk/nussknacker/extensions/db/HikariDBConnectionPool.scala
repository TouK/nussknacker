package pl.touk.nussknacker.extensions.db
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}

import java.sql.Connection

object HikariDBConnectionPool {

  def apply(conf: DBPoolConfig): HikariDBConnectionPool = {
    val hikariConf = new HikariConfig()
    hikariConf.setJdbcUrl(conf.url)
    hikariConf.setUsername(conf.username)
    hikariConf.setPassword(conf.password)
    hikariConf.setMaximumPoolSize(conf.maxTotal)
    conf.connectionProperties.foreach { case (name, value) =>
      hikariConf.addDataSourceProperty(name, value)
    }
    new HikariDBConnectionPool(new HikariDataSource(hikariConf))
  }
}

class HikariDBConnectionPool(ds: HikariDataSource) extends DBConnectionPool {

  override def getConnection: Connection = ds.getConnection
}
