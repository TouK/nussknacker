package pl.touk.nussknacker.engine.definition

import java.lang.reflect.Parameter

import pl.touk.nussknacker.engine.api.PossibleValues
import pl.touk.nussknacker.engine.api.definition.{FixedExpressionValue, FixedExpressionValues, ParameterRestriction}

object ParameterTypeMapper {
  
  //TODO: other restrictions??
  def prepareRestrictions(klazz: Class[_], p: Option[Parameter]) : Option[ParameterRestriction] = {
    handlePossibleValuesAnnotation
      .orElse(handleJavaEnumPossibleValues)
      .applyOrElse((klazz, p), (_:(Class[_], Option[Parameter])) => None)
  }

  private def handleJavaEnumPossibleValues : PartialFunction[(Class[_], Option[Parameter]), Option[ParameterRestriction]] = {
    case (klazz: Class[_], _) if klazz.isEnum => Some(FixedExpressionValues(klazz.getEnumConstants.toList.map(extractEnumValue(klazz))))
  }

  private def extractEnumValue(enumClass: Class[_])(enumConst: Any): FixedExpressionValue = {
    val enumConstName = enumClass.getMethod("name").invoke(enumConst)
    FixedExpressionValue(s"T(${enumClass.getName}).$enumConstName", enumConst.toString)
  }

  private def handlePossibleValuesAnnotation : PartialFunction[(Class[_], Option[Parameter]), Option[ParameterRestriction]] = {
    case (klazz: Class[_], Some(p:Parameter)) if p.getAnnotation(classOf[PossibleValues]) != null =>
      val values  = p.getAnnotation(classOf[PossibleValues]).value().toList
      Some(FixedExpressionValues(values.map(value => FixedExpressionValue(s"'$value'", value))))
  }

}
