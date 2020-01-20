package pl.touk.nussknacker.ui.definition

import com.typesafe.config.Config
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.editor._
import pl.touk.nussknacker.engine.api.process.{ParameterConfig, SingleNodeConfig, WithCategories}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode.FlatNode
import pl.touk.nussknacker.engine.graph.evaluatedparam.Parameter
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node.SubprocessInputDefinition.{SubprocessClazzRef, SubprocessParameter}
import pl.touk.nussknacker.engine.graph.node.{Enricher, SubprocessInput, SubprocessInputDefinition}
import pl.touk.nussknacker.engine.testing.{EmptyProcessConfigCreator, LocalModelData}
import pl.touk.nussknacker.ui.api.helpers.TestFactory
import pl.touk.nussknacker.ui.process.ProcessTypesForCategories
import pl.touk.nussknacker.ui.process.subprocess.SubprocessDetails
import pl.touk.nussknacker.ui.util.ConfigWithScalaVersion

import scala.concurrent.Future

class UIProcessObjectsSpec extends FunSuite with Matchers {

  object TestService extends Service {

    @MethodToInvoke
    def method(@ParamName("param")
               @DualEditor(
                 simpleEditor = new SimpleEditor(
                   `type` = SimpleEditorType.FIXED_VALUES_EDITOR,
                   possibleValues = Array(new LabeledExpression(expression = "expression", label = "label"))
                 ),
                 defaultMode = DualEditorMode.SIMPLE
               )
               input: String,

               @PossibleValues(value = Array("a", "b", "c"))
               @ParamName("param2")
               @SimpleEditor(`type` = SimpleEditorType.STRING_EDITOR)
               param2: String,

               @ParamName("param3")
               @RawEditor
               param3: String,

               @ParamName("param4") param4: String): Future[String] = ???
  }


  test("should read restrictions from config") {

    val model : ModelData = LocalModelData(ConfigWithScalaVersion.config.getConfig("processConfig"), new EmptyProcessConfigCreator() {
      override def services(config: Config): Map[String, WithCategories[Service]] =
        Map("enricher" -> WithCategories(TestService))
    })

    val processObjects =
      UIProcessObjects.prepareUIProcessObjects(model, TestFactory.user("userId"), Set(), false,
        new ProcessTypesForCategories(ConfigWithScalaVersion.config))

    processObjects.nodesConfig("enricher").params shouldBe Some(Map("param" -> ParameterConfig(Some("'default value'"),
      Some(FixedExpressionValues(List(
        FixedExpressionValue("'default value'", "first"),
        FixedExpressionValue("'other value'", "second")
      )))
    )))

    processObjects.processDefinition.services("enricher").parameters.map(p => (p.name, p.restriction)).toMap shouldBe Map(
      "param" -> Some(FixedExpressionValues(List(
        FixedExpressionValue("'default value'", "first"),
        FixedExpressionValue("'other value'", "second")
      ))),
      "param2" -> Some(FixedExpressionValues(List(
        FixedExpressionValue("'a'", "a"),
        FixedExpressionValue("'b'", "b"),
        FixedExpressionValue("'c'", "c")
      ))),
      "param3" -> None,
      "param4" -> None
    )


    processObjects.nodesToAdd.find(_.name == "enrichers")
      .flatMap(_.possibleNodes.find(_.label == "enricher"))
      .map(_.node.asInstanceOf[Enricher].service.parameters) shouldBe Some(List(Parameter("param", Expression("spel", "'default value'")),
      Parameter("param2", Expression("spel", "'a'")),
      Parameter("param3", Expression("spel", "''")),
      Parameter("param4", Expression("spel", "''"))
    ))

  }

  test("should read editor from config") {
    val model : ModelData = LocalModelData(ConfigWithScalaVersion.config.getConfig("processConfig"), new EmptyProcessConfigCreator() {
      override def services(config: Config): Map[String, WithCategories[Service]] =
        Map("enricher" -> WithCategories(TestService))
    })

    val processObjects =
      UIProcessObjects.prepareUIProcessObjects(model, TestFactory.user("userId"), Set(), false,
        new ProcessTypesForCategories(ConfigWithScalaVersion.config))

    processObjects.processDefinition.services("enricher").parameters.map(p => (p.name, p.editor)).toMap shouldBe Map(
      "param" -> Some(DualParameterEditor(
        simpleEditor = SimpleParameterEditor(
          simpleEditorType = SimpleEditorType.FIXED_VALUES_EDITOR,
          possibleValues = List(FixedExpressionValue("expression", "label"))),
        defaultMode = DualEditorMode.SIMPLE
      )),
      "param2" -> Some(SimpleParameterEditor(simpleEditorType = SimpleEditorType.STRING_EDITOR, possibleValues = List.empty)),
      "param3" -> Some(RawParameterEditor),
      "param4" -> None
    )
  }

  test("should read restrictions from config for subprocess") {

    val model : ModelData = LocalModelData(ConfigWithScalaVersion.config.getConfig("processConfig"), new EmptyProcessConfigCreator())

    val processObjects =
      UIProcessObjects.prepareUIProcessObjects(model, TestFactory.user("userId"), Set(
        SubprocessDetails(CanonicalProcess(MetaData("enricher", null, isSubprocess = true), null, List(FlatNode(SubprocessInputDefinition("", List(
          SubprocessParameter("param", SubprocessClazzRef[String])
        )))), None), "")
      ), false, new ProcessTypesForCategories(ConfigWithScalaVersion.config))

    processObjects.processDefinition.subprocessInputs("enricher").parameters.map(p => (p.name, p.restriction)).toMap shouldBe Map(
      "param" -> Some(FixedExpressionValues(List(
        FixedExpressionValue("'default value'", "first"),
        FixedExpressionValue("'other value'", "second")
      )))
    )


    processObjects.nodesConfig("enricher").params shouldBe Some(Map("param" -> ParameterConfig(Some("'default value'"),
      Some(FixedExpressionValues(List(
        FixedExpressionValue("'default value'", "first"),
        FixedExpressionValue("'other value'", "second")
      )))
    )))

    processObjects.nodesToAdd.find(_.name == "subprocesses")
      .flatMap(_.possibleNodes.find(_.label == "enricher"))
      .map(_.node.asInstanceOf[SubprocessInput].ref.parameters) shouldBe Some(List(Parameter("param", Expression("spel", "'default value'"))))

  }

  test("should hide node in hidden category") {

    val model : ModelData = LocalModelData(ConfigWithScalaVersion.config.getConfig("processConfig"), new EmptyProcessConfigCreator() {
      override def services(config: Config): Map[String, WithCategories[Service]] =
        Map(
          "enricher" -> WithCategories(TestService),
          "hiddenEnricher" -> WithCategories(TestService).withNodeConfig(SingleNodeConfig.zero.copy(category = Some("hiddenCategory")))
        )
    })

    val processObjects =
      UIProcessObjects.prepareUIProcessObjects(model, TestFactory.user("userId"), Set(), false,
        new ProcessTypesForCategories(ConfigWithScalaVersion.config))

    processObjects.nodesToAdd.filter(_.name == "hiddenCategory") shouldBe empty
  }

}
