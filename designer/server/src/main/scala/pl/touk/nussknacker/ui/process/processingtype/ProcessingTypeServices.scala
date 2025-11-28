package pl.touk.nussknacker.ui.process.processingtype

import pl.touk.nussknacker.engine.ProcessingTypeConfig.LimitsConfig
import pl.touk.nussknacker.engine.api.component.{
  AdditionalUIConfigProvider,
  ComponentAdditionalConfig,
  DesignerWideComponentId
}
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.engine.compile.ProcessValidator
import pl.touk.nussknacker.engine.definition.action.ModelDataActionInfoProvider
import pl.touk.nussknacker.engine.dict.ProcessDictSubstitutor
import pl.touk.nussknacker.ui.config.DesignerConfig
import pl.touk.nussknacker.ui.definition.{
  AlignedComponentsDefinitionProvider,
  DefinitionsService,
  ScenarioPropertiesConfigFinalizer
}
import pl.touk.nussknacker.ui.definition.component.ComponentServiceProcessingTypeData
import pl.touk.nussknacker.ui.process.NewProcessPreparer
import pl.touk.nussknacker.ui.process.deployment.{ActionInfoService, ScenarioResolver, ScenarioTestExecutorServiceImpl}
import pl.touk.nussknacker.ui.process.fragment.{FragmentRepository, FragmentResolver}
import pl.touk.nussknacker.ui.process.test.ScenarioTestService
import pl.touk.nussknacker.ui.processreport.ProcessCounter
import pl.touk.nussknacker.ui.suggester.ExpressionSuggester
import pl.touk.nussknacker.ui.uiresolving.UIProcessResolver
import pl.touk.nussknacker.ui.validation.{
  NodeValidator,
  ParametersValidator,
  ScenarioLabelsValidator,
  UIProcessValidator
}

import scala.concurrent.ExecutionContext

final class ProcessingTypeServices private (
    private val processingTypeData: ProcessingTypeData,
    val alignedComponentsDefinitionProvider: AlignedComponentsDefinitionProvider,
    val definitionService: DefinitionsService,
    val nodeValidator: NodeValidator,
    val scenarioValidator: UIProcessValidator,
    val parametersValidator: ParametersValidator,
    val expressionSuggester: ExpressionSuggester,
    val scenarioResolver: ScenarioResolver,
    val processResolver: UIProcessResolver,
    val scenarioTestService: ScenarioTestService,
    val actionInfoService: ActionInfoService,
    val newProcessPreparer: NewProcessPreparer,
) {

  def processingType: ProcessingType = processingTypeData.processingType

  def category: String = processingTypeData.category

  def limitsConfig: LimitsConfig = processingTypeData.limitsConfig

  def designerModelData: DesignerModelData = processingTypeData.designerModelData

  // TODO: We should replace all usages of this method with access to DeploymentData which is created separately from model
  //       to fully split deployment managers from model
  def deploymentData: DeploymentData = processingTypeData.deploymentData

  lazy val additionalComponentConfigs: Map[DesignerWideComponentId, ComponentAdditionalConfig] =
    processingTypeData.designerModelData.modelData.additionalConfigsFromProvider

  def componentServiceProcessingTypeData: ComponentServiceProcessingTypeData =
    ComponentServiceProcessingTypeData(alignedComponentsDefinitionProvider, processingTypeData.category)

}

object ProcessingTypeServices {

  def create(
      designerConfig: DesignerConfig,
      additionalUIConfigProvider: AdditionalUIConfigProvider,
      fragmentRepository: FragmentRepository,
      fragmentResolver: FragmentResolver,
      counter: ProcessCounter,
      processingTypeData: ProcessingTypeData
  )(implicit ec: ExecutionContext): ProcessingTypeServices = {
    val scenarioCompilationDependenciesResource =
      processingTypeData.deploymentData.validDeploymentManagerOrStub.scenarioCompilationDependenciesResource
    val nodeValidator = new NodeValidator(
      processingTypeData.designerModelData.modelData,
      scenarioCompilationDependenciesResource,
      fragmentRepository
    )
    val scenarioValidator = new UIProcessValidator(
      processingType = processingTypeData.processingType,
      validator = ProcessValidator.default(processingTypeData.designerModelData.modelData),
      scenarioProperties = processingTypeData.designerModelData.scenarioPropertiesConfig,
      scenarioPropertiesConfigFinalizer =
        new ScenarioPropertiesConfigFinalizer(additionalUIConfigProvider, processingTypeData.processingType),
      engineScenarioCompilationDependenciesResource = scenarioCompilationDependenciesResource,
      scenarioLabelsValidator = new ScenarioLabelsValidator(designerConfig.scenarioLabelConfig),
      additionalValidators = processingTypeData.deploymentData.additionalValidators,
      fragmentResolver = fragmentResolver,
      stickyNotesSettings = designerConfig.stickyNotesSettings
    )
    val substitutor =
      ProcessDictSubstitutor(processingTypeData.designerModelData.modelData.designerDictServices.dictRegistry)
    val processResolver   = new UIProcessResolver(scenarioValidator, substitutor)
    val scenarioResolver  = new ScenarioResolver(fragmentResolver, processingTypeData.processingType)
    val deploymentManager = processingTypeData.deploymentData.validDeploymentManagerOrStub
    val scenarioTestService = new ScenarioTestService(
      designerConfig.testDataSettings,
      processingTypeData.designerModelData.modelData,
      scenarioCompilationDependenciesResource,
      processResolver,
      counter,
      new ScenarioTestExecutorServiceImpl(scenarioResolver, deploymentManager),
      scenarioValidator
    )
    val actionInfoService = new ActionInfoService(
      new ModelDataActionInfoProvider(
        processingTypeData.designerModelData.modelData,
        scenarioCompilationDependenciesResource
      ),
      processResolver,
      scenarioResolver
    )
    val newProcessPreparer = new NewProcessPreparer(
      processingTypeData.deploymentData.metaDataInitializer,
      processingTypeData.designerModelData.scenarioPropertiesConfig,
      new ScenarioPropertiesConfigFinalizer(additionalUIConfigProvider, processingTypeData.processingType),
    )
    val alignedComponentsDefinitionProvider = AlignedComponentsDefinitionProvider(processingTypeData.designerModelData)
    val definitionService = DefinitionsService(
      processingTypeData,
      alignedComponentsDefinitionProvider,
      new ScenarioPropertiesConfigFinalizer(additionalUIConfigProvider, processingTypeData.processingType),
      fragmentRepository,
      designerConfig.fragmentPropertiesDocsUrl
    )
    val parameterValidator =
      new ParametersValidator(
        processingTypeData.designerModelData.modelData,
        processingTypeData.designerModelData.scenarioPropertiesConfig.keys
      )
    val expressionSuggester = ExpressionSuggester(
      processingTypeData.designerModelData.modelData,
      processingTypeData.designerModelData.scenarioPropertiesConfig.keys
    )
    new ProcessingTypeServices(
      processingTypeData,
      alignedComponentsDefinitionProvider,
      definitionService,
      nodeValidator,
      scenarioValidator,
      parameterValidator,
      expressionSuggester,
      scenarioResolver,
      processResolver,
      scenarioTestService,
      actionInfoService,
      newProcessPreparer,
    )
  }

}
