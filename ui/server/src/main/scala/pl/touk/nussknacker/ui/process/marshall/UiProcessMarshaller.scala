package pl.touk.nussknacker.ui.process.marshall

import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.restmodel.ArgonautRestModelCodecs

object UiProcessMarshaller extends ProcessMarshaller()(ArgonautRestModelCodecs.nodeAdditionalFieldsOptCodec, ProcessMarshaller.additionalProcessFieldsCodec)
