package pl.touk.nussknacker.engine.management.sample.service

import pl.touk.nussknacker.engine.api.{MethodToInvoke, ParamName, Service}
import pl.touk.nussknacker.engine.api.editor._
import pl.touk.nussknacker.engine.api.validation.CompileTimeEvaluableValue

import javax.annotation.Nullable
import javax.validation.constraints.NotBlank
import scala.concurrent.Future

object CampaignService extends Service with Serializable {

  @MethodToInvoke
  def invoke(
      @ParamName("CampaignName")
      @Editor(`type` = EditorType.SPEL_TEMPLATE_EDITOR)
      @NotBlank
      campaignName: String,
      @ParamName("Registered")
      @Editor(
        `type` = EditorType.BOOL_EDITOR
      )
      registered: Boolean,
      @ParamName("BusinessConfig")
      @Editor(`type` = EditorType.SPEL_TEMPLATE_EDITOR, isMainEditor = true)
      @Editor(`type` = EditorType.SPEL_EDITOR)
      @Nullable
      businessConfig: String,
      @ParamName("Product Counts")
      @Editor(`type` = EditorType.SPEL_EDITOR)
      @CompileTimeEvaluableValue
      productCounts: Int,
      @ParamName("CampaignType")
      @Editor(
        `type` = EditorType.FIXED_VALUES_EDITOR,
        possibleValues = Array(
          new LabeledExpression(expression = "'mail'", label = "Mail campaign"),
          new LabeledExpression(expression = "'sms'", label = "Sms campaign"),
          new LabeledExpression(expression = "'popup'", label = "Popup campaign"),
          new LabeledExpression(expression = "'push'", label = "Push campaign")
        )
      )
      campaignType: String
  ): Future[Unit] = Future.successful(())

}
