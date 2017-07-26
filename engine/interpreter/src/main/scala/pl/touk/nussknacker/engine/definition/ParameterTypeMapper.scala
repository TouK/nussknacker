package pl.touk.nussknacker.engine.definition

import java.lang.reflect.Parameter

import pl.touk.nussknacker.engine.api.PossibleValues
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.{ParameterRestriction, StringValues}

object ParameterTypeMapper {
  
  //TODO: other restrictions??
  def prepareRestrictions(klazz: Class[_], p: Parameter) : Option[ParameterRestriction] = {
    mapPossibleValues
      .applyOrElse((klazz, p), (_:(Class[_], Parameter)) => None)
  }

  private def mapPossibleValues : PartialFunction[(Class[_], Parameter), Option[ParameterRestriction]] = {
    case (klazz: Class[_], p:Parameter) if p.getAnnotation(classOf[PossibleValues]) != null =>
      val values  = p.getAnnotation(classOf[PossibleValues]).value().toList
      Some(StringValues(values))
  }

}
