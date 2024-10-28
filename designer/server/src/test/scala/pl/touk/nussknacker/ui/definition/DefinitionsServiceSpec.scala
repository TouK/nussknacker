package pl.touk.nussknacker.ui.definition

import com.typesafe.config.ConfigFactory
import org.scalatest.OptionValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.component._
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.editor._
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process.{EmptyProcessConfigCreator, ProcessObjectDependencies, WithCategories}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.definition.component.Components.ComponentDefinitionExtractionMode
import pl.touk.nussknacker.engine.definition.component.bultin.BuiltInComponentsDefinitionsPreparer
import pl.touk.nussknacker.engine.definition.fragment.FragmentComponentDefinitionExtractor
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.modelconfig.ComponentsUiConfigParser
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.engine.{ModelData, ProcessingTypeConfig}
import pl.touk.nussknacker.restmodel.definition.UIDefinitions
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.test.config.ConfigWithScalaVersion
import pl.touk.nussknacker.test.config.WithSimplifiedDesignerConfig.TestProcessingType.Streaming
import pl.touk.nussknacker.test.mock.{MockDeploymentManager, StubFragmentRepository, TestAdditionalUIConfigProvider}
import pl.touk.nussknacker.test.utils.domain.ProcessTestData
import pl.touk.nussknacker.ui.definition.DefinitionsService.{ComponentUiConfigMode, createUIScenarioPropertyConfig}
import pl.touk.nussknacker.ui.process.processingtype.DesignerModelData.DynamicComponentsStaticDefinitions
import pl.touk.nussknacker.ui.security.api.AdminUser

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class DefinitionsServiceSpec extends AnyFunSuite with Matchers with PatientScalaFutures with OptionValues {

  object TestService extends Service {

    @MethodToInvoke
    def method(
        @ParamName("paramDualEditor")
        @DualEditor(
          simpleEditor = new SimpleEditor(
            `type` = SimpleEditorType.FIXED_VALUES_EDITOR,
            possibleValues = Array(new LabeledExpression(expression = "expression", label = "label"))
          ),
          defaultMode = DualEditorMode.SIMPLE
        )
        input: String,
        @SimpleEditor(
          `type` = SimpleEditorType.STRING_EDITOR
        )
        @ParamName("paramStringEditor")
        param2: String,
        @ParamName("paramRawEditor")
        @RawEditor
        param3: String
    ): Future[String] = ???

  }

  test("should read editor from annotations") {
    val model: ModelData = LocalModelData(
      ConfigWithScalaVersion.StreamingProcessTypeConfig.resolved.getConfig("modelConfig"),
      List(ComponentDefinition("enricher", TestService))
    )

    val definitions = prepareDefinitions(model, List.empty)

    definitions
      .components(ComponentId(ComponentType.Service, "enricher"))
      .parameters
      .map(p => (p.name, p.editor))
      .toMap shouldBe Map(
      "paramDualEditor" -> DualParameterEditor(
        simpleEditor = FixedValuesParameterEditor(possibleValues = List(FixedExpressionValue("expression", "label"))),
        defaultMode = DualEditorMode.SIMPLE
      ),
      "paramStringEditor" -> StringParameterEditor,
      "paramRawEditor"    -> RawParameterEditor
    )
  }

  test("should read parameter's label from configuration") {
    val labelSpecifiedInConfiguration = "Human Readable Label"
    val model: ModelData = LocalModelData(
      ConfigFactory.parseString(s"""componentsUiConfig {
          |  service-enricher: {
          |    params {
          |      paramRawEditor {
          |        label: "$labelSpecifiedInConfiguration"
          |      }
          |    }
          |  }
          |}
          |""".stripMargin),
      List(ComponentDefinition("enricher", TestService))
    )

    val definitions = prepareDefinitions(model, List.empty)

    definitions
      .components(ComponentId(ComponentType.Service, "enricher"))
      .parameters
      .map(p => (p.name, p.label))
      .toMap shouldBe Map(
      "paramDualEditor"   -> "paramDualEditor",
      "paramStringEditor" -> "paramStringEditor",
      "paramRawEditor"    -> labelSpecifiedInConfiguration
    )
  }

  test("should hide component in hidden component group") {
    val typeConfig = ProcessingTypeConfig.read(ConfigWithScalaVersion.StreamingProcessTypeConfig)
    val model = LocalModelData(
      typeConfig.modelConfig.resolved,
      List.empty,
      // TODO: use ComponentDefinition instead. Before this, add component group parameter into ComponentDefinition
      new EmptyProcessConfigCreator {
        override def services(
            modelDependencies: ProcessObjectDependencies
        ): Map[String, WithCategories[Service]] =
          Map(
            "enricher" -> WithCategories.anyCategory(TestService),
            "hiddenEnricher" -> WithCategories
              .anyCategory(TestService)
              .withComponentConfig(
                ComponentConfig.zero.copy(componentGroup = Some(ComponentGroupName("hiddenComponentGroup")))
              )
          )
      }
    )

    val definitions = prepareDefinitions(model, List.empty)

    definitions.componentGroups.filter(_.name == ComponentGroupName("hiddenComponentGroup")) shouldBe empty
  }

  test("should be able to setup component group name programmatically") {
    val typeConfig      = ProcessingTypeConfig.read(ConfigWithScalaVersion.StreamingProcessTypeConfig)
    val targetGroupName = ComponentGroupName("someGroup")
    val model = LocalModelData(
      typeConfig.modelConfig.resolved,
      List.empty,
      // TODO: use ComponentDefinition instead. Before this, add component group parameter into ComponentDefinition
      new EmptyProcessConfigCreator {
        override def services(
            modelDependencies: ProcessObjectDependencies
        ): Map[String, WithCategories[Service]] = {
          Map(
            "someGenericNode" -> WithCategories
              .anyCategory(TestService)
              .withComponentConfig(
                ComponentConfig.zero.copy(componentGroup = Some(targetGroupName))
              )
          )
        }
      }
    )

    val componentsGroups = prepareDefinitions(model, List.empty).componentGroups

    componentsGroups.map(_.name) should contain(targetGroupName)
  }

  test("should override fragment's docsUrl from config with value from 'properties'") {
    val typeConfig       = ProcessingTypeConfig.read(ConfigWithScalaVersion.StreamingProcessTypeConfig)
    val model: ModelData = LocalModelData(typeConfig.modelConfig.resolved, List.empty)
    val fragment         = ProcessTestData.sampleFragmentOneOut
    val docsUrl          = "https://nussknacker.io/documentation/"
    val fragmentWithDocsUrl = fragment.copy(metaData =
      fragment.metaData.withTypeSpecificData(typeSpecificData = FragmentSpecificData(Some(docsUrl)))
    )

    val definitions = prepareDefinitions(model, List(fragmentWithDocsUrl))

    definitions.components(ComponentId(ComponentType.Fragment, fragment.name.value)).docsUrl shouldBe Some(docsUrl)
  }

  test("should skip empty fragments in definitions") {
    val typeConfig       = ProcessingTypeConfig.read(ConfigWithScalaVersion.StreamingProcessTypeConfig)
    val model: ModelData = LocalModelData(typeConfig.modelConfig.resolved, List.empty)

    val fragment    = CanonicalProcess(MetaData("emptyFragment", FragmentSpecificData()), List.empty, List.empty)
    val definitions = prepareDefinitions(model, List(fragment))

    definitions.components.get(ComponentId(ComponentType.Fragment, fragment.name.value)) shouldBe empty
  }

  test("should return outputParameters in fragment's definition") {
    val typeConfig       = ProcessingTypeConfig.read(ConfigWithScalaVersion.StreamingProcessTypeConfig)
    val model: ModelData = LocalModelData(typeConfig.modelConfig.resolved, List.empty)

    val fragment    = ProcessTestData.sampleFragmentOneOut
    val definitions = prepareDefinitions(model, List(ProcessTestData.sampleFragmentOneOut))

    val fragmentDefinition =
      definitions.components.get(ComponentId(ComponentType.Fragment, fragment.name.value)).value
    val outputParameters = fragmentDefinition.outputParameters.value
    outputParameters shouldEqual List("output")
  }

  test("should override component's parameter config with additionally provided config") {
    val model: ModelData = localModelWithAdditionalConfig
    val definitions      = prepareDefinitions(model, List.empty)

    val expectedOverridenParamDefaultValue =
      "paramStringEditor" -> Expression.spel("'default-from-additional-ui-config-provider'")
    val returnedParamDefaultValues =
      definitions.components(ComponentId(ComponentType.Service, "enricher")).parameters.map { param =>
        param.name -> param.defaultValue
      }
    returnedParamDefaultValues should contain(expectedOverridenParamDefaultValue)
  }

  test(
    "should not override component's parameter config with additionally provided config when basic config requested"
  ) {
    val model: ModelData = localModelWithAdditionalConfig
    val definitions      = prepareDefinitions(model, List.empty, ComponentUiConfigMode.BasicConfig)

    val expectedParamDefaultValue =
      "paramStringEditor" -> Expression.spel("''")
    val returnedParamDefaultValues =
      definitions.components(ComponentId(ComponentType.Service, "enricher")).parameters.map { param =>
        param.name -> param.defaultValue
      }
    returnedParamDefaultValues should contain(expectedParamDefaultValue)
  }

  private def localModelWithAdditionalConfig = {
    LocalModelData(
      ConfigWithScalaVersion.StreamingProcessTypeConfig.resolved.getConfig("modelConfig"),
      List(ComponentDefinition("enricher", TestService)),
      additionalConfigsFromProvider = Map(
        DesignerWideComponentId("streaming-service-enricher") -> ComponentAdditionalConfig(
          parameterConfigs = Map(
            ParameterName("paramStringEditor") -> ParameterAdditionalUIConfig(
              required = false,
              initialValue = Some(
                FixedExpressionValue(
                  "'default-from-additional-ui-config-provider'",
                  "default-from-additional-ui-config-provider"
                )
              ),
              hintText = None,
              valueEditor = None,
              valueCompileTimeValidation = None
            )
          ),
          componentGroup = None
        )
      ),
      componentDefinitionExtractionMode = ComponentDefinitionExtractionMode.FinalAndBasicDefinitions
    )
  }

  test("should override component's component groups with additionally provided config") {
    val model: ModelData = LocalModelData(
      ConfigWithScalaVersion.StreamingProcessTypeConfig.resolved.getConfig("modelConfig"),
      List(ComponentDefinition("enricher", TestService)),
      additionalConfigsFromProvider = Map(
        DesignerWideComponentId("streaming-service-enricher") -> ComponentAdditionalConfig(
          parameterConfigs = Map.empty,
          componentGroup = Some(TestAdditionalUIConfigProvider.componentGroupName)
        )
      )
    )

    val definitions = prepareDefinitions(model, List.empty)

    definitions.componentGroups.map(c => (c.name, c.components.head.label)) should contain(
      TestAdditionalUIConfigProvider.componentGroupName,
      "enricher"
    )
  }

  test("should override scenario properties with additionally provided config") {
    val typeConfig       = ProcessingTypeConfig.read(ConfigWithScalaVersion.StreamingProcessTypeConfig)
    val model: ModelData = LocalModelData(typeConfig.modelConfig.resolved, List.empty)

    val uiDefinitions = prepareDefinitions(model, List.empty)

    uiDefinitions.scenarioProperties.propertiesConfig shouldBe TestAdditionalUIConfigProvider.scenarioPropertyConfigOverride
      .mapValuesNow(createUIScenarioPropertyConfig)
  }

  private def prepareDefinitions(
      model: ModelData,
      fragmentScenarios: List[CanonicalProcess],
      componentUiConfigMode: ComponentUiConfigMode = ComponentUiConfigMode.EnrichedWithAdditionalConfig
  ): UIDefinitions = {
    val processingType = Streaming

    val alignedComponentsDefinitionProvider = new AlignedComponentsDefinitionProvider(
      new BuiltInComponentsDefinitionsPreparer(ComponentsUiConfigParser.parse(model.modelConfig)),
      new FragmentComponentDefinitionExtractor(
        getClass.getClassLoader,
        Some(_),
        DesignerWideComponentId.default(processingType.stringify, _)
      ),
      model.modelDefinition,
      ProcessingMode.UnboundedStream
    )

    new DefinitionsService(
      modelData = model,
      staticDefinitionForDynamicComponents = DynamicComponentsStaticDefinitions(
        finalDefinitions = Map.empty,
        basicDefinitions = componentUiConfigMode match {
          case ComponentUiConfigMode.EnrichedWithAdditionalConfig => None
          case ComponentUiConfigMode.BasicConfig                  => Some(Map.empty)
        }
      ),
      fragmentPropertiesConfig = Map.empty,
      scenarioPropertiesConfig = Map.empty,
      deploymentManager = new MockDeploymentManager,
      alignedComponentsDefinitionProvider = alignedComponentsDefinitionProvider,
      scenarioPropertiesConfigFinalizer =
        new ScenarioPropertiesConfigFinalizer(TestAdditionalUIConfigProvider, processingType.stringify),
      fragmentRepository = new StubFragmentRepository(Map(processingType.stringify -> fragmentScenarios)),
      fragmentPropertiesDocsUrl = None
    ).prepareUIDefinitions(
      processingType = processingType.stringify,
      forFragment = false,
      componentUiConfigMode = componentUiConfigMode
    )(
      AdminUser("admin", "admin")
    ).futureValue
  }

}
