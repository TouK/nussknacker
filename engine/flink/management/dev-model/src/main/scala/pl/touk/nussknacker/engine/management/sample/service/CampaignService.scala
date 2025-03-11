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
      @SimpleEditor(`type` = SimpleEditorType.SPEL_TEMPLATE_EDITOR)
      @NotBlank
      campaignName: String,
      @ParamName("Registered")
      @SimpleEditor(
        `type` = SimpleEditorType.BOOL_EDITOR
      )
      registered: Boolean,
      @ParamName("BusinessConfig")
      @SimpleEditor(`type` = SimpleEditorType.SPEL_TEMPLATE_EDITOR)
      @SpelEditor
      @Nullable
      businessConfig: String,
      @ParamName("Product Counts")
      @SpelEditor
      @CompileTimeEvaluableValue
      productCounts: Int,
      @ParamName("CampaignType")
      @SimpleEditor(
        `type` = SimpleEditorType.FIXED_VALUES_EDITOR,
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
