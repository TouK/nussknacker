package pl.touk.nussknacker.ui.process.test

import io.circe.Json
import pl.touk.nussknacker.engine.testmode.TestProcess.TestResults
import pl.touk.nussknacker.ui.processreport.NodeCount

import java.time.Instant

final case class ResultsWithCounts(timestamp: Instant, results: TestResults[Json], counts: Map[String, NodeCount])
