package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.server.Directive1
import pl.touk.nussknacker.engine.api.process.{ProcessIdWithName, ProcessName}
import pl.touk.nussknacker.restmodel.ValidatedProcessDetails
import pl.touk.nussknacker.ui.process.ProcessService
import pl.touk.nussknacker.ui.process.ProcessService.GetScenarioWithDetailsOptions
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.ExecutionContext

trait ProcessDirectives {
  import akka.http.scaladsl.server.Directives._

  protected val processService: ProcessService
  implicit val ec: ExecutionContext

  def processDetailsForName(
      processName: String
  )(implicit loggedUser: LoggedUser): Directive1[ValidatedProcessDetails] = {
    processId(processName).flatMap { processIdWithName =>
      onSuccess(
        processService.getProcessWithDetails(processIdWithName, GetScenarioWithDetailsOptions.detailsOnly)
      ).flatMap(provide)
    }
  }

  def processId(processName: String): Directive1[ProcessIdWithName] = {
    onSuccess(processService.getProcessId(ProcessName(processName)))
      .map(ProcessIdWithName(_, ProcessName(processName)))
      .flatMap(provide)
  }

}
