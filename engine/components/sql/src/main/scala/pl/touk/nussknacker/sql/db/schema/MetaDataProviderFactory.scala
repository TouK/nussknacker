package pl.touk.nussknacker.sql.db.schema

import pl.touk.nussknacker.sql.db.pool.DBPoolConfig

trait MetaDataProviderFactory {
  def create(dbPoolConfig: DBPoolConfig): DbMetaDataProvider
}
