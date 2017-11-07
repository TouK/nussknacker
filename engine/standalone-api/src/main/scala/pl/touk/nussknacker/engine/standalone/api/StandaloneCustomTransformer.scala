package pl.touk.nussknacker.engine.standalone.api

import pl.touk.nussknacker.engine.standalone.api.types.InterpreterType

trait StandaloneCustomTransformer {

  type StandaloneCustomTransformation = InterpreterType => InterpreterType

  //TODO: also without variable...
  def createTransformation(outputVariable: String) : StandaloneCustomTransformation
  
}


