package pl.touk.nussknacker.ui.factory

import akka.actor.ActorSystem
import pl.touk.nussknacker.engine.ConfigWithUnresolvedVersion
import pl.touk.nussknacker.engine.api.component.AdditionalUIConfigProvider
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.ui.process.deployment.{AllDeployedScenarioService, DeploymentService}
import pl.touk.nussknacker.ui.process.processingtype.{
  CombinedProcessingTypeData,
  ProcessingTypeData,
  ProcessingTypeDataReader,
  ProcessingTypeDataState
}
import sttp.client3.SttpBackend

import java.util.function.Supplier
import scala.concurrent.{ExecutionContext, Future}

object ProcessingTypeDataReaderBasedProcessingTypeDataStateFactory extends ProcessingTypeDataStateFactory {

  override def create(
      designerConfig: ConfigWithUnresolvedVersion,
      deploymentServiceSupplier: Supplier[DeploymentService],
      createAllDeployedScenarioService: ProcessingType => AllDeployedScenarioService,
      additionalUIConfigProvider: AdditionalUIConfigProvider
  )(
      implicit ec: ExecutionContext,
      actorSystem: ActorSystem,
      sttpBackend: SttpBackend[Future, Any]
  ): ProcessingTypeDataState[ProcessingTypeData, CombinedProcessingTypeData] = {
    val deploymentService = deploymentServiceSupplier.get()
    ProcessingTypeDataReader.loadProcessingTypeData(
      designerConfig,
      deploymentService,
      createAllDeployedScenarioService,
      additionalUIConfigProvider
    )
  }

}
