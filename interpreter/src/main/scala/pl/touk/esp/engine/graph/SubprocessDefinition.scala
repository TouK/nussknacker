package pl.touk.esp.engine.graph

import pl.touk.esp.engine.canonicalgraph.canonicalnode.CanonicalNode
import pl.touk.esp.engine.definition.DefinitionExtractor.ClazzRef

case class SubprocessDefinition(id: String,
                                parameters: List[(String, ClazzRef)], nodes: List[CanonicalNode])