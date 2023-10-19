package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.server.Directive1
import pl.touk.nussknacker.engine.api.process.{ProcessIdWithName, ProcessName}
import pl.touk.nussknacker.restmodel.processdetails.BaseProcessDetails
import pl.touk.nussknacker.ui.process.ProcessService
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.ExecutionContext

trait ProcessDirectives {
  import akka.http.scaladsl.server.Directives._

  protected val processService: ProcessService
  implicit val ec: ExecutionContext

  def processDetailsForName(
      processName: String
  )(implicit loggedUser: LoggedUser): Directive1[BaseProcessDetails[Unit]] = {
    processId(processName).flatMap { processIdWithName =>
      onSuccess(processService.getProcess[Unit](processIdWithName)).flatMap {
        case Right(process) => provide(process)
        case Left(err)      => failWith(err)
      }
    }
  }

  def processId(processName: String): Directive1[ProcessIdWithName] = {
    // TODO: We should handle exceptions explicitly instead of relying on the processId directive to do it implicitly
    handleExceptions(EspErrorToHttp.espErrorHandler).tflatMap { _ =>
      onSuccess(processService.getProcessId(ProcessName(processName))).flatMap {
        case Right(processId) => provide(ProcessIdWithName(processId, ProcessName(processName)))
        case Left(err)        => failWith(err)
      }
    }
  }

}
