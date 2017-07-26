package pl.touk.nussknacker.engine.compiledgraph

import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.api.exception.EspExceptionHandler

case class CompiledProcessParts(metaData: MetaData, exceptionHandler: EspExceptionHandler, source: part.SourcePart)