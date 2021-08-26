package pl.touk.nussknacker.sql.db.schema

import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import pl.touk.nussknacker.sql.DbEnricherConfig
import pl.touk.nussknacker.sql.db.pool.DBPoolConfig

import java.sql.DriverManager

class JdbcMetaDataProviderFactory {

  def getMetaDataProvider(dbPoolConfig: DBPoolConfig): JdbcMetaDataProvider = {
    new JdbcMetaDataProvider(() => DriverManager.getConnection(dbPoolConfig.url, dbPoolConfig.username, dbPoolConfig.password))
  }
}
