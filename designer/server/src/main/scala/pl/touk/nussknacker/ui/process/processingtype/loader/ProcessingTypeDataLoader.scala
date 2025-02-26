package pl.touk.nussknacker.ui.process.processingtype.loader

import cats.effect.IO
import pl.touk.nussknacker.engine.{DeploymentManagerDependencies, ModelDependencies}
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.engine.util.loader.DeploymentManagersClassLoader
import pl.touk.nussknacker.ui.db.DbRef
import pl.touk.nussknacker.ui.process.processingtype.{
  CombinedProcessingTypeData,
  ModelClassLoaderProvider,
  ProcessingTypeData,
  ValueWithRestriction
}
import pl.touk.nussknacker.ui.process.processingtype.provider.ProcessingTypeDataState

trait ProcessingTypeDataLoader {

  def loadProcessingTypeData(
      getModelDependencies: ProcessingType => ModelDependencies,
      getDeploymentManagerDependencies: ProcessingType => DeploymentManagerDependencies,
      deploymentManagersClassLoader: DeploymentManagersClassLoader,
      modelClassLoaderProvider: ModelClassLoaderProvider,
      // should be always available, used by scheduling mechanism,
      // but in tests sometimes we do not want to bootstrap the full environment with db
      dbRef: Option[DbRef],
  ): IO[ProcessingTypeDataState[ProcessingTypeData, CombinedProcessingTypeData]]

}

object ProcessingTypeDataLoader {

  def toValueWithRestriction(processingTypeData: ProcessingTypeData): ValueWithRestriction[ProcessingTypeData] = {
    ValueWithRestriction.userWithAccessRightsToAnyOfCategories(processingTypeData, Set(processingTypeData.category))
  }

}
