package pl.touk.esp.ui.api

import pl.touk.esp.engine.api.MetaData
import pl.touk.esp.engine.build.GraphBuilder
import pl.touk.esp.engine.compile.ProcessValidator
import pl.touk.esp.engine.definition.ProcessDefinitionExtractor.ProcessDefinition
import pl.touk.esp.engine.graph.EspProcess

object ValidationTestData {

  val existingSourceFactory = "barSource"
  val existingSinkFactory = "barSink"

  val validator = ProcessValidator.default(
    ProcessDefinition.empty
      .withSourceFactory(existingSourceFactory)
      .withSinkFactory(existingSinkFactory)
  )

  val validProcess =
    EspProcess(
      MetaData(id = "fooProcess"),
      GraphBuilder.source("source", existingSourceFactory).sink("sink", existingSinkFactory)
    )

  val invalidProcess = {
    val missingSourceFactory = "fooSource"
    val missingSinkFactory = "fooSink"
    EspProcess(
      MetaData(id = "fooProcess"),
      GraphBuilder.source("source", missingSourceFactory).sink("sink", missingSinkFactory)
    )
  }

}
