package pl.touk.nussknacker.engine.testing

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.component.ComponentProvider._
import pl.touk.nussknacker.engine.api.namespaces.ObjectNaming
import pl.touk.nussknacker.engine.api.process.{EmptyProcessConfigCreator, ProcessConfigCreator}
import pl.touk.nussknacker.engine.migration.ProcessMigrations
import pl.touk.nussknacker.engine.modelconfig.{DefaultModelConfigLoader, InputConfigDuringExecution, ModelConfigLoader}
import pl.touk.nussknacker.engine.util.loader.ModelClassLoader
import pl.touk.nussknacker.engine.util.namespaces.DefaultNamespacedObjectNaming

class ComponentProviderBasedModelData(config: Config, providers: ComponentProviders) extends ModelData {

  override def componentProviders: ComponentProviders = providers

  override def migrations: ProcessMigrations = ProcessMigrations.empty

  //we do not want to pass any components via ProcessConfigCreator
  override def configCreator: ProcessConfigCreator = new EmptyProcessConfigCreator

  override def modelClassLoader: ModelClassLoader = ModelClassLoader.empty

  override def modelConfigLoader: ModelConfigLoader = new DefaultModelConfigLoader

  override def objectNaming: ObjectNaming = DefaultNamespacedObjectNaming

  override def inputConfigDuringExecution: InputConfigDuringExecution = InputConfigDuringExecution(config)
}
