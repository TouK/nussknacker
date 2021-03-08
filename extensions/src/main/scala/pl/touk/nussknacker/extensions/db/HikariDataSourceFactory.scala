package pl.touk.nussknacker.extensions.db
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}


object HikariDataSourceFactory {

  def apply(conf: DBPoolConfig): HikariDataSource = {
    val hikariConf = new HikariConfig()
    hikariConf.setJdbcUrl(conf.url)
    hikariConf.setUsername(conf.username)
    hikariConf.setPassword(conf.password)
    hikariConf.setMaximumPoolSize(conf.maxTotal)
    conf.connectionProperties.foreach { case (name, value) =>
      hikariConf.addDataSourceProperty(name, value)
    }
    new HikariDataSource(hikariConf)
  }
}