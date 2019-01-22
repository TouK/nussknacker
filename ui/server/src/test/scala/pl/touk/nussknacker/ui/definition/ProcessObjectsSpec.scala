package pl.touk.nussknacker.ui.definition

import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.definition.{FixedExpressionValue, FixedExpressionValues}
import pl.touk.nussknacker.engine.api.{MetaData, MethodToInvoke, ParamName, Service}
import pl.touk.nussknacker.engine.api.process.{ParameterConfig, WithCategories}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode.FlatNode
import pl.touk.nussknacker.engine.graph.evaluatedparam.Parameter
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node.SubprocessInputDefinition.{SubprocessClazzRef, SubprocessParameter}
import pl.touk.nussknacker.engine.graph.node.{Enricher, SubprocessInput, SubprocessInputDefinition}
import pl.touk.nussknacker.engine.testing.{EmptyProcessConfigCreator, LocalModelData}
import pl.touk.nussknacker.ui.api.ProcessObjects
import pl.touk.nussknacker.ui.api.helpers.TestFactory
import pl.touk.nussknacker.ui.process.subprocess.SubprocessDetails

import scala.concurrent.Future

class ProcessObjectsSpec extends FunSuite with Matchers {

  object TestService extends Service {
    @MethodToInvoke
    def method(@ParamName("param") input: String): Future[String] = ???
  }


  test("should read restrictions from config") {

    val model : ModelData = LocalModelData(ConfigFactory.load().getConfig("processConfig"), new EmptyProcessConfigCreator() {
      override def services(config: Config): Map[String, WithCategories[Service]] =
        Map("enricher" -> WithCategories(TestService))
    })

    val processObjects =
      ProcessObjects.prepareUIProcessObjects(model, TestFactory.user(), Set(), false)

    processObjects.nodesConfig("enricher").params shouldBe Some(Map("param" -> ParameterConfig(Some("'default value'"),
      Some(FixedExpressionValues(List(
        FixedExpressionValue("'default value'", "first"),
        FixedExpressionValue("'other value'", "second")
      )))
    )))
    processObjects.nodesToAdd.find(_.name == "enrichers")
      .flatMap(_.possibleNodes.find(_.label == "enricher"))
      .map(_.node.asInstanceOf[Enricher].service.parameters) shouldBe Some(List(Parameter("param", Expression("spel", "'default value'"))))

  }


  test("should read restrictions from config for subprocess") {

    val model : ModelData = LocalModelData(ConfigFactory.load().getConfig("processConfig"), new EmptyProcessConfigCreator())

    val processObjects =
      ProcessObjects.prepareUIProcessObjects(model, TestFactory.user(), Set(
        SubprocessDetails(CanonicalProcess(MetaData("enricher", null, isSubprocess = true), null, List(FlatNode(SubprocessInputDefinition("", List(
          SubprocessParameter("param", SubprocessClazzRef[String])
        ))))), "")
      ), false)

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

}
