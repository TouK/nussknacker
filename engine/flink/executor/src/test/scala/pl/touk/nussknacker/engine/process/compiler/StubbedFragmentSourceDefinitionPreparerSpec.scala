package pl.touk.nussknacker.engine.process.compiler

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.Params
import pl.touk.nussknacker.engine.api.definition.{
  DualParameterEditor,
  Parameter,
  ParameterEditor,
  StringParameterEditor
}
import pl.touk.nussknacker.engine.api.editor.DualEditorMode
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process.TestWithParametersSupport
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.definition.clazz.{ClassDefinition, ClassDefinitionSet}
import pl.touk.nussknacker.engine.definition.fragment.FragmentParametersDefinitionExtractor
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition.{FragmentClazzRef, FragmentParameter}

class StubbedFragmentSourceDefinitionPreparerSpec extends AnyFunSuite with Matchers {

  case class SimplifiedParam(name: String, typingResult: TypingResult, editor: Option[ParameterEditor])

  test("should generate test parameters for fragment input definition") {
    val fragmentInputDefinition = FragmentInputDefinition(
      "",
      List(
        FragmentParameter(ParameterName("name"), FragmentClazzRef[String]),
        FragmentParameter(ParameterName("age"), FragmentClazzRef[Long]),
      )
    )
    val stubbedSourcePreparer = new StubbedFragmentSourceDefinitionPreparer(
      new FragmentParametersDefinitionExtractor(getClass.getClassLoader, ClassDefinitionSet(Set.empty[ClassDefinition]))
    )
    val parameters: Seq[Parameter] = stubbedSourcePreparer
      .createSourceDefinition("foo", fragmentInputDefinition)
      .implementationInvoker
      .invokeMethod(Params.empty, None, Seq.empty)
      .asInstanceOf[TestWithParametersSupport[Map[String, Any]]]
      .testParametersDefinition
    val expectedParameters = List(
      SimplifiedParam(
        "name",
        Typed.apply[String],
        Option(DualParameterEditor(StringParameterEditor, DualEditorMode.RAW))
      ),
      SimplifiedParam("age", Typed.apply[Long], None),
    )
    parameters.map(p =>
      SimplifiedParam(p.name.value, p.typ, p.editor)
    ) should contain theSameElementsAs expectedParameters
  }

}
