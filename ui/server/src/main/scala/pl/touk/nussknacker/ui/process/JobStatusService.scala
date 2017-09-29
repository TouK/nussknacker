package pl.touk.nussknacker.ui.process

import akka.actor.ActorRef
import pl.touk.nussknacker.ui.process.deployment.CheckStatus
import pl.touk.nussknacker.ui.process.displayedgraph.ProcessStatus
import akka.pattern.ask
import akka.util.Timeout
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.Future

class JobStatusService(managerActor: ActorRef) {
  import scala.concurrent.duration._

  def retrieveJobStatus(processId: String)(implicit user: LoggedUser): Future[Option[ProcessStatus]] = {
    implicit val timeout = Timeout(1 minute)
    (managerActor ? CheckStatus(processId, user)).mapTo[Option[ProcessStatus]]
  }
}
