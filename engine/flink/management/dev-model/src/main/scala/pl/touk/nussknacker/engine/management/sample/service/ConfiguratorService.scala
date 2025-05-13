package pl.touk.nussknacker.engine.management.sample.service

import pl.touk.nussknacker.engine.api.{MethodToInvoke, ParamName, Service}
import pl.touk.nussknacker.engine.api.editor.{Editor, EditorType}
import pl.touk.nussknacker.engine.api.validation.CompileTimeEvaluableValue

import javax.annotation.Nullable
import javax.validation.constraints.NotBlank
import scala.concurrent.Future

object ConfiguratorService extends Service with Serializable {

  @MethodToInvoke
  def invoke(
      @ParamName("Template ID")
      @Editor(`type` = EditorType.SPEL_TEMPLATE_EDITOR)
      @Editor(`type` = EditorType.SPEL_EDITOR)
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
