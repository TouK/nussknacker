package pl.touk.esp.engine.split

import pl.touk.esp.engine.splittedgraph.part._

object PartsCollector {

  def collectParts(part: ProcessPart): List[ProcessPart] = {
    val children = part match {
      case source: SourcePart =>
        source.nextParts.flatMap(collectParts)
      case agg: AggregateExpressionPart =>
        collectParts(agg.nextPart)
      case agg: AfterAggregationPart =>
        agg.nextParts.flatMap(collectParts)
      case sink: SinkPart =>
        List.empty
    }
    part :: children
  }

}
