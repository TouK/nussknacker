package pl.touk.nussknacker.ui.definition.editor

import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.editor.DualEditorMode

protected object ParameterTypeEditorExtractor extends ParameterEditorExtractorStrategy {

  override def evaluateParameterEditor(param: Parameter): Option[ParameterEditor] = {
    param.runtimeClass match {
      case klazz if klazz.isEnum => Some(
        FixedValuesParameterEditor(
          possibleValues = param.runtimeClass.getEnumConstants.toList.map(extractEnumValue(param.runtimeClass))
        )
      )
      case klazz if klazz == classOf[java.lang.String] => Some(
        DualParameterEditor(
          simpleEditor = StringParameterEditor,
          defaultMode = DualEditorMode.RAW
        )
      )
      case klazz if klazz == classOf[java.time.LocalDateTime] => Some(
        DualParameterEditor(
          simpleEditor = DateTimeParameterEditor,
          defaultMode = DualEditorMode.SIMPLE
        )
      )
      case klazz if klazz == classOf[java.time.LocalTime] => Some(
        DualParameterEditor(
          simpleEditor = TimeParameterEditor,
          defaultMode = DualEditorMode.SIMPLE
        )
      )
      case klazz if klazz == classOf[java.time.LocalDate] => Some(
        DualParameterEditor(
          simpleEditor = DateParameterEditor,
          defaultMode = DualEditorMode.SIMPLE
        )
      )
      case _ => Some(RawParameterEditor)
    }
  }

  private def extractEnumValue(enumClass: Class[_])(enumConst: Any): FixedExpressionValue = {
    val enumConstName = enumClass.getMethod("name").invoke(enumConst)
    FixedExpressionValue(s"T(${enumClass.getName}).$enumConstName", enumConst.toString)
  }

}
