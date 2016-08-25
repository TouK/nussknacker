package pl.touk.esp.ui.api

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, RequestEntity, StatusCodes}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest._
import pl.touk.esp.engine.api.MetaData
import pl.touk.esp.engine.build.GraphBuilder
import pl.touk.esp.engine.canonize.ProcessCanonizer
import pl.touk.esp.engine.compile.ProcessValidator
import pl.touk.esp.engine.definition.ProcessDefinitionExtractor.ProcessDefinition
import pl.touk.esp.engine.graph.EspProcess
import pl.touk.esp.ui.process.marshall.{DisplayableProcessCodec, ProcessConverter}
import argonaut.Argonaut._
import argonaut.PrettyParams

class ValidationResourcesSpec extends FlatSpec with ScalatestRouteTest with Matchers with Inside {

  val existingSourceFactory = "barSource"
  val existingSinkFactory = "barSink"
  val validator = ProcessValidator.default(
    ProcessDefinition.empty
      .withSourceFactory(existingSourceFactory)
      .withSinkFactory(existingSinkFactory)
  )
  val route = new ValidationResources(validator).route

  it should "find errors in a bad process" in {
    val missingSourceFactory = "fooSource"
    val missingSinkFactory = "fooSink"
    val process =
      EspProcess(
        MetaData(id = "fooProcess"),
        GraphBuilder.source("source", missingSourceFactory).sink("sink", missingSinkFactory)
      )

    Post("/processValidation", toEntity(process)) ~> route ~> check {
      status shouldEqual StatusCodes.BadRequest
      entityAs[String] should include ("MissingSourceFactory")
    }
  }

  it should "find no errors in a good process" in {
    val process =
      EspProcess(
        MetaData(id = "fooProcess"),
        GraphBuilder.source("source", existingSourceFactory).sink("sink", existingSinkFactory)
      )

    Post("/processValidation", toEntity(process)) ~> route ~> check {
      status shouldEqual StatusCodes.OK
    }
  }

  private def toEntity(process: EspProcess): RequestEntity = {
    val canonical = ProcessCanonizer.canonize(process)
    val displayable = ProcessConverter.toDisplayable(canonical)
    implicit val encode = DisplayableProcessCodec.encoder
    val json = displayable.asJson.pretty(PrettyParams.spaces2.copy(dropNullKeys = true, preserveOrder = true))
    HttpEntity(ContentTypes.`application/json`, json)
  }
}
