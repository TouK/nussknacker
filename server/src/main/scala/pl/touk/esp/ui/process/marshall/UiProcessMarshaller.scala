package pl.touk.esp.ui.process.marshall

import pl.touk.esp.engine.marshall.ProcessMarshaller
import pl.touk.esp.ui.codec.UiCodecs

object UiProcessMarshaller {
  def apply(): ProcessMarshaller = {
    new ProcessMarshaller()(UiCodecs.nodeAdditionalFieldsOptCodec, UiCodecs.processAdditionalFieldsOptCodec)
  }
}
