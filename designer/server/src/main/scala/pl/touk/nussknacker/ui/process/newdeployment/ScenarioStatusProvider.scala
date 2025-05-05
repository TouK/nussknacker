package pl.touk.nussknacker.ui.process.newdeployment

import cats.effect.IO
import pl.touk.nussknacker.engine.api.process.{ProcessingType, ProcessName}
import pl.touk.nussknacker.ui.process.repository.DBIOActionRunner
import pl.touk.nussknacker.ui.security.api.LoggedUser

class ScenarioStatusProvider(deploymentRepository: DeploymentRepository, dbioRunner: DBIOActionRunner) {

  def getActiveScenariosFor(
      processingTypes: Iterable[ProcessingType]
  )(implicit user: LoggedUser): IO[Set[ProcessName]] = {
    IO
      .fromFuture(IO {
        dbioRunner.run(deploymentRepository.getProcessingTypesDeployments(processingTypes))
      })
      .map { deployments =>
        deployments.flatMap { deployment =>
          if (deployment.status.isActive) Some(deployment.scenarioName) else None
        }
      }
  }

}
