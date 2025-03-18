package pl.touk.nussknacker.engine.management.sample.service

import pl.touk.nussknacker.engine.api.{MethodToInvoke, ParamName, Service}
import pl.touk.nussknacker.engine.api.editor._
import pl.touk.nussknacker.engine.api.validation.CompileTimeEvaluableValue

import javax.validation.constraints.NotBlank
import scala.concurrent.Future

// In services all parameters are lazy evaluated
class SimpleTypesService extends Service with Serializable {

  @MethodToInvoke
  def invoke(
      @ParamName("booleanParam")
      @Editor(
        `type` = EditorType.BOOL_EDITOR
      ) booleanParam: Boolean,
      @ParamName("DualParam")
      @Editor(`type` = EditorType.SPEL_TEMPLATE_EDITOR)
      @Editor(`type` = EditorType.SPEL_EDITOR)
      @NotBlank
      dualParam: String,
      @ParamName("SimpleParam")
      @Editor(`type` = EditorType.SPEL_TEMPLATE_EDITOR)
      simpleParam: String,
      @ParamName("RawParam")
      @Editor(`type` = EditorType.SPEL_TEMPLATE_EDITOR)
      @Editor(`type` = EditorType.SPEL_EDITOR)
      rawParam: String,
      @ParamName("intParam")
      @Editor(`type` = EditorType.SPEL_TEMPLATE_EDITOR)
      @CompileTimeEvaluableValue
      intParam: Int,
      @ParamName("rawIntParam")
      @Editor(`type` = EditorType.SPEL_EDITOR)
      @CompileTimeEvaluableValue
      rawIntParam: Int,
      @ParamName("fixedValuesStringParam")
      @Editor(
        `type` = EditorType.FIXED_VALUES_EDITOR,
        possibleValues = Array(
          new LabeledExpression(expression = "'Max'", label = "Max"),
          new LabeledExpression(expression = "'Min'", label = "Min")
        )
      ) fixedValuesStringParam: String,
      @ParamName("bigDecimalParam") bigDecimalParam: java.math.BigDecimal,
      @ParamName("bigIntegerParam") bigIntegerParam: java.math.BigInteger
  ): Future[Unit] = {
    ???
  }

}
