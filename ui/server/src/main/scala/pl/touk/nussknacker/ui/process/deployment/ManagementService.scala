package pl.touk.nussknacker.ui.process.deployment

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.deployment.ProcessState
import pl.touk.nussknacker.restmodel.process.ProcessIdWithName
import pl.touk.nussknacker.ui.EspError.XError
import pl.touk.nussknacker.ui.security.api.LoggedUser

import java.time
import scala.concurrent.{ExecutionContext, Future}

class ManagementService(managerActor: ActorRef, duration: time.Duration) extends LazyLogging {
  import scala.concurrent.duration._

  private implicit val timeout: Timeout = Timeout(duration.toMillis millis)

  type ArchiveResponse = XError[Unit]

  /**
    * Handling error at retrieving status from manager is created at ManagementActor
    */
  def getProcessState(processIdWithName: ProcessIdWithName)(implicit ec: ExecutionContext, user: LoggedUser): Future[ProcessState] = {
    implicit val timeout: Timeout = Timeout(1 minute)
    (managerActor ? CheckStatus(processIdWithName, user)).mapTo[ProcessState]
  }

  def deployProcess(processIdWithName: ProcessIdWithName, savepointPath: Option[String], comment: Option[String])(implicit ec: ExecutionContext, user: LoggedUser): Future[Any] = {
    managerActor ? Deploy(processIdWithName, user, savepointPath, comment)
  }

  def cancelProcess(processIdWithName: ProcessIdWithName, comment: Option[String])(implicit ec: ExecutionContext, user: LoggedUser): Future[Any] = {
    managerActor ? Cancel(processIdWithName, user, comment)
  }
}
