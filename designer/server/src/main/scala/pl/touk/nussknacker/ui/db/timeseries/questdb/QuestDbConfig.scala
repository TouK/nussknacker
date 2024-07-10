package pl.touk.nussknacker.ui.db.timeseries.questdb

import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import pl.touk.nussknacker.ui.config.Implicits.parseOptionalConfig

import scala.concurrent.duration.{DurationInt, FiniteDuration}

sealed trait QuestDbConfig

object QuestDbConfig {

  final case class Enabled(
      // This should be configured if nu will be run in multi instances.
      // If there are many instances we should change diagrams on grafana.
      instanceId: String,
      directory: Option[String],
      tasksExecutionDelay: FiniteDuration,
      retentionDelay: FiniteDuration,
      poolConfig: QuestDbPoolConfig
  ) extends QuestDbConfig

  case object Disabled extends QuestDbConfig

  final case class QuestDbPoolConfig(
      corePoolSize: Int,
      maxPoolSize: Int,
      keepAliveTimeInSeconds: Long,
      queueCapacity: Int
  )

  def apply(config: Config): QuestDbConfig = parseOptionalConfig[Boolean](config, "questDbSettings.enabled") match {
    case Some(false) => Disabled
    case _ =>
      Enabled(
        instanceId = parseOptionalConfig[String](config, "questDbSettings.instanceId").getOrElse("designer-statistics"),
        directory = parseOptionalConfig[String](config, "questDbSettings.directory"),
        tasksExecutionDelay = parseOptionalConfig[FiniteDuration](config, "questDbSettings.tasksExecutionDelay")
          .getOrElse(30 seconds),
        retentionDelay = parseOptionalConfig[FiniteDuration](config, "questDbSettings.retentionDelay")
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

}
