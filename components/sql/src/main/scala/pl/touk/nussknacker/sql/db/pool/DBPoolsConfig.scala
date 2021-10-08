package pl.touk.nussknacker.sql.db.pool

import com.typesafe.config.Config

object DBPoolsConfig {

  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._

  final val configPath: String = "dbPools"

  def apply(config: Config): Map[String, DBPoolConfig] = config
    .getAs[Map[String, DBPoolConfig]](configPath)
    .getOrElse(Map.empty)
}
