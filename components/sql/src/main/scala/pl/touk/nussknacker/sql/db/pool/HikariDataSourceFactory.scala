package pl.touk.nussknacker.sql.db.pool

import com.zaxxer.hikari.{HikariConfig, HikariDataSource}

object HikariDataSourceFactory {

  def apply(conf: DBPoolConfig): HikariDataSource = {
    val hikariConf = new HikariConfig()
    hikariConf.setJdbcUrl(conf.url)
    hikariConf.setUsername(conf.username)
    hikariConf.setPassword(conf.password)
    hikariConf.setMinimumIdle(conf.initialSize)
    hikariConf.setMaximumPoolSize(conf.maxTotal)
    hikariConf.setConnectionTimeout(conf.timeout.toMillis)
    hikariConf.setDriverClassName(conf.driverClassName)
    conf.connectionProperties.foreach { case (name, value) =>
      hikariConf.addDataSourceProperty(name, value)
    }
    new HikariDataSource(hikariConf)
  }
}
