package pl.touk.nussknacker.engine.process.compiler

import com.typesafe.config.ConfigFactory
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.definition.{
  DualParameterEditor,
  Parameter,
  ParameterEditor,
  StringParameterEditor
}
import pl.touk.nussknacker.engine.api.editor.DualEditorMode
import pl.touk.nussknacker.engine.api.process.{EmptyProcessConfigCreator, Source, TestWithParametersSupport}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition.{FragmentClazzRef, FragmentParameter}
import pl.touk.nussknacker.engine.testing.LocalModelData

class StubbedFragmentInputDefinitionSourceSpec extends AnyFunSuite with Matchers {

  case class SimplifiedParam(name: String, typingResult: TypingResult, editor: Option[ParameterEditor])

  test("should generate test parameters for fragment input definition") {
    val fragmentInputDefinition = FragmentInputDefinition(
      "",
      List(
        FragmentParameter("name", FragmentClazzRef[String]),
        FragmentParameter("age", FragmentClazzRef[Long]),
      )
    )
    val stubbedSource =
      new StubbedFragmentInputDefinitionSource(LocalModelData(ConfigFactory.empty, new EmptyProcessConfigCreator()))
    val parameters: Seq[Parameter] = stubbedSource
      .createSourceDefinition(fragmentInputDefinition)
      .implementationInvoker
      .invokeMethod(Map.empty, None, Seq.empty)
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
    parameters.map(p => SimplifiedParam(p.name, p.typ, p.editor)) should contain theSameElementsAs expectedParameters
  }

}
