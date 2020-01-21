package pl.touk.nussknacker.engine.definition

import java.lang.reflect.Parameter

import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.editor.{DualEditor, RawEditor, SimpleEditor, SimpleEditorType}

object EditorExtractor {

  def extract(param: Parameter): Option[ParameterEditor] = {
    val dualEditorAnnotation: DualEditor = param.getAnnotation(classOf[DualEditor])
    val simpleEditorAnnotation = param.getAnnotation(classOf[SimpleEditor])
    val rawEditorAnnotation = param.getAnnotation(classOf[RawEditor])

    (dualEditorAnnotation, simpleEditorAnnotation, rawEditorAnnotation) match {
      case (dualEditorAnnotation: DualEditor, null, null) => {
        val defaultMode = dualEditorAnnotation.defaultMode()
        val simpleEditor = dualEditorAnnotation.simpleEditor()
        Some(DualParameterEditor(simpleParameterEditor(simpleEditor), defaultMode))
      }
      case (null, simpleEditorAnnotation: SimpleEditor, null) => Some(simpleParameterEditor(simpleEditorAnnotation))
      case (null, null, rawEditorAnnotation: RawEditor) => Some(RawParameterEditor)
      case _ => None
    }
  }

  private def simpleParameterEditor(simpleEditorAnnotation: SimpleEditor) = {
    simpleEditorAnnotation.`type`() match {
      case SimpleEditorType.BOOL_EDITOR => BoolParameterEditor
      case SimpleEditorType.STRING_EDITOR => StringParameterEditor
      case SimpleEditorType.FIXED_VALUES_EDITOR => FixedValuesParameterEditor(
        simpleEditorAnnotation.possibleValues()
          .map(value => FixedExpressionValue(value.expression(), value.label()))
          .toList
      )
    }
  }
}
