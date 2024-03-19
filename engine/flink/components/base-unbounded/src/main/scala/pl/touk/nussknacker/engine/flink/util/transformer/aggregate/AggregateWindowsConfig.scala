package pl.touk.nussknacker.engine.flink.util.transformer.aggregate

import com.typesafe.config.Config
import net.ceedubs.ficus.readers.ValueReader

object AggregateWindowsConfig {
  val Default = AggregateWindowsConfig(tumblingWindowsOffset = None)

  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  private implicit val durationValueReader: ValueReader[java.time.Duration] =
    ValueReader[String].map(value => java.time.Duration.parse(value))

  def loadOrDefault(config: Config): AggregateWindowsConfig = {
    config.getAs[AggregateWindowsConfig]("aggregateWindowsConfig").getOrElse(AggregateWindowsConfig.Default)
  }

}

case class AggregateWindowsConfig(tumblingWindowsOffset: Option[java.time.Duration])
