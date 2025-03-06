package pl.touk.nussknacker.engine.management.sample.service

import pl.touk.nussknacker.engine.api.{MethodToInvoke, ParamName, Service}
import pl.touk.nussknacker.engine.api.editor.{SimpleEditor, SimpleEditorType, SpelEditor}

import scala.concurrent.Future

case object MultipleParamsService extends Service {

  @MethodToInvoke
  def invoke(
      @ParamName("foo") foo: String,
      @ParamName("bar")
      @SimpleEditor(`type` = SimpleEditorType.SPEL_TEMPLATE_EDITOR)
      @SpelEditor
      bar: String,
      @ParamName("baz") baz: String,
      @ParamName("quax") quax: String
  ) = Future.successful(())

}
