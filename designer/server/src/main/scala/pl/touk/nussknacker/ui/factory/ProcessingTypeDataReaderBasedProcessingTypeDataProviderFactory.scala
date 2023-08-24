package pl.touk.nussknacker.ui.factory
import akka.actor.ActorSystem
import pl.touk.nussknacker.engine.{CombinedProcessingTypeData, ConfigWithUnresolvedVersion, ProcessingTypeData}
import pl.touk.nussknacker.ui.process.ProcessCategoryService
import pl.touk.nussknacker.ui.process.deployment.DeploymentService
import pl.touk.nussknacker.ui.process.processingtypedata.{ProcessingTypeDataProvider, ProcessingTypeDataReader}
import sttp.client3.SttpBackend

import java.util.function.Supplier
import scala.concurrent.{ExecutionContext, Future}

object ProcessingTypeDataReaderBasedProcessingTypeDataProviderFactory extends ProcessingTypeDataProviderFactory {

  override def create(designerConfig: ConfigWithUnresolvedVersion,
                      deploymentServiceSupplier: Supplier[DeploymentService],
                      categoriesService: ProcessCategoryService)
                     (implicit ec: ExecutionContext,
                      actorSystem: ActorSystem,
                      sttpBackend: SttpBackend[Future, Any]): ProcessingTypeDataProvider[ProcessingTypeData, CombinedProcessingTypeData] = {
    implicit val deploymentService: DeploymentService = deploymentServiceSupplier.get()
    implicit val categoriesServiceImp: ProcessCategoryService = categoriesService
    ProcessingTypeDataReader.loadProcessingTypeData(designerConfig)
  }
}
