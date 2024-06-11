package pl.touk.nussknacker.ui.db.timeseries.questdb

import com.typesafe.config.Config
import pl.touk.nussknacker.ui.config.Implicits.parseOptionalConfig
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._

import scala.concurrent.duration.{DurationInt, FiniteDuration}

case class QuestDbConfig(
    enabled: Boolean,
    // This should be configured if nu will be run in multi instances.
    // If there are many instances we should change diagrams on grafana.
    instanceId: String,
    directory: Option[String],
    flushTaskDelay: FiniteDuration,
    retentionTaskDelay: FiniteDuration,
    poolConfig: QuestDbPoolConfig
)

case class QuestDbPoolConfig(corePoolSize: Int, maxPoolSize: Int, keepAliveTimeInSeconds: Long, queueCapacity: Int)

object QuestDbConfig {

  def apply(config: Config): QuestDbConfig = new QuestDbConfig(
    enabled = parseOptionalConfig[Boolean](config, "questDbSettings.enabled").getOrElse(true),
    instanceId = parseOptionalConfig[String](config, "questDbSettings.instanceId").getOrElse("designer-statistics"),
    directory = parseOptionalConfig[String](config, "questDbSettings.directory"),
    flushTaskDelay = parseOptionalConfig[FiniteDuration](config, "questDbSettings.flushTaskDelay")
      .getOrElse(30 seconds),
    retentionTaskDelay = parseOptionalConfig[FiniteDuration](config, "questDbSettings.retentionTaskDelay")
      .getOrElse(24 hours),
    poolConfig = parseOptionalConfig[QuestDbPoolConfig](config, "questDbSettings.poolConfig").getOrElse(
      QuestDbPoolConfig(
        corePoolSize = 2,
        maxPoolSize = 4,
        keepAliveTimeInSeconds = 60,
        queueCapacity = 8
      )
    )
  )

}
