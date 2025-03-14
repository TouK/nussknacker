package pl.touk.nussknacker.ui.process.processingtype

import pl.touk.nussknacker.engine.api.component.{
  AdditionalUIConfigProvider,
  ComponentAdditionalConfig,
  DesignerWideComponentId
}
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.engine.compile.ProcessValidator
import pl.touk.nussknacker.engine.definition.action.ModelDataActionInfoProvider
import pl.touk.nussknacker.engine.definition.test.ModelDataTestInfoProvider
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
import pl.touk.nussknacker.ui.process.test.{PreliminaryScenarioTestDataSerDe, ScenarioTestService}
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

final case class ProcessingTypeServices private (
    private val processingTypeData: ProcessingTypeData,
    alignedComponentsDefinitionProvider: AlignedComponentsDefinitionProvider,
    definitionService: DefinitionsService,
    nodeValidator: NodeValidator,
    scenarioValidator: UIProcessValidator,
    parametersValidator: ParametersValidator,
    expressionSuggester: ExpressionSuggester,
    scenarioResolver: ScenarioResolver,
    processResolver: UIProcessResolver,
    scenarioTestService: ScenarioTestService,
    actionInfoService: ActionInfoService,
    newProcessPreparer: NewProcessPreparer,
) {

  def processingType: ProcessingType = processingTypeData.processingType

  def category: String = processingTypeData.category

  def designerModelData: DesignerModelData = processingTypeData.designerModelData

  def deploymentData: DeploymentData = processingTypeData.deploymentData

  lazy val additionalComponentConfigs: Map[DesignerWideComponentId, ComponentAdditionalConfig] =
    processingTypeData.designerModelData.modelData.additionalConfigsFromProvider

  def componentServiceProcessingTypeData: ComponentServiceProcessingTypeData =
    ComponentServiceProcessingTypeData(alignedComponentsDefinitionProvider, processingTypeData.category)

}

object ProcessingTypeServices {

  import net.ceedubs.ficus.Ficus._

  def create(
      designerConfig: DesignerConfig,
      additionalUIConfigProvider: AdditionalUIConfigProvider,
      fragmentRepository: FragmentRepository,
      fragmentResolver: FragmentResolver,
      counter: ProcessCounter,
      processingTypeData: ProcessingTypeData
  )(implicit ec: ExecutionContext): ProcessingTypeServices = {
    val nodeValidator = new NodeValidator(processingTypeData.designerModelData.modelData, fragmentRepository)
    val scenarioValidator = new UIProcessValidator(
      processingTypeData.processingType,
      ProcessValidator.default(processingTypeData.designerModelData.modelData),
      processingTypeData.deploymentData.scenarioPropertiesConfig,
      new ScenarioPropertiesConfigFinalizer(additionalUIConfigProvider, processingTypeData.processingType),
      new ScenarioLabelsValidator(designerConfig.scenarioLabelConfig),
      processingTypeData.deploymentData.additionalValidators,
      fragmentResolver
    )
    val substitutor =
      ProcessDictSubstitutor(processingTypeData.designerModelData.modelData.designerDictServices.dictRegistry)
    val processResolver   = new UIProcessResolver(scenarioValidator, substitutor)
    val scenarioResolver  = new ScenarioResolver(fragmentResolver, processingTypeData.processingType)
    val deploymentManager = processingTypeData.deploymentData.validDeploymentManagerOrStub
    val scenarioTestService = new ScenarioTestService(
      new ModelDataTestInfoProvider(processingTypeData.designerModelData.modelData),
      processResolver,
      designerConfig.testDataSettings,
      new PreliminaryScenarioTestDataSerDe(designerConfig.testDataSettings),
      counter,
      new ScenarioTestExecutorServiceImpl(scenarioResolver, deploymentManager)
    )
    val actionInfoService = new ActionInfoService(
      new ModelDataActionInfoProvider(processingTypeData.designerModelData.modelData),
      processResolver,
      scenarioResolver
    )
    val newProcessPreparer = new NewProcessPreparer(
      processingTypeData.deploymentData.metaDataInitializer,
      processingTypeData.deploymentData.scenarioPropertiesConfig,
      new ScenarioPropertiesConfigFinalizer(additionalUIConfigProvider, processingTypeData.processingType),
    )
    val alignedComponentsDefinitionProvider = AlignedComponentsDefinitionProvider(processingTypeData.designerModelData)
    val definitionService = DefinitionsService(
      processingTypeData,
      alignedComponentsDefinitionProvider,
      new ScenarioPropertiesConfigFinalizer(additionalUIConfigProvider, processingTypeData.processingType),
      fragmentRepository,
      designerConfig.rawConfig.getAs[String]("fragmentPropertiesDocsUrl")
    )
    val parameterValidator =
      new ParametersValidator(
        processingTypeData.designerModelData.modelData,
        processingTypeData.deploymentData.scenarioPropertiesConfig.keys
      )
    val expressionSuggester = ExpressionSuggester(
      processingTypeData.designerModelData.modelData,
      processingTypeData.deploymentData.scenarioPropertiesConfig.keys
    )
    ProcessingTypeServices(
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
