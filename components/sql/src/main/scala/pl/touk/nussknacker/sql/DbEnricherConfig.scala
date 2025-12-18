package pl.touk.nussknacker.sql

import net.ceedubs.ficus.Ficus
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import net.ceedubs.ficus.readers.ValueReader
import pl.touk.nussknacker.sql.db.pool.DBPoolConfig

final case class DbEnricherConfig(name: DbEnricherName, dbPool: DBPoolConfig)

object DbEnricherConfig {

  implicit val valueReader: ValueReader[DbEnricherConfig] = {
    Ficus.configValueReader.map { config =>
      DbEnricherConfig(config.as[DbEnricherName]("name")(DbEnricherName.valueReader), config.as[DBPoolConfig]("dbPool"))
    }
  }

}

final case class DbEnricherName(value: String) {
  override def toString: String = value
}

object DbEnricherName {

  implicit val valueReader: ValueReader[DbEnricherName] = Ficus.stringValueReader.map(DbEnricherName(_))

}
