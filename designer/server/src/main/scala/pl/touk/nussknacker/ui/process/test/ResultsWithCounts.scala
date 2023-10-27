package pl.touk.nussknacker.ui.process.test

import pl.touk.nussknacker.engine.testmode.TestProcess.TestResults
import pl.touk.nussknacker.ui.processreport.NodeCount

final case class ResultsWithCounts[T](results: TestResults[T], counts: Map[String, NodeCount])
