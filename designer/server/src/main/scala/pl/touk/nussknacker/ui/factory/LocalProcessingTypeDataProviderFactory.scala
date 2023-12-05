package pl.touk.nussknacker.ui.factory

import akka.actor.ActorSystem
import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.deployment.ProcessingTypeDeploymentService
import pl.touk.nussknacker.engine._
import pl.touk.nussknacker.ui.process.deployment.DeploymentService
import pl.touk.nussknacker.ui.process.processingtypedata.{
  DefaultProcessingTypeDeploymentService,
  MapBasedProcessingTypeDataProvider,
  ProcessingTypeDataProvider
}
import pl.touk.nussknacker.ui.util.LocalNussknackerWithSingleModel.{category, typeName}
import _root_.sttp.client3.SttpBackend
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.ui.process.processingtypedata.ProcessingTypeDataReader.toValueWithPermission

import java.util.function.Supplier
import scala.concurrent.{ExecutionContext, Future}

class LocalProcessingTypeDataProviderFactory(
    modelData: ModelData,
    deploymentManagerProvider: DeploymentManagerProvider,
    managerConfig: Config
) extends ProcessingTypeDataProviderFactory {

  override def create(
      designerConfig: ConfigWithUnresolvedVersion,
      deploymentServiceSupplier: Supplier[DeploymentService]
  )(
      implicit ec: ExecutionContext,
      actorSystem: ActorSystem,
      sttpBackend: SttpBackend[Future, Any]
  ): ProcessingTypeDataProvider[ProcessingTypeData, CombinedProcessingTypeData] = {
    val deploymentService: DeploymentService = deploymentServiceSupplier.get()
    implicit val processTypeDeploymentService: ProcessingTypeDeploymentService =
      new DefaultProcessingTypeDeploymentService(typeName, deploymentService)
    val categoriesConfig = new CategoryConfig(Some(category))
    val data =
      ProcessingTypeData.createProcessingTypeData(deploymentManagerProvider, modelData, managerConfig, categoriesConfig)
    val processingTypes = Map(typeName -> data)
    val combinedData    = CombinedProcessingTypeData.create(processingTypes, designerConfig)
    new MapBasedProcessingTypeDataProvider(processingTypes.mapValuesNow(toValueWithPermission), combinedData)
  }

}
