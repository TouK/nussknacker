package pl.touk.nussknacker.ui.ha

import com.typesafe.config.Config
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import pl.touk.nussknacker.engine.util.config.CustomFicusInstances._

import java.net.InetAddress
import scala.concurrent.duration._
import scala.util.Try

sealed trait HaMode

object HaMode {

  final case class Disabled(instanceId: String) extends HaMode

  final case class LeaderConfig(
      heartbeatInterval: FiniteDuration = 10.seconds,
      leaseDuration: FiniteDuration = 30.seconds,
      releaseOnStop: Boolean = true,
  )

  final case class Enabled(
      instanceId: String,
      leader: LeaderConfig,
      periodicLockDuration: FiniteDuration,
      lockQueryTimeout: FiniteDuration,
  ) extends HaMode

  def fromConfig(config: Config): HaMode =
    if (config.hasPath("ha.enabled") && config.getBoolean("ha.enabled")) {
      val cfg = config.as[EnabledConfig]("ha")
      val enabled = Enabled(
        instanceId = cfg.instanceId.getOrElse(defaultInstanceId),
        leader = cfg.leader,
        periodicLockDuration = cfg.periodicLockDuration,
        lockQueryTimeout = cfg.lockQueryTimeout,
      )
      if (enabled.lockQueryTimeout >= enabled.leader.heartbeatInterval)
        throw new IllegalArgumentException(
          s"ha.lockQueryTimeout (${enabled.lockQueryTimeout}) must be less than ha.leader.heartbeatInterval (${enabled.leader.heartbeatInterval})."
        )
      if (enabled.leader.leaseDuration <= enabled.leader.heartbeatInterval)
        throw new IllegalArgumentException(
          s"ha.leader.leaseDuration (${enabled.leader.leaseDuration}) must be greater than ha.leader.heartbeatInterval (${enabled.leader.heartbeatInterval}). " +
            s"Recommended: leaseDuration >= 3 * heartbeatInterval."
        )
      enabled
    } else {
      val instanceId =
        if (config.hasPath("ha.instanceId")) config.getString("ha.instanceId")
        else defaultInstanceId
      Disabled(instanceId)
    }

  private def defaultInstanceId: String =
    Try(InetAddress.getLocalHost.getHostName).fold(
      ex =>
        throw new IllegalArgumentException(
          "Cannot determine instanceId automatically. Set ha.instanceId explicitly in the config.",
          ex
        ),
      identity
    )

  private final case class EnabledConfig(
      instanceId: Option[String] = None,
      leader: LeaderConfig = LeaderConfig(),
      periodicLockDuration: FiniteDuration = 5.minutes,
      lockQueryTimeout: FiniteDuration = 5.seconds,
  )

}
