package pl.touk.nussknacker.ui.definition

import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.component.ComponentGroupName
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.context.transformation.{NodeDependencyValue, SingleInputGenericNodeTransformation}
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.editor._
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, SingleComponentConfig, WithCategories}
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.util.process.EmptyProcessConfigCreator
import pl.touk.nussknacker.engine.{ModelData, ProcessingTypeConfig}
import pl.touk.nussknacker.ui.api.helpers.TestFactory
import pl.touk.nussknacker.ui.api.helpers.TestFactory.MockDeploymentManager
import pl.touk.nussknacker.ui.process.ConfigProcessCategoryService
import pl.touk.nussknacker.ui.util.ConfigWithScalaVersion

import scala.concurrent.Future

class UIProcessObjectsFactorySpec extends FunSuite with Matchers {

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

  test("should read editor from annotations") {
    val model: ModelData = LocalModelData(ConfigWithScalaVersion.streamingProcessTypeConfig, new EmptyProcessConfigCreator() {
      override def services(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[Service]] =
        Map("enricher" -> WithCategories(TestService))
    })

    val processObjects = UIProcessObjectsFactory.prepareUIProcessObjects(
      model,
      mockDeploymentManager,
      TestFactory.user("userId"),
      Set(),
      false,
      new ConfigProcessCategoryService(ConfigWithScalaVersion.config)
    )

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

    val typeConfig = ProcessingTypeConfig.read(ConfigWithScalaVersion.streamingProcessTypeConfig)
    val model : ModelData = LocalModelData(typeConfig.modelConfig, new EmptyProcessConfigCreator() {
      override def services(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[Service]] =
        Map(
          "enricher" -> WithCategories(TestService),
          "hiddenEnricher" -> WithCategories(TestService).withComponentConfig(SingleComponentConfig.zero.copy(componentGroup = Some(ComponentGroupName("hiddenCategory"))))
        )
    })

    val processObjects =
      UIProcessObjectsFactory.prepareUIProcessObjects(model, mockDeploymentManager, TestFactory.user("userId"), Set(), false,
        new ConfigProcessCategoryService(ConfigWithScalaVersion.config))

    processObjects.nodesToAdd.filter(_.name == ComponentGroupName("hiddenCategory")) shouldBe empty
  }

  test("should be able to assign generic node to some category") {
    val typeConfig = ProcessingTypeConfig.read(ConfigWithScalaVersion.streamingProcessTypeConfig)
    val model : ModelData = LocalModelData(typeConfig.modelConfig, new EmptyProcessConfigCreator() {
      override def customStreamTransformers(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[CustomStreamTransformer]] =
        Map(
          "someGenericNode" -> WithCategories(SampleGenericNodeTransformation).withComponentConfig(SingleComponentConfig.zero.copy(componentGroup = Some(ComponentGroupName("someCategory"))))
        )
    })

    val processObjects =
      UIProcessObjectsFactory.prepareUIProcessObjects(model, mockDeploymentManager, TestFactory.user("userId"), Set(), false,
        new ConfigProcessCategoryService(ConfigWithScalaVersion.config))

    val nodeGroups = processObjects.nodesToAdd.filter(_.name == ComponentGroupName("someCategory"))
    nodeGroups should not be empty
  }


}
