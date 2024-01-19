package pl.touk.nussknacker.ui.factory

import akka.actor.ActorSystem
import pl.touk.nussknacker.engine.api.component.AdditionalUIConfigProvider
import pl.touk.nussknacker.engine.{CombinedProcessingTypeData, ConfigWithUnresolvedVersion, ProcessingTypeData}
import pl.touk.nussknacker.ui.process.deployment.DeploymentService
import pl.touk.nussknacker.ui.process.processingtypedata.{ProcessingTypeDataReader, ProcessingTypeDataState}
import sttp.client3.SttpBackend

import java.util.function.Supplier
import scala.concurrent.{ExecutionContext, Future}

object ProcessingTypeDataReaderBasedProcessingTypeDataStateFactory extends ProcessingTypeDataStateFactory {

  override def create(
      designerConfig: ConfigWithUnresolvedVersion,
      deploymentServiceSupplier: Supplier[DeploymentService],
      additionalUIConfigProvider: AdditionalUIConfigProvider
  )(
      implicit ec: ExecutionContext,
      actorSystem: ActorSystem,
      sttpBackend: SttpBackend[Future, Any]
  ): ProcessingTypeDataState[ProcessingTypeData, CombinedProcessingTypeData] = {
    implicit val deploymentService: DeploymentService = deploymentServiceSupplier.get()
    ProcessingTypeDataReader.loadProcessingTypeData(designerConfig, additionalUIConfigProvider)
  }

}
