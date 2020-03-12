package pl.touk.nussknacker.engine.definition

import java.lang.reflect.Parameter

import pl.touk.nussknacker.engine.api.definition.{FixedExpressionValue, FixedValuesParameterEditor, ParameterEditor}

object ParameterTypeEditorDeterminer {
  def tryDetermine(p: Parameter): Option[ParameterEditor] = {
    val runtimeClass = p.getType
    runtimeClass match {
      case klazz if klazz.isEnum => Some(
        FixedValuesParameterEditor(
          possibleValues = runtimeClass.getEnumConstants.toList.map(extractEnumValue(runtimeClass))
        )
      )
      case _ => None
    }
  }

  private def extractEnumValue(enumClass: Class[_])(enumConst: Any): FixedExpressionValue = {
    val enumConstName = enumClass.getMethod("name").invoke(enumConst)
    FixedExpressionValue(s"T(${enumClass.getName}).$enumConstName", enumConst.toString)
  }
}
