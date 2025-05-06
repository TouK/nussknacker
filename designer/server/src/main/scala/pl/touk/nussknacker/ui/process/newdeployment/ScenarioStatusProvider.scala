package pl.touk.nussknacker.ui.process.newdeployment

import cats.effect.IO
import pl.touk.nussknacker.engine.api.process.{ProcessingType, ProcessName}
import pl.touk.nussknacker.ui.process.processingtype.provider.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.repository.DBIOActionRunner
import pl.touk.nussknacker.ui.security.api.LoggedUser

class ScenarioStatusProvider(
    processingTypeChecker: ProcessingTypeDataProvider[_, _],
    deploymentRepository: DeploymentRepository,
    dbioRunner: DBIOActionRunner
) {

  def getActiveScenariosFor(
      processingTypes: Iterable[ProcessingType]
  )(implicit user: LoggedUser): IO[Set[ProcessName]] = {
    for {
      allowedProcessingTypes <- onlyAllowedProcessingTypes(processingTypes)
      deployments            <- processingTypesDeploymentsRelatedTo(allowedProcessingTypes)
    } yield {
      deployments.flatMap { deployment =>
        if (deployment.status.isActive) Some(deployment.scenarioName) else None
      }
    }
  }

  private def onlyAllowedProcessingTypes(processingTypes: Iterable[ProcessingType])(implicit user: LoggedUser) = {
    IO.delay {
      processingTypes.filter { processingType =>
        processingTypeChecker.forProcessingType(processingType).isDefined
      }
    }
  }

  private def processingTypesDeploymentsRelatedTo(processingTypes: Iterable[ProcessingType]) = {
    IO.fromFuture(IO(dbioRunner.run {
      deploymentRepository.getProcessingTypesDeployments(processingTypes)
    }))
  }

}
