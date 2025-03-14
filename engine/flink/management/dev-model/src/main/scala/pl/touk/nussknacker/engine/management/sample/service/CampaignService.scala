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
      @ParameterEditor(`type` = ParameterEditorType.SPEL_TEMPLATE_EDITOR)
      @NotBlank
      campaignName: String,
      @ParamName("Registered")
      @ParameterEditor(
        `type` = ParameterEditorType.BOOL_EDITOR
      )
      registered: Boolean,
      @ParamName("BusinessConfig")
      @ParameterEditor(`type` = ParameterEditorType.SPEL_TEMPLATE_EDITOR)
      @ParameterEditor(`type` = ParameterEditorType.SPEL_EDITOR)
      @Nullable
      businessConfig: String,
      @ParamName("Product Counts")
      @ParameterEditor(`type` = ParameterEditorType.SPEL_EDITOR)
      @CompileTimeEvaluableValue
      productCounts: Int,
      @ParamName("CampaignType")
      @ParameterEditor(
        `type` = ParameterEditorType.FIXED_VALUES_EDITOR,
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
