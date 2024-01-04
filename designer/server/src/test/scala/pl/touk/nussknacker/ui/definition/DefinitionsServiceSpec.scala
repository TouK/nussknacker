package pl.touk.nussknacker.ui.definition

import com.typesafe.config.ConfigFactory
import org.scalatest.OptionValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.component._
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.context.transformation.{NodeDependencyValue, SingleInputGenericNodeTransformation}
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.editor._
import pl.touk.nussknacker.engine.api.process.{EmptyProcessConfigCreator, ProcessObjectDependencies, WithCategories}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.definition.component.ToStaticComponentDefinitionTransformer
import pl.touk.nussknacker.engine.definition.component.bultin.BuiltInComponentsStaticDefinitionsPreparer
import pl.touk.nussknacker.engine.definition.fragment.FragmentWithoutValidatorsDefinitionExtractor
import pl.touk.nussknacker.engine.modelconfig.ComponentsUiConfigParser
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.engine.{MetaDataInitializer, ModelData, ProcessingTypeConfig}
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.api.helpers.{
  MockDeploymentManager,
  ProcessTestData,
  StubFragmentRepository,
  TestProcessingTypes
}
import pl.touk.nussknacker.ui.definition.DefinitionsService.createUIScenarioPropertyConfig
import pl.touk.nussknacker.ui.security.api.AdminUser
import pl.touk.nussknacker.ui.util.ConfigWithScalaVersion

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

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

  object SampleGenericNodeTransformation
      extends CustomStreamTransformer
      with SingleInputGenericNodeTransformation[AnyRef] {

    override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(
        implicit nodeId: NodeId
    ): this.NodeTransformationDefinition = { case TransformationStep(Nil, _) =>
      FinalResults(context, Nil)
    }

    override def nodeDependencies: List[NodeDependency] = List.empty

    override def implementation(
        params: Map[String, Any],
        dependencies: List[NodeDependencyValue],
        finalState: Option[State]
    ): AnyRef =
      ???

  }

  test("should read editor from annotations") {
    val model: ModelData = LocalModelData(
      ConfigWithScalaVersion.StreamingProcessTypeConfig.resolved.getConfig("modelConfig"),
      List(ComponentDefinition("enricher", TestService))
    )

    val definitions = prepareDefinitions(model, List.empty)

    definitions
      .components(ComponentInfo(ComponentType.Service, "enricher"))
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
      .components(ComponentInfo(ComponentType.Service, "enricher"))
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
                SingleComponentConfig.zero.copy(componentGroup = Some(ComponentGroupName("hiddenComponentGroup")))
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
        override def customStreamTransformers(
            modelDependencies: ProcessObjectDependencies
        ): Map[String, WithCategories[CustomStreamTransformer]] = {
          Map(
            "someGenericNode" -> WithCategories
              .anyCategory(SampleGenericNodeTransformation)
              .withComponentConfig(
                SingleComponentConfig.zero.copy(componentGroup = Some(targetGroupName))
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

    definitions.componentsConfig(fragmentWithDocsUrl.name.value).docsUrl shouldBe Some(docsUrl)
  }

  test("should skip empty fragments in definitions") {
    val typeConfig       = ProcessingTypeConfig.read(ConfigWithScalaVersion.StreamingProcessTypeConfig)
    val model: ModelData = LocalModelData(typeConfig.modelConfig.resolved, List.empty)

    val fragment    = CanonicalProcess(MetaData("emptyFragment", FragmentSpecificData()), List.empty, List.empty)
    val definitions = prepareDefinitions(model, List(fragment))

    definitions.components.get(ComponentInfo(ComponentType.Fragment, fragment.name.value)) shouldBe empty
  }

  test("should return outputParameters in fragment's definition") {
    val typeConfig       = ProcessingTypeConfig.read(ConfigWithScalaVersion.StreamingProcessTypeConfig)
    val model: ModelData = LocalModelData(typeConfig.modelConfig.resolved, List.empty)

    val fragment    = ProcessTestData.sampleFragmentOneOut
    val definitions = prepareDefinitions(model, List(ProcessTestData.sampleFragmentOneOut))

    val fragmentDefinition =
      definitions.components.get(ComponentInfo(ComponentType.Fragment, fragment.name.value)).value
    val outputParameters = fragmentDefinition.outputParameters.value
    outputParameters shouldEqual List("output")
  }

  test("should override component's parameter config with additionally provided config") {
    val model: ModelData = LocalModelData(
      ConfigWithScalaVersion.StreamingProcessTypeConfig.resolved.getConfig("modelConfig"),
      List(ComponentDefinition("enricher", TestService)),
      additionalConfigsFromProvider = TestAdditionalUIConfigProvider.componentAdditionalConfigMap
    )

    val definitions = prepareDefinitions(model, List.empty)

    definitions.componentsConfig("enricher").params.get.map { case (name, config) =>
      name -> config.defaultValue
    } should contain(
      "paramStringEditor" -> Some("'default-from-additional-ui-config-provider'")
    )
  }

  test("should override component's component groups with additionally provided config") {
    val model: ModelData = LocalModelData(
      ConfigWithScalaVersion.StreamingProcessTypeConfig.resolved.getConfig("modelConfig"),
      List(ComponentDefinition("enricher", TestService)),
      additionalConfigsFromProvider = TestAdditionalUIConfigProvider.componentAdditionalConfigMap
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

    val definitions = prepareDefinitions(model, List.empty)

    definitions.scenarioPropertiesConfig shouldBe TestAdditionalUIConfigProvider.scenarioPropertyConfigOverride
      .mapValuesNow(createUIScenarioPropertyConfig)
  }

  private def prepareDefinitions(model: ModelData, fragmentScenarios: List[CanonicalProcess]) = {
    val staticModelDefinition =
      ToStaticComponentDefinitionTransformer.transformModel(
        model,
        MetaDataInitializer(StreamMetaData.typeName).create(_, Map.empty)
      )
    val processingType = TestProcessingTypes.Streaming

    val modelDefinitionEnricher = new ModelDefinitionEnricher(
      new BuiltInComponentsStaticDefinitionsPreparer(ComponentsUiConfigParser.parse(model.modelConfig)),
      new FragmentWithoutValidatorsDefinitionExtractor(getClass.getClassLoader),
      staticModelDefinition,
      ComponentId.default(processingType, _)
    )

    new DefinitionsService(
      modelData = model,
      scenarioPropertiesConfig = Map.empty,
      deploymentManager = new MockDeploymentManager,
      modelDefinitionEnricher = modelDefinitionEnricher,
      scenarioPropertiesConfigFinalizer =
        new ScenarioPropertiesConfigFinalizer(TestAdditionalUIConfigProvider, processingType),
      fragmentRepository = new StubFragmentRepository(Map(processingType -> fragmentScenarios))
    ).prepareUIDefinitions(
      processingType,
      forFragment = false
    )(AdminUser("admin", "admin"))
      .futureValue
  }

}
