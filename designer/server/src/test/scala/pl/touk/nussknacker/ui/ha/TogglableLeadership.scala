package pl.touk.nussknacker.ui.ha

import cats.effect.{IO, Resource}
import cats.effect.std.Supervisor

class TogglableLeadership(initiallyLeader: Boolean) extends Leadership {

  @volatile private var leader: Boolean = initiallyLeader

  def becomeLeader(): Unit   = leader = true
  def loseLeadership(): Unit = leader = false

  override val haEnabled: Boolean  = true
  override val instanceId: String  = "test-instance"
  override def isLeader(): Boolean = leader

  override protected def doStartHeartbeat(supervisor: Supervisor[IO]): Resource[IO, Unit] = Resource.unit[IO]

}
