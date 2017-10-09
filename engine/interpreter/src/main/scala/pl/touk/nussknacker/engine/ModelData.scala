package pl.touk.nussknacker.engine

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.process.ProcessConfigCreator
import pl.touk.nussknacker.engine.compile.ProcessValidator
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectDefinition
import pl.touk.nussknacker.engine.definition.{ConfigCreatorSignalDispatcher, ConfigCreatorTestInfoProvider, ProcessDefinitionExtractor}
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.{ObjectProcessDefinition, ProcessDefinition}
import pl.touk.nussknacker.engine.migration.ProcessMigrations
import pl.touk.nussknacker.engine.util.ThreadUtils
import pl.touk.nussknacker.engine.util.multiplicity.{Empty, Many, Multiplicity, One}
import pl.touk.nussknacker.engine.util.loader.{JarClassLoader, ProcessConfigCreatorLoader, ScalaServiceLoader}

object ModelData {

  def apply(config: Config, jarPathKey: String, configKey: String) : ModelData = {
    val processConfig = config.getConfig(configKey)
    val jarClassLoader = JarClassLoader(config.getString(jarPathKey))
    ClassLoaderModelData(processConfig, jarClassLoader)
  }

}

case class ClassLoaderModelData(processConfig: Config, jarClassLoader: JarClassLoader)
  extends ModelData {

  //this is not lazy, to be able to detect if creator can be created...
  val configCreator : ProcessConfigCreator = ProcessConfigCreatorLoader.loadProcessConfigCreator(jarClassLoader.classLoader)

  lazy val migrations: ProcessMigrations = {
    Multiplicity(ScalaServiceLoader.load[ProcessMigrations](jarClassLoader.classLoader)) match {
      case Empty() => ProcessMigrations.empty
      case One(migrationsDef) => migrationsDef
      case Many(moreThanOne) =>
        throw new IllegalArgumentException(s"More than one ProcessMigrations instance found: $moreThanOne")
    }
  }


}

trait ModelData extends ConfigCreatorSignalDispatcher with ConfigCreatorTestInfoProvider{

  def migrations: ProcessMigrations

  def configCreator : ProcessConfigCreator

  lazy val processDefinition: ProcessDefinition[ObjectDefinition] =
    withThisAsContextClassLoader {
      ObjectProcessDefinition(ProcessDefinitionExtractor.extractObjectWithMethods(configCreator, processConfig))
    }

  lazy val validator: ProcessValidator = ProcessValidator.default(processDefinition)

  def withThisAsContextClassLoader[T](block: => T) : T = {
    ThreadUtils.withThisAsContextClassLoader(jarClassLoader.classLoader) {
      block
    }
  }

  def jarClassLoader : JarClassLoader
}