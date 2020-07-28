package pl.touk.nussknacker.ui.definition

import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.editor._
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, SingleNodeConfig, WithCategories}
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.util.process.EmptyProcessConfigCreator
import pl.touk.nussknacker.engine.{ModelData, ProcessingTypeConfig}
import pl.touk.nussknacker.ui.api.helpers.TestFactory
import pl.touk.nussknacker.ui.process.ProcessTypesForCategories
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

  test("should read editor from annotations") {
    val model: ModelData = LocalModelData(ConfigWithScalaVersion.streamingProcessTypeConfig, new EmptyProcessConfigCreator() {
      override def services(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[Service]] =
        Map("enricher" -> WithCategories(TestService))
    })

    val processObjects = UIProcessObjectsFactory.prepareUIProcessObjects(
      model,
      TestFactory.user("userId"),
      Set(),
      false,
      new ProcessTypesForCategories(ConfigWithScalaVersion.config)
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
          "hiddenEnricher" -> WithCategories(TestService).withNodeConfig(SingleNodeConfig.zero.copy(category = Some("hiddenCategory")))
        )
    })

    val processObjects =
      UIProcessObjectsFactory.prepareUIProcessObjects(model, TestFactory.user("userId"), Set(), false,
        new ProcessTypesForCategories(ConfigWithScalaVersion.config))

    processObjects.nodesToAdd.filter(_.name == "hiddenCategory") shouldBe empty
  }

}
