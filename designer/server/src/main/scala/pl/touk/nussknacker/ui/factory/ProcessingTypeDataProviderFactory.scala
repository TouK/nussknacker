package pl.touk.nussknacker.ui.factory

import akka.actor.ActorSystem
import pl.touk.nussknacker.engine.{CombinedProcessingTypeData, ConfigWithUnresolvedVersion, ProcessingTypeData}
import pl.touk.nussknacker.ui.process.ProcessCategoryService
import pl.touk.nussknacker.ui.process.deployment.DeploymentService
import pl.touk.nussknacker.ui.process.processingtypedata.ProcessingTypeDataProvider
import sttp.client3.SttpBackend

import java.util.function.Supplier
import scala.concurrent.{ExecutionContext, Future}

trait ProcessingTypeDataProviderFactory {

  def create(
      designerConfig: ConfigWithUnresolvedVersion,
      deploymentServiceSupplier: Supplier[DeploymentService],
      categoriesService: ProcessCategoryService
  )(
      implicit ec: ExecutionContext,
      actorSystem: ActorSystem,
      sttpBackend: SttpBackend[Future, Any]
  ): ProcessingTypeDataProvider[ProcessingTypeData, CombinedProcessingTypeData]
}
