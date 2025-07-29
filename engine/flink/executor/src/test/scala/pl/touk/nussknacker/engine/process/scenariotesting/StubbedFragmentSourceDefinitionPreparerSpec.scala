package pl.touk.nussknacker.engine.process.scenariotesting

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.ModelConfig.GlobalParametersConfig
import pl.touk.nussknacker.engine.ScenarioCompilationDependencies
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process.{ComponentUseContext, TestWithParametersSupport}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.definition.clazz.{ClassDefinition, ClassDefinitionSet}
import pl.touk.nussknacker.engine.definition.component.NodeCompilationDependencies
import pl.touk.nussknacker.engine.definition.fragment.FragmentParametersDefinitionExtractor
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition.{FragmentClazzRef, FragmentParameter}

class StubbedFragmentSourceDefinitionPreparerSpec extends AnyFunSuite with Matchers {

  case class SimplifiedParam(name: String, typingResult: TypingResult, editors: List[ParameterEditor])

  test("should generate test parameters for fragment input definition") {
    val fragmentInputDefinition = FragmentInputDefinition(
      "",
      List(
        FragmentParameter(ParameterName("name"), FragmentClazzRef[String]),
        FragmentParameter(ParameterName("age"), FragmentClazzRef[Long]),
      )
    )
    val stubbedSourcePreparer = new StubbedFragmentSourceDefinitionPreparer(
      new FragmentParametersDefinitionExtractor(
        getClass.getClassLoader,
        ClassDefinitionSet(Set.empty[ClassDefinition]),
        GlobalParametersConfig.default
      )
    )
    val parameters: Seq[Parameter] = stubbedSourcePreparer
      .createSourceDefinition("foo", fragmentInputDefinition)
      .implementationInvoker
      .invokeMethod(
        Params.empty,
        new NodeCompilationDependencies(
          scenarioCompilationDependencies = new ScenarioCompilationDependencies(
            JobData(MetaData("dumb", FragmentSpecificData()), ProcessVersion.empty),
            EngineScenarioCompilationDependencies.empty
          ),
          nodeData = fragmentInputDefinition,
          componentUseContext = ComponentUseContext.ScenarioTesting,
        ),
        invocationContext = None
      )
      .asInstanceOf[TestWithParametersSupport[Map[String, Any]]]
      .testParametersDefinition
    val expectedParameters = List(
      SimplifiedParam(
        "name",
        Typed.apply[String],
        List(SpelTemplateParameterEditor, SpelParameterEditor),
      ),
      SimplifiedParam("age", Typed.apply[Long], List(SpelParameterEditor)),
    )
    parameters.map(p =>
      SimplifiedParam(p.name.value, p.typ, p.editors)
    ) should contain theSameElementsAs expectedParameters
  }

}
