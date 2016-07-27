package pl.touk.esp.engine.api

import pl.touk.esp.engine.api.sink._

case class InterpretationResult(sinkRef: Option[SinkRef], output: Any)