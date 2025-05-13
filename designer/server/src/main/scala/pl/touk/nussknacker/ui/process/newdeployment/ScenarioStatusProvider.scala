package pl.touk.nussknacker.ui.process.newdeployment

import cats.effect.IO
import pl.touk.nussknacker.engine.api.process.{ProcessingType, ProcessName}
import pl.touk.nussknacker.ui.UnauthorizedError
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
      _           <- checkIfProcessingTypesAreAllowed(processingTypes)
      deployments <- processingTypesDeploymentsRelatedTo(processingTypes)
    } yield {
      deployments.flatMap { deployment =>
        if (deployment.status.isActive) Some(deployment.scenarioName) else None
      }
    }
  }

  private def checkIfProcessingTypesAreAllowed(processingTypes: Iterable[ProcessingType])(implicit user: LoggedUser) = {
    IO.delay {
      processingTypes.foreach { processingType =>
        processingTypeChecker.forProcessingTypeE(processingType) match {
          case Left(error: UnauthorizedError) => throw error
          case Right(_)                       =>
        }
      }
    }
  }

  private def processingTypesDeploymentsRelatedTo(processingTypes: Iterable[ProcessingType]) = {
    IO.fromFuture(IO(dbioRunner.run {
      deploymentRepository.getProcessingTypesDeployments(processingTypes)
    }))
  }

}
