package pl.touk.nussknacker.engine

import java.io.File
import java.net.URL

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.process.ProcessConfigCreator
import pl.touk.nussknacker.engine.compile.ProcessValidator
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectDefinition
import pl.touk.nussknacker.engine.definition.{ConfigCreatorSignalDispatcher, ConfigCreatorTestInfoProvider, ProcessDefinitionExtractor}
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.{ObjectProcessDefinition, ProcessDefinition}
import pl.touk.nussknacker.engine.migration.ProcessMigrations
import pl.touk.nussknacker.engine.util.ThreadUtils
import pl.touk.nussknacker.engine.util.multiplicity.{Empty, Many, Multiplicity, One}
import pl.touk.nussknacker.engine.util.loader.{ModelClassLoader, ProcessConfigCreatorLoader, ScalaServiceLoader}

import scala.util.Try

object ModelData {

  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  import net.ceedubs.ficus.readers.ValueReader

  def apply(config: Config, classPathConfigKey: String, processConfigKey: String) : ModelData = {

    implicit val urlValueReader: ValueReader[URL] = ValueReader[String]
      .map(value => Try(new URL(value)).getOrElse(new File(value).toURI.toURL))

    val classpathConfig = config.as[ClasspathConfig](classPathConfigKey)
    val processConfig = config.getConfig(processConfigKey)

    //TODO: ability to generate additional classpath?
    val jarClassLoader = ModelClassLoader(classpathConfig.urls)
    ClassLoaderModelData(processConfig, jarClassLoader)
  }

  //TODO: remove jarPath
  case class ClasspathConfig(jarPath: Option[URL], classpath: Option[List[URL]]) {
    def urls: List[URL] = jarPath.toList ++ classpath.getOrElse(List())
  }

}


case class ClassLoaderModelData(processConfig: Config, modelClassLoader: ModelClassLoader)
  extends ModelData {

  //this is not lazy, to be able to detect if creator can be created...
  val configCreator : ProcessConfigCreator = ProcessConfigCreatorLoader.loadProcessConfigCreator(modelClassLoader.classLoader)

  lazy val migrations: ProcessMigrations = {
    Multiplicity(ScalaServiceLoader.load[ProcessMigrations](modelClassLoader.classLoader)) match {
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
    ThreadUtils.withThisAsContextClassLoader(modelClassLoader.classLoader) {
      block
    }
  }

  def modelClassLoader : ModelClassLoader
}
