package pl.touk.nussknacker.engine

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.ClassLoaderModelData.ExtractDefinitionFunImpl
import pl.touk.nussknacker.engine.ModelData.ExtractDefinitionFun
import pl.touk.nussknacker.engine.api.component.{ComponentAdditionalConfig, ComponentId, ComponentInfo}
import pl.touk.nussknacker.engine.api.dict.{DictServicesFactory, EngineDictRegistry, UiDictServices}
import pl.touk.nussknacker.engine.api.namespaces.ObjectNaming
import pl.touk.nussknacker.engine.api.process.{ProcessConfigCreator, ProcessObjectDependencies}
import pl.touk.nussknacker.engine.definition.component.ComponentDefinitionWithImplementation
import pl.touk.nussknacker.engine.definition.model.{
  ModelDefinition,
  ModelDefinitionExtractor,
  ModelDefinitionWithClasses
}
import pl.touk.nussknacker.engine.dict.DictServicesFactoryLoader
import pl.touk.nussknacker.engine.migration.ProcessMigrations
import pl.touk.nussknacker.engine.modelconfig.{DefaultModelConfigLoader, InputConfigDuringExecution, ModelConfigLoader}
import pl.touk.nussknacker.engine.util.ThreadUtils
import pl.touk.nussknacker.engine.util.loader.{ModelClassLoader, ProcessConfigCreatorLoader, ScalaServiceLoader}
import pl.touk.nussknacker.engine.util.multiplicity.{Empty, Many, Multiplicity, One}
import pl.touk.nussknacker.engine.util.namespaces.ObjectNamingProvider

import java.net.URL

object ModelData extends LazyLogging {

  type ExtractDefinitionFun =
    (
        ClassLoader,
        ProcessObjectDependencies,
        ComponentInfo => ComponentId,
        Map[ComponentId, ComponentAdditionalConfig]
    ) => ModelDefinition[ComponentDefinitionWithImplementation]

  def apply(
      processingTypeConfig: ProcessingTypeConfig,
      additionalConfigsFromProvider: Map[ComponentId, ComponentAdditionalConfig],
      componentInfoToId: ComponentInfo => ComponentId
  ): ModelData = {
    ModelData(
      processingTypeConfig.modelConfig,
      ModelClassLoader(processingTypeConfig.classPath),
      Some(processingTypeConfig.category),
      componentInfoToId,
      additionalConfigsFromProvider
    )
  }

  // On the runtime side, we get only model config, not the whole processing type config,
  // so we don't have category, processingType and additionalConfigsFromProvider
  // But it is not a big deal, because scenario was already validated before deploy, so we already check that
  // we don't use not allowed components for a given category
  // and that the scenario doesn't violate validators introduced by additionalConfigsFromProvider
  def apply(inputConfig: Config, modelClassLoader: ModelClassLoader, category: Option[String]): ModelData = {
    ModelData(
      inputConfig = ConfigWithUnresolvedVersion(modelClassLoader.classLoader, inputConfig),
      modelClassLoader = modelClassLoader,
      category = category,
      componentInfoToId = info => ComponentId(info.toString),
      additionalConfigsFromProvider = Map.empty
    )
  }

  def apply(
      inputConfig: ConfigWithUnresolvedVersion,
      modelClassLoader: ModelClassLoader,
      category: Option[String],
      componentInfoToId: ComponentInfo => ComponentId,
      additionalConfigsFromProvider: Map[ComponentId, ComponentAdditionalConfig]
  ): ModelData = {
    logger.debug("Loading model data from: " + modelClassLoader)
    ClassLoaderModelData(
      modelConfigLoader =>
        modelConfigLoader.resolveInputConfigDuringExecution(inputConfig, modelClassLoader.classLoader),
      modelClassLoader,
      category,
      componentInfoToId,
      additionalConfigsFromProvider
    )
  }

  // Used on Flink, where we start already with resolved config so we should not resolve it twice.
  def duringExecution(inputConfig: Config): ModelData = {
    ClassLoaderModelData(
      _ => InputConfigDuringExecution(inputConfig),
      ModelClassLoader(Nil),
      None,
      info => ComponentId(info.toString),
      Map.empty
    )
  }

  implicit class BaseModelDataExt(baseModelData: BaseModelData) {
    def asInvokableModelData: ModelData = baseModelData.asInstanceOf[ModelData]
  }

}

case class ClassLoaderModelData private (
    private val resolveInputConfigDuringExecution: ModelConfigLoader => InputConfigDuringExecution,
    modelClassLoader: ModelClassLoader,
    override val category: Option[String],
    override val componentInfoToId: ComponentInfo => ComponentId,
    override val additionalConfigsFromProvider: Map[ComponentId, ComponentAdditionalConfig]
) extends ModelData {

  // this is not lazy, to be able to detect if creator can be created...
  override val configCreator: ProcessConfigCreator = ProcessConfigCreatorLoader.justOne(modelClassLoader.classLoader)

  override lazy val modelConfigLoader: ModelConfigLoader = {
    Multiplicity(ScalaServiceLoader.load[ModelConfigLoader](modelClassLoader.classLoader)) match {
      case Empty()                => new DefaultModelConfigLoader
      case One(modelConfigLoader) => modelConfigLoader
      case Many(moreThanOne) =>
        throw new IllegalArgumentException(s"More than one ModelConfigLoader instance found: $moreThanOne")
    }
  }

  override lazy val inputConfigDuringExecution: InputConfigDuringExecution = resolveInputConfigDuringExecution(
    modelConfigLoader
  )

  override lazy val migrations: ProcessMigrations = {
    Multiplicity(ScalaServiceLoader.load[ProcessMigrations](modelClassLoader.classLoader)) match {
      case Empty()            => ProcessMigrations.empty
      case One(migrationsDef) => migrationsDef
      case Many(moreThanOne) =>
        throw new IllegalArgumentException(s"More than one ProcessMigrations instance found: $moreThanOne")
    }
  }

  override def objectNaming: ObjectNaming = ObjectNamingProvider(modelClassLoader.classLoader)

  override val extractModelDefinitionFun: ExtractDefinitionFun = new ExtractDefinitionFunImpl(configCreator, category)

}

object ClassLoaderModelData {

  class ExtractDefinitionFunImpl(configCreator: ProcessConfigCreator, category: Option[String])
      extends ExtractDefinitionFun
      with Serializable {

    override def apply(
        classLoader: ClassLoader,
        modelDependencies: ProcessObjectDependencies,
        componentInfoToId: ComponentInfo => ComponentId,
        additionalConfigsFromProvider: Map[ComponentId, ComponentAdditionalConfig]
    ): ModelDefinition[ComponentDefinitionWithImplementation] = {
      ModelDefinitionExtractor.extractModelDefinition(
        configCreator,
        classLoader,
        modelDependencies,
        category,
        componentInfoToId,
        additionalConfigsFromProvider
      )
    }

  }

}

trait ModelData extends BaseModelData with AutoCloseable {

  // TODO: We should move model extensions that are used only on designer side into a separate wrapping class e.g. DesignerModelData
  //       Thanks to that we 1) avoid unnecessary noice on runtime side, but also 2) we still see what kind of model extensions
  //       do we have. See AdditionalInfoProviders as well
  def migrations: ProcessMigrations

  final def modelInfo: Map[String, String] = configCreator.buildInfo()

  def configCreator: ProcessConfigCreator

  // It won't be necessary after we get rid of ProcessConfigCreator API
  def category: Option[String]

  def componentInfoToId: ComponentInfo => ComponentId

  def additionalConfigsFromProvider: Map[ComponentId, ComponentAdditionalConfig]

  final lazy val modelDefinitionWithClasses: ModelDefinitionWithClasses = {
    val modelDefinitions = withThisAsContextClassLoader {
      extractModelDefinitionFun(
        modelClassLoader.classLoader,
        ProcessObjectDependencies(modelConfig, objectNaming),
        componentInfoToId,
        additionalConfigsFromProvider
      )
    }
    ModelDefinitionWithClasses(modelDefinitions)
  }

  // This has to be a function instead of method to explicitly define what scope will be serializable by Flink.
  // See parameters of implementing functions
  def extractModelDefinitionFun: ExtractDefinitionFun

  final def modelDefinition: ModelDefinition[ComponentDefinitionWithImplementation] =
    modelDefinitionWithClasses.modelDefinition

  private lazy val dictServicesFactory: DictServicesFactory =
    DictServicesFactoryLoader.justOne(modelClassLoader.classLoader)

  final lazy val designerDictServices: UiDictServices =
    dictServicesFactory.createUiDictServices(modelDefinition.expressionConfig.dictionaries, modelConfig)

  final lazy val engineDictRegistry: EngineDictRegistry =
    dictServicesFactory.createEngineDictRegistry(modelDefinition.expressionConfig.dictionaries)

  // TODO: remove it, see notice in CustomProcessValidatorFactory
  final def customProcessValidator: CustomProcessValidator = {
    CustomProcessValidatorLoader.loadProcessValidators(modelClassLoader.classLoader, modelConfig)
  }

  final def withThisAsContextClassLoader[T](block: => T): T = {
    ThreadUtils.withThisAsContextClassLoader(modelClassLoader.classLoader) {
      block
    }
  }

  final override def modelClassLoaderUrls: List[URL] = modelClassLoader.urls

  def modelClassLoader: ModelClassLoader

  def modelConfigLoader: ModelConfigLoader

  final override lazy val modelConfig: Config =
    modelConfigLoader.resolveConfig(inputConfigDuringExecution, modelClassLoader.classLoader)

  final def close(): Unit = {
    designerDictServices.close()
  }

}
