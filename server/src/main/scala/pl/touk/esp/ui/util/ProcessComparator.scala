package pl.touk.esp.ui.util

import pl.touk.esp.ui.process.displayedgraph.DisplayableProcess

object ProcessComparator {

  def compare(process1: DisplayableProcess, process2: DisplayableProcess) : List[Difference] = {
    //TODO :)
    List()
  }

  case class Difference(nodeId: String, description: String)
}
