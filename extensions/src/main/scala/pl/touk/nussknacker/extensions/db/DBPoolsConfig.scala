package pl.touk.nussknacker.extensions.db

import com.typesafe.config.Config

object DBPoolsConfig {

  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._

  final val configPath: String = "dbPools"

  def apply(config: Config): Map[String, DBPoolConfig] =
    config.as[Map[String, DBPoolConfig]](configPath)
}
