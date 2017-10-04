package pl.touk.nussknacker.engine.standalone.api

import pl.touk.nussknacker.engine.standalone.StandaloneProcessInterpreter.OutFunType

trait StandaloneCustomTransformer extends {

  type StandaloneCustomTransformation = OutFunType => OutFunType

  //TODO: also without variable...
  def createTransformation(outputVariable: String) : StandaloneCustomTransformation
  
}


