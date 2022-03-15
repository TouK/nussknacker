package pl.touk.nussknacker.engine.management.sample

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.{CustomActionsProvider, CustomActionsProviderFactory}
import pl.touk.nussknacker.engine.api.deployment.{CustomAction, CustomActionError, CustomActionRequest, CustomActionResult, ProcessingTypeDeploymentService}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess

import scala.concurrent.Future

class DevCustomActionsProviderFactory extends CustomActionsProviderFactory {
  override def create(config: Config, deploymentService: ProcessingTypeDeploymentService): CustomActionsProvider = new CustomActionsProvider{
    override def customActions: List[CustomAction] = List(
      CustomAction("test-running", List("RUNNING")),
      CustomAction("test-canceled", List("CANCELED")),
      CustomAction("test-all", Nil),
    )

    override def invokeCustomAction(actionRequest: CustomActionRequest, canonicalProcess: CanonicalProcess): Future[Either[CustomActionError, CustomActionResult]] =
      Future.successful(Right(CustomActionResult(actionRequest, s"Done${actionRequest.name}")))
  }
}

