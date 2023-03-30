package pl.touk.nussknacker.engine.flink.util.transformer.aggregate

import com.typesafe.config.Config
import net.ceedubs.ficus.readers.ValueReader

import java.time.ZoneId


object AggregateWindowsConfig {
  val Default = AggregateWindowsConfig(dailyWindowsAlignZoneId = None)

  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._

  private implicit val zoneIdValueReader: ValueReader[ZoneId] = ValueReader[String].map(value => ZoneId.of(value))

  def loadOrDefault(config: Config): AggregateWindowsConfig = {
    config.getAs[AggregateWindowsConfig]("aggregateWindowsConfig").getOrElse(AggregateWindowsConfig.Default)
  }
}

case class AggregateWindowsConfig(dailyWindowsAlignZoneId: Option[ZoneId])