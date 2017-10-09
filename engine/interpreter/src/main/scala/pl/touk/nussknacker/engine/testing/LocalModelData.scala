package pl.touk.nussknacker.engine.testing

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.process.ProcessConfigCreator
import pl.touk.nussknacker.engine.migration.ProcessMigrations
import pl.touk.nussknacker.engine.util.loader.JarClassLoader


case class LocalModelData(processConfig: Config,
                          configCreator: ProcessConfigCreator,
                          migrations: ProcessMigrations = ProcessMigrations.empty,
                          jarClassLoader: JarClassLoader = JarClassLoader.empty) extends ModelData