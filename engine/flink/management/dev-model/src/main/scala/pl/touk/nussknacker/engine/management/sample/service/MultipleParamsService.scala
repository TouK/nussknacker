package pl.touk.nussknacker.engine.management.sample.service

import pl.touk.nussknacker.engine.api.{MethodToInvoke, ParamName, Service}
import pl.touk.nussknacker.engine.api.editor.{Editor, EditorType}

import scala.concurrent.Future

case object MultipleParamsService extends Service {

  @MethodToInvoke
  def invoke(
      @ParamName("foo") foo: String,
      @ParamName("bar")
      @Editor(`type` = EditorType.SPEL_TEMPLATE_EDITOR)
      @Editor(`type` = EditorType.SPEL_EDITOR)
      bar: String,
      @ParamName("baz") baz: String,
      @ParamName("quax") quax: String
  ) = Future.successful(())

}
