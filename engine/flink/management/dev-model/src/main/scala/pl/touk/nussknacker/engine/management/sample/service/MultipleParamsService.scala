package pl.touk.nussknacker.engine.management.sample.service

import pl.touk.nussknacker.engine.api.{MethodToInvoke, ParamName, Service}
import pl.touk.nussknacker.engine.api.editor.{ParameterEditor, ParameterEditorType, SpelEditor}

import scala.concurrent.Future

case object MultipleParamsService extends Service {

  @MethodToInvoke
  def invoke(
      @ParamName("foo") foo: String,
      @ParamName("bar")
      @ParameterEditor(`type` = ParameterEditorType.SPEL_TEMPLATE_EDITOR)
      @ParameterEditor(`type` = ParameterEditorType.SPEL_EDITOR)
      bar: String,
      @ParamName("baz") baz: String,
      @ParamName("quax") quax: String
  ) = Future.successful(())

}
