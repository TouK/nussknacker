package pl.touk.nussknacker.ui.process.marshall

import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.restmodel.RestModelCodecs

object UiProcessMarshaller extends ProcessMarshaller()(RestModelCodecs.nodeAdditionalFieldsOptCodec, ProcessMarshaller.additionalProcessFieldsCodec)
