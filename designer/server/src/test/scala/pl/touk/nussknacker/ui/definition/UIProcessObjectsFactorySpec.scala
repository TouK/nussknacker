package pl.touk.nussknacker.ui.definition

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.component.{AdditionalPropertyConfig, AdditionalUIConfigProvider, ComponentGroupName, ComponentId, ParameterConfig, SingleComponentConfig, SingleComponentUIConfig}
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.context.transformation.{NodeDependencyValue, SingleInputGenericNodeTransformation}
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.editor._
import pl.touk.nussknacker.engine.api.process.{EmptyProcessConfigCreator, ProcessObjectDependencies, WithCategories}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.definition.ToStaticObjectDefinitionTransformer
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.{MetaDataInitializer, ModelData, ProcessingTypeConfig}
import pl.touk.nussknacker.ui.api.helpers.{MockDeploymentManager, ProcessTestData, TestFactory, TestProcessingTypes}
import pl.touk.nussknacker.ui.definition.UIProcessObjectsFactory.createUIAdditionalPropertyConfig
import pl.touk.nussknacker.ui.process.ConfigProcessCategoryService
import pl.touk.nussknacker.ui.process.fragment.FragmentDetails
import pl.touk.nussknacker.ui.util.ConfigWithScalaVersion
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap

import scala.concurrent.Future

class UIProcessObjectsFactorySpec extends AnyFunSuite with Matchers {

  object TestService extends Service {

    @MethodToInvoke
    def method(@ParamName("paramDualEditor")
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
               param3: String): Future[String] = ???
  }


  object SampleGenericNodeTransformation extends CustomStreamTransformer with SingleInputGenericNodeTransformation[AnyRef] {

    override def contextTransformation(context: ValidationContext,
                                       dependencies: List[NodeDependencyValue])(implicit nodeId: NodeId): this.NodeTransformationDefinition = {
      case TransformationStep(Nil, _) =>
        FinalResults(context, Nil)
    }

    override def nodeDependencies: List[NodeDependency] = List.empty

    override def implementation(params: Map[String, Any], dependencies: List[NodeDependencyValue], finalState: Option[State]): AnyRef =
      ???

  }

  private val mockDeploymentManager = new MockDeploymentManager

  private val initialData = MetaDataInitializer(StreamMetaData.typeName)

  test("should read editor from annotations") {
    val model: ModelData = LocalModelData(ConfigWithScalaVersion.StreamingProcessTypeConfig.resolved.getConfig("modelConfig"), new EmptyProcessConfigCreator() {
      override def services(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[Service]] =
        Map("enricher" -> WithCategories(TestService))
    })

    val processObjects = prepareUIProcessObjects(model, Set.empty, Map.empty)

    processObjects.processDefinition.services("enricher").parameters.map(p => (p.name, p.editor)).toMap shouldBe Map(
      "paramDualEditor" -> DualParameterEditor(
        simpleEditor = FixedValuesParameterEditor(possibleValues = List(FixedExpressionValue("expression", "label"))),
        defaultMode = DualEditorMode.SIMPLE
      ),
      "paramStringEditor" -> StringParameterEditor,
      "paramRawEditor" -> RawParameterEditor
    )
  }

  test("should hide node in hidden category") {

    val typeConfig = ProcessingTypeConfig.read(ConfigWithScalaVersion.StreamingProcessTypeConfig)
    val model: ModelData = LocalModelData(typeConfig.modelConfig.resolved, new EmptyProcessConfigCreator() {
      override def services(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[Service]] =
        Map(
          "enricher" -> WithCategories(TestService),
          "hiddenEnricher" -> WithCategories(TestService).withComponentConfig(SingleComponentConfig.zero.copy(componentGroup = Some(ComponentGroupName("hiddenCategory"))))
        )
    })

    val processObjects = prepareUIProcessObjects(model, Set.empty, Map.empty)

    processObjects.componentGroups.filter(_.name == ComponentGroupName("hiddenCategory")) shouldBe empty
  }

  test("should be able to assign generic node to some category") {
    val typeConfig = ProcessingTypeConfig.read(ConfigWithScalaVersion.StreamingProcessTypeConfig)
    val model: ModelData = LocalModelData(typeConfig.modelConfig.resolved, new EmptyProcessConfigCreator() {
      override def customStreamTransformers(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[CustomStreamTransformer]] =
        Map(
          "someGenericNode" -> WithCategories(SampleGenericNodeTransformation).withComponentConfig(SingleComponentConfig.zero.copy(componentGroup = Some(ComponentGroupName("someCategory"))))
        )
    })

    val processObjects = prepareUIProcessObjects(model, Set.empty, Map.empty)

    val componentsGroups = processObjects.componentGroups.filter(_.name == ComponentGroupName("someCategory"))
    componentsGroups should not be empty
  }

  test("should override fragment's docsUrl from config with value from 'properties'") {
    val typeConfig = ProcessingTypeConfig.read(ConfigWithScalaVersion.StreamingProcessTypeConfig)
    val model: ModelData = LocalModelData(typeConfig.modelConfig.resolved, new EmptyProcessConfigCreator())
    val fragment = ProcessTestData.sampleFragmentOneOut
    val docsUrl = "https://nussknacker.io/documentation/"
    val fragmentWithDocsUrl = fragment.copy(metaData = fragment.metaData.withTypeSpecificData(typeSpecificData = FragmentSpecificData(Some(docsUrl))))

    val processObjects = prepareUIProcessObjects(model, Set(FragmentDetails(fragmentWithDocsUrl, "Category1")), Map.empty)

    processObjects.componentsConfig("sub1").docsUrl shouldBe Some(docsUrl)
  }

  test("should skip empty fragments in definitions") {
    val typeConfig = ProcessingTypeConfig.read(ConfigWithScalaVersion.StreamingProcessTypeConfig)
    val model: ModelData = LocalModelData(typeConfig.modelConfig.resolved, new EmptyProcessConfigCreator())

    val fragment = CanonicalProcess(MetaData("emptyFragment", FragmentSpecificData()), List.empty, List.empty)
    val processObjects = prepareUIProcessObjects(model, Set(FragmentDetails(fragment, "Category1")), Map.empty)

    processObjects.componentsConfig.get(fragment.id) shouldBe empty
  }

  private val validatorsOverride = Some(List(FixedValuesValidator(possibleValues = List(FixedExpressionValue("otherExpression", "otherLabel")))))
  private val additionalPropertiesOverride = Map(
    "property1" -> AdditionalPropertyConfig.empty.copy(
      defaultValue = Some("defaultOverride")
    )
  )

  class TestAdditionalUIConfigProvider extends AdditionalUIConfigProvider {

    override def getComponentUIConfigs(processingType: String): Map[ComponentId, SingleComponentUIConfig] =
      Map(
        ComponentId("streaming-enricher-enricher") -> SingleComponentUIConfig.zero.copy(
          params = Some(Map(
            "paramDualEditor" -> ParameterConfig.empty.copy(
              validators = validatorsOverride
            )
          ))
        )
      )

    override def getAllComponentUIConfigs: Map[ComponentId, SingleComponentUIConfig] = ???

    override def getAdditionalPropertiesUIConfigs(category: String): Map[String, AdditionalPropertyConfig] = additionalPropertiesOverride

  }

  test("should override component's parameter config with additionally provided config") {
    val model: ModelData = LocalModelData(ConfigWithScalaVersion.StreamingProcessTypeConfig.resolved.getConfig("modelConfig"), new EmptyProcessConfigCreator() {
      override def services(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[Service]] =
        Map("enricher" -> WithCategories(TestService))
    },
      additionalComponentsUIConfigProvider = new TestAdditionalUIConfigProvider
    )

    val processObjects = prepareUIProcessObjects(model, Set.empty, Map.empty)

    processObjects.componentsConfig("enricher").params.map(_.map { case (key, value) => key -> value.validators }) shouldBe Some(Map(
      "paramDualEditor" -> validatorsOverride,
      "param" -> None // from designer.conf, which also goes into `componentsConfig`
    ))
  }

  test("should override additional properties with additionally provided config") {
    val typeConfig = ProcessingTypeConfig.read(ConfigWithScalaVersion.StreamingProcessTypeConfig)
    val model: ModelData = LocalModelData(typeConfig.modelConfig.resolved, new EmptyProcessConfigCreator())

    val additionalPropertyConfig = Map(
      "property1" -> AdditionalPropertyConfig.empty.copy(
        defaultValue = Some("defaultOverride") // TODO should it be possible to override Some("default") to None? (currently it's not)
      )
    )

    val processObjects = prepareUIProcessObjects(model, Set.empty, additionalPropertyConfig)

    processObjects.additionalPropertiesConfig shouldBe additionalPropertiesOverride.mapValuesNow(createUIAdditionalPropertyConfig)
  }


  private def prepareUIProcessObjects(model: ModelData,
                                      fragmentDetails: Set[FragmentDetails],
                                      additionalPropertiesConfig: Map[String, AdditionalPropertyConfig]
                                     ) = {
    val staticObjectsDefinition = ToStaticObjectDefinitionTransformer.transformModel(model, initialData.create(_, Map.empty))
    UIProcessObjectsFactory.prepareUIProcessObjects(
      model,
      staticObjectsDefinition,
      mockDeploymentManager,
      TestFactory.user("userId"),
      fragmentDetails,
      isFragment = false,
      new ConfigProcessCategoryService(ConfigWithScalaVersion.TestsConfig),
      additionalPropertiesConfig,
      TestProcessingTypes.Streaming
    )
  }


}
