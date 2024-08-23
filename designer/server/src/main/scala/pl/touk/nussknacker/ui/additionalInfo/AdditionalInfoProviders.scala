package pl.touk.nussknacker.ui.additionalInfo

import cats.data.OptionT
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.additionalInfo.{AdditionalInfo, AdditionalInfoProvider}
import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.engine.graph.node.NodeData
import pl.touk.nussknacker.engine.util.loader.ScalaServiceLoader
import pl.touk.nussknacker.ui.process.processingtype.provider.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.{ExecutionContext, Future}

class AdditionalInfoProviders(typeToConfig: ProcessingTypeDataProvider[ModelData, _]) {

  // TODO: The AdditionalInfoProvider interface should be changed to the factory that creates AdditionalInfoProvider
  //       based on the model config.
  // TODO: We should move loading of these extensions into the class where we see how many designer side model
  //       extensions do we have. See notice next to ModelData.migrations
  private val nodeProviders: ProcessingTypeDataProvider[Option[NodeData => Future[Option[AdditionalInfo]]], _] =
    typeToConfig.mapValues(pt =>
      ScalaServiceLoader
        .load[AdditionalInfoProvider](pt.modelClassLoader.classLoader)
        .headOption
        .map(_.nodeAdditionalInfo(pt.modelConfig))
    )

  private val propertiesProviders: ProcessingTypeDataProvider[Option[MetaData => Future[Option[AdditionalInfo]]], _] =
    typeToConfig.mapValues(pt =>
      ScalaServiceLoader
        .load[AdditionalInfoProvider](pt.modelClassLoader.classLoader)
        .headOption
        .map(_.propertiesAdditionalInfo(pt.modelConfig))
    )

  def prepareAdditionalInfoForNode(nodeData: NodeData, processingType: ProcessingType)(
      implicit ec: ExecutionContext,
      user: LoggedUser
  ): Future[Option[AdditionalInfo]] = {
    (for {
      provider <- OptionT.fromOption[Future](nodeProviders.forProcessingType(processingType).flatten)
      data     <- OptionT(provider(nodeData))
    } yield data).value
  }

  def prepareAdditionalInfoForProperties(metaData: MetaData, processingType: ProcessingType)(
      implicit ec: ExecutionContext,
      user: LoggedUser
  ): Future[Option[AdditionalInfo]] = {
    (for {
      provider <- OptionT.fromOption[Future](propertiesProviders.forProcessingType(processingType).flatten)
      data     <- OptionT(provider(metaData))
    } yield data).value
  }

}
