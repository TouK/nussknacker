package pl.touk.nussknacker.ui.process

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.deployment.ProcessState
import pl.touk.nussknacker.restmodel.process.ProcessIdWithName
import pl.touk.nussknacker.ui.process.deployment.CheckStatus
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.{ExecutionContext, Future}

class JobStatusService(managerActor: ActorRef) extends LazyLogging {
  import scala.concurrent.duration._

  /**
    * Handling error at retrieving status from manager is created at ManagementActor
    */
  def retrieveJobStatus(processId: ProcessIdWithName)(implicit ec: ExecutionContext, user: LoggedUser): Future[ProcessState] = {
    implicit val timeout: Timeout = Timeout(1 minute)
    (managerActor ? CheckStatus(processId, user)).mapTo[ProcessState]
  }

}
