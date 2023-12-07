package pl.touk.nussknacker.engine.definition.component.parameter

import javax.annotation.Nullable

object OptionalDeterminer {

  def isOptional(
      parameterData: ParameterData,
      isScalaOptionParameter: Boolean,
      isJavaOptionalParameter: Boolean
  ): Boolean =
    isScalaOptionParameter || isJavaOptionalParameter || parameterData.getAnnotation[Nullable].isDefined

}
