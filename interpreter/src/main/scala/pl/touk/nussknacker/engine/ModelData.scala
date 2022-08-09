package pl.touk.nussknacker.engine

import java.net.URL
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.dict.UiDictServices
import pl.touk.nussknacker.engine.api.namespaces.ObjectNaming
import pl.touk.nussknacker.engine.api.process.{ProcessConfigCreator, ProcessObjectDependencies}
import pl.touk.nussknacker.engine.compile.ProcessValidator
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectDefinition
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.ProcessDefinition
import pl.touk.nussknacker.engine.definition.{DefinitionExtractor, ProcessDefinitionExtractor, TypeInfos}
import pl.touk.nussknacker.engine.dict.DictServicesFactoryLoader
import pl.touk.nussknacker.engine.migration.ProcessMigrations
import pl.touk.nussknacker.engine.modelconfig.{DefaultModelConfigLoader, InputConfigDuringExecution, ModelConfigLoader}
import pl.touk.nussknacker.engine.util.ThreadUtils
import pl.touk.nussknacker.engine.util.loader.{ModelClassLoader, ProcessConfigCreatorLoader, ScalaServiceLoader}
import pl.touk.nussknacker.engine.util.multiplicity.{Empty, Many, Multiplicity, One}
import pl.touk.nussknacker.engine.util.namespaces.ObjectNamingProvider

object ModelData extends LazyLogging {

  def apply(processingTypeConfig: ProcessingTypeConfig): ModelData = {
    ModelData(processingTypeConfig.modelConfig, ModelClassLoader(processingTypeConfig.classPath))
  }

  def apply(inputConfig: Config, modelClassLoader: ModelClassLoader) : ModelData = {
    logger.debug("Loading model data from: " + modelClassLoader)
    ClassLoaderModelData(
      modelConfigLoader => modelConfigLoader.resolveInputConfigDuringExecution(inputConfig, modelClassLoader.classLoader),
      modelClassLoader)
  }

  // Used on Flink, where we start already with resolved config so we should not resolve it twice.
  def duringExecution(inputConfig: Config): ModelData = {
    ClassLoaderModelData(_ => InputConfigDuringExecution(inputConfig), ModelClassLoader(Nil))
  }

  case class ClasspathConfig(classpath: List[URL])

  implicit class BaseModelDataExt(baseModelData: BaseModelData) {
    def asInvokableModelData: ModelData = baseModelData.asInstanceOf[ModelData]
  }

}


case class ClassLoaderModelData private(private val resolveInputConfigDuringExecution: ModelConfigLoader => InputConfigDuringExecution,
                                        modelClassLoader: ModelClassLoader)
  extends ModelData {

  //this is not lazy, to be able to detect if creator can be created...
  override val configCreator : ProcessConfigCreator = ProcessConfigCreatorLoader.justOne(modelClassLoader.classLoader)

  override lazy val modelConfigLoader: ModelConfigLoader = {
    Multiplicity(ScalaServiceLoader.load[ModelConfigLoader](modelClassLoader.classLoader)) match {
      case Empty() => new DefaultModelConfigLoader
      case One(modelConfigLoader) => modelConfigLoader
      case Many(moreThanOne) =>
        throw new IllegalArgumentException(s"More than one ModelConfigLoader instance found: $moreThanOne")
    }
  }

  override lazy val inputConfigDuringExecution: InputConfigDuringExecution = resolveInputConfigDuringExecution(modelConfigLoader)

  override lazy val migrations: ProcessMigrations = {
    Multiplicity(ScalaServiceLoader.load[ProcessMigrations](modelClassLoader.classLoader)) match {
      case Empty() => ProcessMigrations.empty
      case One(migrationsDef) => migrationsDef
      case Many(moreThanOne) =>
        throw new IllegalArgumentException(s"More than one ProcessMigrations instance found: $moreThanOne")
    }
  }

  override def objectNaming: ObjectNaming = ObjectNamingProvider(modelClassLoader.classLoader)
}

trait ModelData extends BaseModelData with AutoCloseable {

  def migrations: ProcessMigrations

  def configCreator: ProcessConfigCreator

  lazy val processWithObjectsDefinition: ProcessDefinition[DefinitionExtractor.ObjectWithMethodDef] =
    withThisAsContextClassLoader {
      ProcessDefinitionExtractor.extractObjectWithMethods(configCreator, ProcessObjectDependencies(processConfig, objectNaming))
    }

  lazy val processDefinition: ProcessDefinition[ObjectDefinition] = ProcessDefinitionExtractor.toObjectDefinition(processWithObjectsDefinition)

  lazy val typeDefinitions: Set[TypeInfos.ClazzDefinition] = ProcessDefinitionExtractor.extractTypes(processWithObjectsDefinition)

  // We can create dict services here because ModelData is fat object that is created once on start
  lazy val dictServices: UiDictServices =
    DictServicesFactoryLoader.justOne(modelClassLoader.classLoader).createUiDictServices(processDefinition.expressionConfig.dictionaries, processConfig)

  def prepareValidatorForCategory(category: Option[String]): ProcessValidator =
    ProcessValidator.default(
      category.map(processWithObjectsDefinition.forCategory).getOrElse(processWithObjectsDefinition),
      dictServices.dictRegistry,
      modelClassLoader.classLoader
    )

  def withThisAsContextClassLoader[T](block: => T) : T = {
    ThreadUtils.withThisAsContextClassLoader(modelClassLoader.classLoader) {
      block
    }
  }

  override def modelClassLoaderUrls: List[URL] = modelClassLoader.urls

  def modelClassLoader : ModelClassLoader

  def modelConfigLoader: ModelConfigLoader

  override lazy val processConfig: Config = modelConfigLoader.resolveConfig(inputConfigDuringExecution, modelClassLoader.classLoader)

  def close(): Unit = {
    dictServices.close()
  }

}
