package pl.touk.esp.ui.api

import pl.touk.esp.engine.build.EspProcessBuilder
import pl.touk.esp.engine.compile.ProcessValidator
import pl.touk.esp.engine.definition.ProcessDefinitionExtractor.ProcessDefinition

object ValidationTestData {

  val existingSourceFactory = "barSource"
  val existingSinkFactory = "barSink"
  val existingStreamTransformer = "transformer"

  val validator = ProcessValidator.default(
    ProcessDefinition.empty
      .withSourceFactory(existingSourceFactory)
      .withSinkFactory(existingSinkFactory)
      .withCustomStreamTransformer(existingStreamTransformer)
  )

  val validProcess =
    EspProcessBuilder
      .id("fooProcess")
      .exceptionHandler()
      .source("source", existingSourceFactory)
      .customNode("custom", "out1", existingStreamTransformer)
      .sink("sink", existingSinkFactory)

  val invalidProcess = {
    val missingSourceFactory = "fooSource"
    val missingSinkFactory = "fooSink"

    EspProcessBuilder
      .id("fooProcess")
      .exceptionHandler()
      .source("source", missingSourceFactory)
      .sink("sink", missingSinkFactory)
  }

}
