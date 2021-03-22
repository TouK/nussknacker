package pl.touk.nussknacker.engine.standalone.api

import pl.touk.nussknacker.engine.api.LazyParameterInterpreter
import pl.touk.nussknacker.engine.standalone.api.types.InterpreterType

trait StandaloneCustomTransformer {

  type StandaloneCustomTransformation = (InterpreterType, LazyParameterInterpreter) => InterpreterType

  def createTransformation(outputVariable: Option[String]) : StandaloneCustomTransformation
  
}


