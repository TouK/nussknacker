package pl.touk.nussknacker.sql

import pl.touk.nussknacker.sql.db.pool.DBPoolConfig

case class DbEnricherConfig(name: String, dbPool: DBPoolConfig)
