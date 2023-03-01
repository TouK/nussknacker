package pl.touk.nussknacker.ui.process.deployment

import pl.touk.nussknacker.engine.api.deployment.{DeployedScenarioData, ProcessState}
import pl.touk.nussknacker.restmodel.process.{ProcessIdWithName, ProcessingType}
import pl.touk.nussknacker.ui.process.repository.DeploymentComment
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.{ExecutionContext, Future}

trait DeploymentService extends ProcessStateService {

  def getDeployedScenarios(processingType: ProcessingType)
                          (implicit ec: ExecutionContext): Future[List[DeployedScenarioData]]

  //inner Future in result allows to wait for deployment finish, while outer handles validation
  def deployProcessAsync(id: ProcessIdWithName, savepointPath: Option[String], deploymentComment: Option[DeploymentComment])
                        (implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[Future[_]]

  def cancelProcess(id: ProcessIdWithName, deploymentComment: Option[DeploymentComment])
                   (implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[_]

}

trait ProcessStateService {

  def getProcessState(processIdWithName: ProcessIdWithName)
                     (implicit user: LoggedUser, ec: ExecutionContext): Future[ProcessState]

}


