package pl.touk.nussknacker.engine.definition

import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition.FragmentParameter

object FragmentParameterValidator {

  def isValid(fragmentParameter: FragmentParameter): Boolean = {

    val fixedValuesDefined =
      fragmentParameter.fixedValueList.nonEmpty || fragmentParameter.fixedValueListPresetId.nonEmpty
    val fixedValuesConflict =
      fragmentParameter.fixedValueList.nonEmpty && fragmentParameter.fixedValueListPresetId.nonEmpty

    //    initialValue, if allowOnlyValuesFromFixedValuesList=true has to be in the list
    //    (harder) initialValue (and fixedValues?) have to be of proper type

    !(fragmentParameter.allowOnlyValuesFromFixedValuesList && !fixedValuesDefined) &&
    !fixedValuesConflict &&
    !(fixedValuesDefined && !List("java.lang.String", "java.lang.Boolean").contains(fragmentParameter.typ.refClazzName))
  }

}
