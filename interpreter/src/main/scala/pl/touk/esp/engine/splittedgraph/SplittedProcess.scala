package pl.touk.esp.engine.splittedgraph

import pl.touk.esp.engine.api.MetaData
import pl.touk.esp.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.esp.engine.splittedgraph.part.SourcePart

case class SplittedProcess(metaData: MetaData, exceptionHandlerRef: ExceptionHandlerRef, source: SourcePart)
