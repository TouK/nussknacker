package pl.touk.nussknacker.ui.api

import org.apache.pekko.http.scaladsl.server.Directive1
import pl.touk.nussknacker.engine.api.process.{ProcessIdWithName, ProcessName}
import pl.touk.nussknacker.restmodel.scenariodetails.ScenarioWithDetails
import pl.touk.nussknacker.ui.process.ProcessService
import pl.touk.nussknacker.ui.process.ProcessService.GetScenarioWithDetailsOptions
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.util.NuPathMatchers

import scala.concurrent.ExecutionContext

trait ProcessDirectives extends NuPathMatchers {
  import org.apache.pekko.http.scaladsl.server.Directives._

  protected val processService: ProcessService
  implicit val ec: ExecutionContext

  def processDetailsForName(
      processName: ProcessName
  )(implicit loggedUser: LoggedUser): Directive1[ScenarioWithDetails] = {
    processId(processName).flatMap { processIdWithName =>
      onSuccess({
        processService
          .getLatestProcessWithDetails(processIdWithName, GetScenarioWithDetailsOptions.detailsOnly)
      }).flatMap(provide)
    }
  }

  def processId(processName: ProcessName): Directive1[ProcessIdWithName] = {
    onSuccess(processService.getProcessIdUnsafe(processName))
      .map(ProcessIdWithName(_, processName))
      .flatMap(provide)
  }

}
