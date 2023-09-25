package pl.touk.nussknacker.engine.testing

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.namespaces.ObjectNaming
import pl.touk.nussknacker.engine.api.process.ProcessConfigCreator
import pl.touk.nussknacker.engine.migration.ProcessMigrations
import pl.touk.nussknacker.engine.util.loader.ModelClassLoader
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.component.{AdditionalComponentsUIConfigProvider, DefaultAdditionalComponentsUIConfigProvider}
import pl.touk.nussknacker.engine.modelconfig.{DefaultModelConfigLoader, InputConfigDuringExecution, ModelConfigLoader}
import pl.touk.nussknacker.engine.util.namespaces.DefaultNamespacedObjectNaming

object LocalModelData {

  def apply(inputConfig: Config,
            configCreator: ProcessConfigCreator,
            migrations: ProcessMigrations = ProcessMigrations.empty,
            modelConfigLoader: ModelConfigLoader = new DefaultModelConfigLoader,
            modelClassLoader: ModelClassLoader = ModelClassLoader.empty,
            objectNaming: ObjectNaming = DefaultNamespacedObjectNaming,
            additionalComponentsUIConfigProvider: AdditionalComponentsUIConfigProvider = new DefaultAdditionalComponentsUIConfigProvider
           ): LocalModelData =
    new LocalModelData(InputConfigDuringExecution(inputConfig), modelConfigLoader, configCreator, migrations, modelClassLoader, objectNaming, additionalComponentsUIConfigProvider)

}

case class LocalModelData(inputConfigDuringExecution: InputConfigDuringExecution,
                          modelConfigLoader: ModelConfigLoader,
                          configCreator: ProcessConfigCreator,
                          migrations: ProcessMigrations,
                          modelClassLoader: ModelClassLoader,
                          objectNaming: ObjectNaming,
                          additionalComponentsUIConfigProvider: AdditionalComponentsUIConfigProvider
                         ) extends ModelData
