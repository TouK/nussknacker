package pl.touk.nussknacker.sql

import pl.touk.nussknacker.sql.db.pool.DBPoolConfig

final case class DbEnricherConfig(name: String, dbPool: DBPoolConfig, displayDbErrors: Boolean = false)
