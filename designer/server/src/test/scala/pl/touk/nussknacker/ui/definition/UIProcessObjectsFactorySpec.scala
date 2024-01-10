package pl.touk.nussknacker.ui.definition

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
import pl.touk.nussknacker.ui.api.helpers.{MockDeploymentManager, ProcessTestData, TestProcessingTypes}
import pl.touk.nussknacker.ui.definition.UIProcessObjectsFactory.createUIScenarioPropertyConfig
import pl.touk.nussknacker.ui.util.ConfigWithScalaVersion

import scala.concurrent.Future

class UIProcessObjectsFactorySpec extends AnyFunSuite with Matchers {

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

    val processObjects = prepareUIProcessObjects(model, List.empty)

    processObjects
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

  test("should hide node in hidden category") {
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
                SingleComponentConfig.zero.copy(componentGroup = Some(ComponentGroupName("hiddenCategory")))
              )
          )
      }
    )

    val processObjects = prepareUIProcessObjects(model, List.empty)

    processObjects.componentGroups.filter(_.name == ComponentGroupName("hiddenCategory")) shouldBe empty
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

    val componentsGroups = prepareUIProcessObjects(model, List.empty).componentGroups

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

    val processObjects = prepareUIProcessObjects(model, List(fragmentWithDocsUrl))

    processObjects.componentsConfig(fragmentWithDocsUrl.name.value).docsUrl shouldBe Some(docsUrl)
  }

  test("should skip empty fragments in definitions") {
    val typeConfig       = ProcessingTypeConfig.read(ConfigWithScalaVersion.StreamingProcessTypeConfig)
    val model: ModelData = LocalModelData(typeConfig.modelConfig.resolved, List.empty)

    val fragment       = CanonicalProcess(MetaData("emptyFragment", FragmentSpecificData()), List.empty, List.empty)
    val processObjects = prepareUIProcessObjects(model, List(fragment))

    processObjects.components.get(ComponentInfo(ComponentType.Fragment, fragment.name.value)) shouldBe empty
  }

  test("should override component's parameter config with additionally provided config") {
    val model: ModelData = LocalModelData(
      ConfigWithScalaVersion.StreamingProcessTypeConfig.resolved.getConfig("modelConfig"),
      List(ComponentDefinition("enricher", TestService))
    )

    val processObjects = prepareUIProcessObjects(model, List.empty)

    processObjects.componentsConfig("enricher").params.get.map { case (name, config) =>
      name -> config.defaultValue
    } should contain(
      "paramStringEditor" -> Some("'default-from-additional-ui-config-provider'")
    )
  }

  test("should override component's component groups with additionally provided config") {
    val model: ModelData = LocalModelData(
      ConfigWithScalaVersion.StreamingProcessTypeConfig.resolved.getConfig("modelConfig"),
      List(ComponentDefinition("enricher", TestService))
    )

    val processObjects = prepareUIProcessObjects(model, List.empty)

    processObjects.componentGroups.map(c => (c.name, c.components.head.label)) should contain(
      TestAdditionalUIConfigProvider.componentGroupName,
      "enricher"
    )
  }

  test("should override scenario properties with additionally provided config") {
    val typeConfig       = ProcessingTypeConfig.read(ConfigWithScalaVersion.StreamingProcessTypeConfig)
    val model: ModelData = LocalModelData(typeConfig.modelConfig.resolved, List.empty)

    val processObjects = prepareUIProcessObjects(model, List.empty)

    processObjects.scenarioPropertiesConfig shouldBe TestAdditionalUIConfigProvider.scenarioPropertyConfigOverride
      .mapValuesNow(createUIScenarioPropertyConfig)
  }

  private def prepareUIProcessObjects(model: ModelData, fragmentScenarios: List[CanonicalProcess]) = {
    val staticModelDefinition =
      ToStaticComponentDefinitionTransformer.transformModel(
        model,
        MetaDataInitializer(StreamMetaData.typeName).create(_, Map.empty)
      )
    val additionalUIConfigFinalizer = new AdditionalUIConfigFinalizer(TestAdditionalUIConfigProvider)
    val modelDefinitionEnricher = new ModelDefinitionEnricher(
      new BuiltInComponentsStaticDefinitionsPreparer(ComponentsUiConfigParser.parse(model.modelConfig)),
      new FragmentWithoutValidatorsDefinitionExtractor(getClass.getClassLoader),
      additionalUIConfigFinalizer,
      staticModelDefinition
    )
    // FIXME: remove code duplication with DefinitionResources - extract DefinitionService
    val enrichedModelDefinition =
      modelDefinitionEnricher
        .modelDefinitionWithBuiltInComponentsAndFragments(
          forFragment = false,
          fragmentScenarios = fragmentScenarios,
          TestProcessingTypes.Streaming
        )
    val finalizedScenarioPropertiesConfig = additionalUIConfigFinalizer
      .finalizeScenarioProperties(Map.empty, TestProcessingTypes.Streaming)
    UIProcessObjectsFactory.prepareUIProcessObjects(
      enrichedModelDefinition,
      model,
      new MockDeploymentManager,
      forFragment = false,
      finalizedScenarioPropertiesConfig
    )
  }

}
