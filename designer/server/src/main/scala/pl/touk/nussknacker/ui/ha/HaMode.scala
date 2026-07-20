package pl.touk.nussknacker.ui.ha

import com.typesafe.config.Config
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import pl.touk.nussknacker.engine.util.config.CustomFicusInstances._

import java.net.InetAddress
import scala.concurrent.duration._

sealed trait HaMode

object HaMode {

  case object Disabled extends HaMode

  final case class Enabled(
      instanceId: String,
      leaderLeaseDuration: FiniteDuration,
      leaderHeartbeatInterval: FiniteDuration,
      periodicLockDuration: FiniteDuration,
      lockQueryTimeout: FiniteDuration,
  ) extends HaMode

  def fromConfig(config: Config): HaMode =
    if (config.hasPath("ha.enabled") && config.getBoolean("ha.enabled")) {
      val cfg = config.as[EnabledConfig]("ha")
      val enabled = Enabled(
        instanceId = cfg.instanceId.getOrElse(InetAddress.getLocalHost.getHostName),
        leaderLeaseDuration = cfg.leaderLeaseDuration,
        leaderHeartbeatInterval = cfg.leaderHeartbeatInterval,
        periodicLockDuration = cfg.periodicLockDuration,
        lockQueryTimeout = cfg.lockQueryTimeout,
      )
      if (enabled.lockQueryTimeout >= enabled.leaderHeartbeatInterval)
        throw new IllegalArgumentException(
          s"ha.lockQueryTimeout (${enabled.lockQueryTimeout}) must be less than ha.leaderHeartbeatInterval (${enabled.leaderHeartbeatInterval})."
        )
      if (enabled.leaderLeaseDuration <= enabled.leaderHeartbeatInterval)
        throw new IllegalArgumentException(
          s"ha.leaderLeaseDuration (${enabled.leaderLeaseDuration}) must be greater than ha.leaderHeartbeatInterval (${enabled.leaderHeartbeatInterval}). " +
            s"Recommended: leaderLeaseDuration >= 3 * leaderHeartbeatInterval."
        )
      enabled
    } else {
      Disabled
    }

  private final case class EnabledConfig(
      instanceId: Option[String] = None,
      leaderLeaseDuration: FiniteDuration = 30.seconds,
      leaderHeartbeatInterval: FiniteDuration = 10.seconds,
      periodicLockDuration: FiniteDuration = 5.minutes,
      lockQueryTimeout: FiniteDuration = 5.seconds,
  )

}
