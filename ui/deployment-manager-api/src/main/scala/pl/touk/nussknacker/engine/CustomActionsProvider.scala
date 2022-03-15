package pl.touk.nussknacker.engine

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.util.loader.ScalaServiceLoader

import scala.concurrent.Future

trait CustomActionsProviderFactory {
  def create(config: Config, deploymentService: ProcessingTypeDeploymentService): CustomActionsProvider
}

object CustomActionsProviderFactory {
  def noOp: CustomActionsProviderFactory = (_: Config, _: ProcessingTypeDeploymentService) => EmptyCustomActionsProvider
}

object CustomActionsProviderFactoryLoader {
  def apply(classLoader: ClassLoader): CustomActionsProviderFactory = {
    ScalaServiceLoader.loadClass[CustomActionsProviderFactory](classLoader) {
      CustomActionsProviderFactory.noOp
    }
  }
}

trait CustomActionsProvider {
  def customActions: List[CustomAction]
  def invokeCustomAction(actionRequest: CustomActionRequest, canonicalProcess: CanonicalProcess): Future[Either[CustomActionError, CustomActionResult]]
}

object EmptyCustomActionsProvider extends CustomActionsProvider {
  override def customActions: List[CustomAction] = Nil

  override def invokeCustomAction(actionRequest: CustomActionRequest, canonicalProcess: CanonicalProcess): Future[Either[CustomActionError, CustomActionResult]] =
    Future.successful(Left(CustomActionNotImplemented(actionRequest)))
}
