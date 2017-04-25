package pl.touk.esp.ui.processreport

import pl.touk.esp.ui.process.displayedgraph.DisplayableProcess
import pl.touk.process.report.influxdb.ProcessBaseCounts

class ProcessCountsReporter() {

  import cats.implicits._

  //w tej implementacji wykorzystujemy zalozenie ze z kazdego miejsca w procesie prowadzi jedna sciezka do zrodla
  //przechodzimy po calym procesie od sinka do sourca i wyliczamy licznosci na kazdym z wezlow
  def reportCounts(process: DisplayableProcess, baseCounts: ProcessBaseCounts): Map[String, Long] = {
    def countForSinglePath(nodeId: String, countsSoFar: Map[String, Long], alreadyAddedDeadEnds: Set[String]): (Map[String, Long], Set[String]) = {
      process.edges.find(_.to == nodeId) match {
        case None =>
          (countsSoFar, alreadyAddedDeadEnds)
        case Some(edge) =>
          val previousNodeId = edge.from
          val currentCount = countsSoFar(nodeId)
          baseCounts.deadEnds.get(previousNodeId) match {
            case Some(deadEndCount) if !alreadyAddedDeadEnds.contains(previousNodeId) =>
              val newCounts = countsSoFar ++ Map(previousNodeId -> (currentCount + deadEndCount))
              countForSinglePath(previousNodeId, newCounts, alreadyAddedDeadEnds ++ Set(previousNodeId))
            case _ =>
              val newCounts = countsSoFar ++ Map(previousNodeId -> currentCount)
              countForSinglePath(previousNodeId, newCounts, alreadyAddedDeadEnds)
          }
      }
    }

    val countsForAllPaths = baseCounts.ends.keys.toList.scanLeft((Map.empty[String, Long], Set.empty[String]))((acc, end) => {
      countForSinglePath(end, Map(end -> baseCounts.ends(end)), acc._2)
    })
    countsForAllPaths.map(_._1).reduce(_ combine _)
  }
}
