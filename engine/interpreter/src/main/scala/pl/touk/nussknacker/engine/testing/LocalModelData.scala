package pl.touk.nussknacker.engine.testing

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.process.ProcessConfigCreator
import pl.touk.nussknacker.engine.migration.ProcessMigrations
import pl.touk.nussknacker.engine.util.loader.ModelClassLoader

case class LocalModelData(processConfigFromConfiguration: Config,
                          configCreator: ProcessConfigCreator,
                          migrations: ProcessMigrations = ProcessMigrations.empty,
                          modelClassLoader: ModelClassLoader = ModelClassLoader.empty) extends ModelData