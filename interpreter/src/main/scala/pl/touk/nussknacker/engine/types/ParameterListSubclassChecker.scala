package pl.touk.nussknacker.engine.types

import pl.touk.nussknacker.engine.api.generics.ParameterList

object ParameterListSubclassChecker {
  def check(subclassParameters: ParameterList, superclassParameters: ParameterList): Boolean = {
    val ParameterList(subclassNoVarArg, subclassVarArgOption) = subclassParameters
    val ParameterList(superclassNoVarArg, superclassVarArgOption) = superclassParameters

    val subclassNoVarArgOption = subclassNoVarArg.map(Some(_))
    val superclassNoVarArgOption = superclassNoVarArg.map(Some(_))

    val zippedParameters = subclassNoVarArgOption.zipAll(superclassNoVarArgOption, subclassVarArgOption, superclassVarArgOption) :+
      (subclassVarArgOption, superclassVarArgOption)

    subclassNoVarArg.length >= superclassNoVarArg.length && zippedParameters.forall {
      case (None, None) => true
      case (None, Some(_)) => true
      case (Some(_), None) => false
      case (Some(sub), Some(sup)) => sub.refClazz.canBeSubclassOf(sup.refClazz)
    }
  }
}
