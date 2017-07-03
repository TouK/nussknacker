package pl.touk.esp.engine.compiledgraph

import pl.touk.esp.engine.api.MetaData
import pl.touk.esp.engine.api.exception.EspExceptionHandler

case class CompiledProcessParts(metaData: MetaData, exceptionHandler: EspExceptionHandler, source: part.SourcePart)