package pl.touk.nussknacker.ui.factory
import akka.actor.ActorSystem
import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.deployment.ProcessingTypeDeploymentService
import pl.touk.nussknacker.engine.{
  CombinedProcessingTypeData,
  ConfigWithUnresolvedVersion,
  DeploymentManagerProvider,
  ModelData,
  ProcessingTypeData
}
import pl.touk.nussknacker.ui.process.ProcessCategoryService
import pl.touk.nussknacker.ui.process.deployment.DeploymentService
import pl.touk.nussknacker.ui.process.processingtypedata.{
  DefaultProcessingTypeDeploymentService,
  MapBasedProcessingTypeDataProvider,
  ProcessingTypeDataProvider
}
import pl.touk.nussknacker.ui.util.LocalNussknackerWithSingleModel.typeName
import sttp.client3.SttpBackend

import java.util.function.Supplier
import scala.concurrent.{ExecutionContext, Future}

class LocalProcessingTypeDataProviderFactory(
    modelData: ModelData,
    deploymentManagerProvider: DeploymentManagerProvider,
    managerConfig: Config
) extends ProcessingTypeDataProviderFactory {

  override def create(
      designerConfig: ConfigWithUnresolvedVersion,
      deploymentServiceSupplier: Supplier[DeploymentService],
      categoriesService: ProcessCategoryService
  )(
      implicit ec: ExecutionContext,
      actorSystem: ActorSystem,
      sttpBackend: SttpBackend[Future, Any]
  ): ProcessingTypeDataProvider[ProcessingTypeData, CombinedProcessingTypeData] = {
    val deploymentService: DeploymentService = deploymentServiceSupplier.get()
    implicit val processTypeDeploymentService: ProcessingTypeDeploymentService =
      new DefaultProcessingTypeDeploymentService(typeName, deploymentService)
    val data = ProcessingTypeData.createProcessingTypeData(deploymentManagerProvider, modelData, managerConfig)
    val processingTypes = Map(typeName -> data)
    val combinedData    = CombinedProcessingTypeData.create(processingTypes, categoriesService)
    new MapBasedProcessingTypeDataProvider(processingTypes, combinedData)
  }
}
