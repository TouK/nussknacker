package pl.touk.nussknacker.engine.management.sample.service

import javax.validation.constraints.NotBlank
import pl.touk.nussknacker.engine.api.editor._
import pl.touk.nussknacker.engine.api.validation.Literal
import pl.touk.nussknacker.engine.api.{MethodToInvoke, ParamName, Service}

import scala.concurrent.Future

// In services all parameters are lazy evaluated
class SimpleTypesService extends Service with Serializable {
  @MethodToInvoke
  def invoke(@ParamName("booleanParam")
             @SimpleEditor(
               `type` = SimpleEditorType.BOOL_EDITOR
             ) booleanParam: Boolean,

             @ParamName("DualParam")
             @DualEditor(
               simpleEditor = new SimpleEditor(`type` = SimpleEditorType.STRING_EDITOR),
               defaultMode = DualEditorMode.SIMPLE
             )
             @NotBlank
             dualParam: String,

             @ParamName("SimpleParam")
             @SimpleEditor(`type` = SimpleEditorType.STRING_EDITOR)
             simpleParam: String,

             @ParamName("RawParam")
             @DualEditor(
               simpleEditor = new SimpleEditor(`type` = SimpleEditorType.STRING_EDITOR),
               defaultMode = DualEditorMode.RAW
             )
             rawParam: String,

             @ParamName("intParam")
             @SimpleEditor(`type` = SimpleEditorType.STRING_EDITOR)
             @Literal
             intParam: Int,

             @ParamName("rawIntParam")
             @RawEditor
             @Literal
             rawIntParam: Int,

             @ParamName("fixedValuesStringParam")
             @SimpleEditor(
               `type` = SimpleEditorType.FIXED_VALUES_EDITOR,
               possibleValues = Array(
                 new LabeledExpression(expression = "'Max'", label = "Max"),
                 new LabeledExpression(expression = "'Min'", label = "Min")
               )
             ) fixedValuesStringParam: String,

             @ParamName("bigDecimalParam") bigDecimalParam: java.math.BigDecimal,
             @ParamName("bigIntegerParam") bigIntegerParam: java.math.BigInteger): Future[Unit] = {
    ???
  }
}
