package pl.touk.esp.engine.definition

import com.typesafe.config.Config
import pl.touk.esp.engine.api.process.{ProcessConfigCreator, WithCategories}
import pl.touk.esp.engine.definition.DefinitionExtractor.ObjectWithMethodDef
import pl.touk.esp.engine.util.ThreadUtils

trait SignalDispatcher {
  //TODO docelowo powinien byc tutaj przekazywany processId, najlepiej przekazywany do ProcessSignalSender jako dodatkowy parametr
  def dispatchSignal(signalType: String, parameters: Map[String, AnyRef]): Option[Unit]
}

trait ConfigCreatorSignalDispatcher extends SignalDispatcher {
  def configCreator: ProcessConfigCreator

  def processConfig: Config

  def dispatchSignal(signalType: String, parameters: Map[String, AnyRef]): Option[Unit] = {
    ThreadUtils.withThisAsContextClassLoader(configCreator.getClass.getClassLoader) {
      configCreator.signals(processConfig).get(signalType).map { signalFactory =>
        val objectWithMethodDef = ObjectWithMethodDef(WithCategories(signalFactory.value), ProcessObjectDefinitionExtractor.signals)
        objectWithMethodDef.invokeMethod(parameters.get, Seq.empty)
        ()
      }
    }
  }
}