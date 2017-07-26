package pl.touk.nussknacker.engine.splittedgraph

import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.nussknacker.engine.splittedgraph.part.SourcePart

case class SplittedProcess(metaData: MetaData, exceptionHandlerRef: ExceptionHandlerRef, source: SourcePart)
