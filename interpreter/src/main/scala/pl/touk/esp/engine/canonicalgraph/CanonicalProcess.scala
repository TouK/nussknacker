package pl.touk.esp.engine.canonicalgraph

import pl.touk.esp.engine.api.MetaData
import pl.touk.esp.engine.canonicalgraph.canonicalnode.CanonicalNode

case class CanonicalProcess(metaData: MetaData, nodes: List[CanonicalNode])
