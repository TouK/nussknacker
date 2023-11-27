package pl.touk.nussknacker.engine.management.sample.service

import javax.annotation.Nullable
import javax.validation.constraints.NotBlank
import pl.touk.nussknacker.engine.api.editor.{DualEditor, DualEditorMode, SimpleEditor, SimpleEditorType}
import pl.touk.nussknacker.engine.api.validation.CompileTimeEvaluableValue
import pl.touk.nussknacker.engine.api.{MethodToInvoke, ParamName, Service}

import scala.concurrent.Future

object ConfiguratorService extends Service with Serializable {

  @MethodToInvoke
  def invoke(
      @ParamName("Template ID")
      @DualEditor(
        simpleEditor = new SimpleEditor(`type` = SimpleEditorType.STRING_EDITOR),
        defaultMode = DualEditorMode.SIMPLE
      )
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
