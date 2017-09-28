package pl.touk.nussknacker.ui.process.marshall

import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.ui.codec.UiCodecs

object UiProcessMarshaller extends ProcessMarshaller()(UiCodecs.nodeAdditionalFieldsOptCodec, UiCodecs.processAdditionalFieldsOptCodec)
