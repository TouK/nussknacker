package pl.touk.nussknacker.engine.demo.service

import java.time.LocalDate

import javax.annotation.Nullable
import pl.touk.nussknacker.engine.api.editor.{DualEditor, DualEditorMode, SimpleEditor, SimpleEditorType}
import pl.touk.nussknacker.engine.api.{MethodToInvoke, ParamName, Service}

import scala.concurrent.Future

class ValidatorTypesService extends Service with Serializable {
  @MethodToInvoke
  def invoke(@ParamName("Email")
             @DualEditor(
               simpleEditor = new SimpleEditor(`type` = SimpleEditorType.STRING_EDITOR),
               defaultMode = DualEditorMode.SIMPLE
             ) email: String,
             @ParamName("Age") @Nullable age: Int,
             @ParamName("Birthday") @Nullable birthday: LocalDate,
             @ParamName("Amount") @Nullable amount: BigDecimal,
             @ParamName("Description") @Nullable description: String
            ): Future[Unit]
  = Future.successful(Unit)
}
