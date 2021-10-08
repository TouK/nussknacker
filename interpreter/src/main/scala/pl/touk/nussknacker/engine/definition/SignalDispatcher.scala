package pl.touk.nussknacker.engine.definition

import pl.touk.nussknacker.engine.ModelData

object SignalDispatcher {

  def dispatchSignal(modelData: ModelData)(signalType: String, processId: String, parameters: Map[String, AnyRef]): Option[Unit] = {
    modelData.processWithObjectsDefinition.signalsWithTransformers.get(signalType).map { case (signalFactory, _) =>
      modelData.withThisAsContextClassLoader {
        signalFactory.invokeMethod(parameters, None, List(processId))
      }
      ()
    }
  }
}