package pl.touk.nussknacker.engine.testing

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.process.ProcessConfigCreator
import pl.touk.nussknacker.engine.migration.ProcessMigrations
import pl.touk.nussknacker.engine.util.loader.ModelClassLoader
import pl.touk.nussknacker.engine.{ModelConfigToLoad, ModelData}

object LocalModelData {

  def apply(config: Config,
            configCreator: ProcessConfigCreator,
            migrations: ProcessMigrations = ProcessMigrations.empty,
            modelClassLoader: ModelClassLoader = ModelClassLoader.empty): LocalModelData =
    new LocalModelData(ModelConfigToLoad(config), configCreator, migrations, modelClassLoader)

}

case class LocalModelData(processConfigFromConfiguration: ModelConfigToLoad,
                          configCreator: ProcessConfigCreator,
                          migrations: ProcessMigrations,
                          modelClassLoader: ModelClassLoader) extends ModelData