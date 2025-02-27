package pl.touk.nussknacker.engine.management.sample.service

import pl.touk.nussknacker.engine.api.{MethodToInvoke, ParamName, Service}
import pl.touk.nussknacker.engine.api.editor.{DualEditor, DualEditorMode, SimpleEditor, SimpleEditorType}

import scala.concurrent.Future

case object MultipleParamsService extends Service {

  @MethodToInvoke
  def invoke(
      @ParamName("foo") foo: String,
      @ParamName("bar")
      @DualEditor(
        simpleEditor = new SimpleEditor(`type` = SimpleEditorType.STRING_EDITOR),
        defaultMode = DualEditorMode.SIMPLE
      )
      bar: String,
      @ParamName("baz") baz: String,
      @ParamName("quax") quax: String
  ) = Future.successful(())

}
