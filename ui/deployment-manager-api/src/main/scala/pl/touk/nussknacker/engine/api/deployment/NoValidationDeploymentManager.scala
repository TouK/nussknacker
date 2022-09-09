package pl.touk.nussknacker.engine.api.deployment
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.{DeploymentData, ExternalDeploymentId}

import scala.concurrent.Future

trait NoValidationDeploymentManager { self: DeploymentManager =>

  override type ValidationResult = Unit

  override def validate(processVersion: ProcessVersion, deploymentData: DeploymentData, canonicalProcess: CanonicalProcess): Future[Unit] = Future.successful(())

  override def deploy(processVersion: ProcessVersion, deploymentData: DeploymentData, canonicalProcess: CanonicalProcess,
                      savepointPath: Option[String], validationResult: ValidationResult): Future[Option[ExternalDeploymentId]] =
    deploy(processVersion, deploymentData, canonicalProcess, savepointPath)

  protected def deploy(processVersion: ProcessVersion, deploymentData: DeploymentData, canonicalProcess: CanonicalProcess, savepointPath: Option[String]): Future[Option[ExternalDeploymentId]]

}
