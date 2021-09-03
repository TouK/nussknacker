package pl.touk.nussknacker.sql.db.ignite

import pl.touk.nussknacker.sql.db.pool.DBPoolConfig
import pl.touk.nussknacker.sql.db.schema.{DbMetaDataProvider, MetaDataProviderFactory}

import java.sql.DriverManager

class IgniteMetaDataProviderFactory extends MetaDataProviderFactory {
  override def create(dbPoolConfig: DBPoolConfig): DbMetaDataProvider =
    new IgniteMetaDataProvider(() => DriverManager.getConnection(dbPoolConfig.url, dbPoolConfig.username, dbPoolConfig.password))
}
