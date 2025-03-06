package pl.touk.nussknacker.engine.management.sample.service

import pl.touk.nussknacker.engine.api.{MethodToInvoke, ParamName, Service}
import pl.touk.nussknacker.engine.api.editor.{SimpleEditor, SimpleEditorType, SpelEditor}
import pl.touk.nussknacker.engine.api.validation.CompileTimeEvaluableValue

import javax.annotation.Nullable
import javax.validation.constraints.NotBlank
import scala.concurrent.Future

object ConfiguratorService extends Service with Serializable {

  @MethodToInvoke
  def invoke(
      @ParamName("Template ID")
      @SimpleEditor(`type` = SimpleEditorType.SPEL_TEMPLATE_EDITOR)
      @SpelEditor
      @NotBlank
      template: String,
      @ParamName("Version")
      @CompileTimeEvaluableValue
      version: Int,
      @ParamName("JsonConfig")
      @Nullable
      jsonConfig: String
  ): Future[Unit] = Future.successful(())

}
