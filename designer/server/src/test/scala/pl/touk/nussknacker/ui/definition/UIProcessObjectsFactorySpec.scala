package pl.touk.nussknacker.ui.definition

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.component.{ComponentDefinition, ComponentGroupName, SingleComponentConfig}
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.context.transformation.{NodeDependencyValue, SingleInputGenericNodeTransformation}
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.editor._
import pl.touk.nussknacker.engine.api.process.{EmptyProcessConfigCreator, ProcessObjectDependencies, WithCategories}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.definition.component.ToStaticComponentDefinitionTransformer
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.engine.{MetaDataInitializer, ModelData, ProcessingTypeConfig}
import pl.touk.nussknacker.ui.api.helpers.{MockDeploymentManager, ProcessTestData, TestFactory, TestProcessingTypes}
import pl.touk.nussknacker.ui.definition.UIProcessObjectsFactory.createUIScenarioPropertyConfig
import pl.touk.nussknacker.ui.process.fragment.FragmentDetails
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

  private val mockDeploymentManager = new MockDeploymentManager

  private val initialData = MetaDataInitializer(StreamMetaData.typeName)

  test("should read editor from annotations") {
    val model: ModelData = LocalModelData(
      ConfigWithScalaVersion.StreamingProcessTypeConfig.resolved.getConfig("modelConfig"),
      List(ComponentDefinition("enricher", TestService))
    )

    val processObjects = prepareUIProcessObjects(model, Set.empty)

    processObjects.processDefinition.services("enricher").parameters.map(p => (p.name, p.editor)).toMap shouldBe Map(
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
    val model: ModelData = LocalModelData(
      typeConfig.modelConfig.resolved,
      List.empty,
      // TODO: use ComponentDefinition instead. Before this, add component group parameter into ComponentDefinition
      new EmptyProcessConfigCreator {
        override def services(
            processObjectDependencies: ProcessObjectDependencies
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

    val processObjects = prepareUIProcessObjects(model, Set.empty)

    processObjects.componentGroups.filter(_.name == ComponentGroupName("hiddenCategory")) shouldBe empty
  }

  test("should be able to assign generic node to some category") {
    val typeConfig = ProcessingTypeConfig.read(ConfigWithScalaVersion.StreamingProcessTypeConfig)
    val model: ModelData = LocalModelData(
      typeConfig.modelConfig.resolved,
      List.empty,
      // TODO: use ComponentDefinition instead. Before this, add component group parameter into ComponentDefinition
      new EmptyProcessConfigCreator {
        override def customStreamTransformers(
            processObjectDependencies: ProcessObjectDependencies
        ): Map[String, WithCategories[CustomStreamTransformer]] =
          Map(
            "someGenericNode" -> WithCategories
              .anyCategory(SampleGenericNodeTransformation)
              .withComponentConfig(
                SingleComponentConfig.zero.copy(componentGroup = Some(ComponentGroupName("someCategory")))
              )
          )
      }
    )

    val processObjects = prepareUIProcessObjects(model, Set.empty)

    val componentsGroups = processObjects.componentGroups.filter(_.name == ComponentGroupName("someCategory"))
    componentsGroups should not be empty
  }

  test("should override fragment's docsUrl from config with value from 'properties'") {
    val typeConfig       = ProcessingTypeConfig.read(ConfigWithScalaVersion.StreamingProcessTypeConfig)
    val model: ModelData = LocalModelData(typeConfig.modelConfig.resolved, List.empty)
    val fragment         = ProcessTestData.sampleFragmentOneOut
    val docsUrl          = "https://nussknacker.io/documentation/"
    val fragmentWithDocsUrl = fragment.copy(metaData =
      fragment.metaData.withTypeSpecificData(typeSpecificData = FragmentSpecificData(Some(docsUrl)))
    )

    val processObjects = prepareUIProcessObjects(model, Set(FragmentDetails(fragmentWithDocsUrl, "Category1")))

    processObjects.componentsConfig("sub1").docsUrl shouldBe Some(docsUrl)
  }

  test("should skip empty fragments in definitions") {
    val typeConfig       = ProcessingTypeConfig.read(ConfigWithScalaVersion.StreamingProcessTypeConfig)
    val model: ModelData = LocalModelData(typeConfig.modelConfig.resolved, List.empty)

    val fragment       = CanonicalProcess(MetaData("emptyFragment", FragmentSpecificData()), List.empty, List.empty)
    val processObjects = prepareUIProcessObjects(model, Set(FragmentDetails(fragment, "Category1")))

    processObjects.processDefinition.fragmentInputs.get(fragment.id) shouldBe empty
  }

  test("should override component's parameter config with additionally provided config") {
    val model: ModelData = LocalModelData(
      ConfigWithScalaVersion.StreamingProcessTypeConfig.resolved.getConfig("modelConfig"),
      List(ComponentDefinition("enricher", TestService))
    )

    val processObjects = prepareUIProcessObjects(model, Set.empty)

    processObjects.componentsConfig("enricher").params.get.map { case (name, config) =>
      name -> config.defaultValue
    } should contain(
      "paramStringEditor" -> Some("'default-from-additional-ui-config-provider'")
    )
  }

  test("should override scenario properties with additionally provided config") {
    val typeConfig       = ProcessingTypeConfig.read(ConfigWithScalaVersion.StreamingProcessTypeConfig)
    val model: ModelData = LocalModelData(typeConfig.modelConfig.resolved, List.empty)

    val processObjects = prepareUIProcessObjects(model, Set.empty)

    processObjects.scenarioPropertiesConfig shouldBe TestAdditionalUIConfigProvider.scenarioPropertyConfigOverride
      .mapValuesNow(createUIScenarioPropertyConfig)
  }

  private def prepareUIProcessObjects(model: ModelData, fragmentDetails: Set[FragmentDetails]) = {
    val staticModelDefinition =
      ToStaticComponentDefinitionTransformer.transformModel(model, initialData.create(_, Map.empty))
    UIProcessObjectsFactory.prepareUIProcessObjects(
      model,
      staticModelDefinition,
      mockDeploymentManager,
      TestFactory.user("userId"),
      fragmentDetails,
      isFragment = false,
      TestFactory.createCategoryService(ConfigWithScalaVersion.TestsConfig),
      Map.empty,
      TestProcessingTypes.Streaming,
      TestAdditionalUIConfigProvider
    )
  }

}
