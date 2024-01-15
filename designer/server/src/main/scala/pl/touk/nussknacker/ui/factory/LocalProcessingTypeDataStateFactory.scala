package pl.touk.nussknacker.ui.factory

import akka.actor.ActorSystem
import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.deployment.ProcessingTypeDeploymentService
import pl.touk.nussknacker.engine._
import pl.touk.nussknacker.ui.process.deployment.DeploymentService
import pl.touk.nussknacker.ui.process.processingtypedata.{
  DefaultProcessingTypeDeploymentService,
  ProcessingTypeDataState
}
import pl.touk.nussknacker.ui.util.LocalNussknackerWithSingleModel.{category, typeName}
import _root_.sttp.client3.SttpBackend
import pl.touk.nussknacker.engine.api.component.AdditionalUIConfigProvider
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.ui.process.processingtypedata.ProcessingTypeDataReader.toValueWithPermission

import java.util.function.Supplier
import scala.concurrent.{ExecutionContext, Future}

class LocalProcessingTypeDataStateFactory(
    modelData: ModelData,
    deploymentManagerProvider: DeploymentManagerProvider,
    managerConfig: Config
) extends ProcessingTypeDataStateFactory {

  override def create(
      designerConfig: ConfigWithUnresolvedVersion,
      deploymentServiceSupplier: Supplier[DeploymentService],
      additionalUIConfigProvider: AdditionalUIConfigProvider
  )(
      implicit ec: ExecutionContext,
      actorSystem: ActorSystem,
      sttpBackend: SttpBackend[Future, Any]
  ): ProcessingTypeDataState[ProcessingTypeData, CombinedProcessingTypeData] = {
    val deploymentService: DeploymentService = deploymentServiceSupplier.get()
    implicit val processTypeDeploymentService: ProcessingTypeDeploymentService =
      new DefaultProcessingTypeDeploymentService(typeName, deploymentService)
    val data =
      ProcessingTypeData.createProcessingTypeData(
        typeName,
        deploymentManagerProvider,
        modelData,
        managerConfig,
        category
      )
    val processingTypes = Map(typeName -> data)
    val combinedData    = CombinedProcessingTypeData.create(processingTypes)
    ProcessingTypeDataState(processingTypes.mapValuesNow(toValueWithPermission), () => combinedData, new Object)
  }

}
