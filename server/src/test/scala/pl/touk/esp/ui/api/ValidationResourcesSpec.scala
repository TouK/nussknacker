package pl.touk.esp.ui.api

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest._
import pl.touk.esp.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.esp.engine.graph.expression.Expression
import pl.touk.esp.engine.graph.node
import pl.touk.esp.engine.graph.node.Source
import pl.touk.esp.engine.graph.sink.SinkRef
import pl.touk.esp.engine.graph.source.SourceRef
import pl.touk.esp.ui.api.ProcessValidation.ValidationResult
import pl.touk.esp.ui.api.helpers.TestFactory._
import pl.touk.esp.ui.db.entity.ProcessEntity.ProcessingType
import pl.touk.esp.ui.process.displayedgraph.displayablenode.Edge
import pl.touk.esp.ui.process.displayedgraph.{DisplayableProcess, ProcessProperties}
import pl.touk.esp.ui.security.{LoggedUser, Permission}

class ValidationResourcesSpec extends FlatSpec with ScalatestRouteTest with Matchers with Inside {

  val route = withPermissions(new ValidationResources(processValidation).route, Permission.Read)

  it should "find errors in a bad process" in {
    Post("/processValidation", posting.toEntity(ProcessTestData.invalidProcess)) ~> route ~> check {
      status shouldEqual StatusCodes.OK
      val entity = entityAs[String]
      entity should include ("MissingSourceFactory")
    }
  }

  it should "return fatal error for bad ids" in {
    val invalidCharacters = DisplayableProcess("p1", ProcessProperties(Some(2), Some(false), ExceptionHandlerRef(List()), None),
      List(Source("s1", SourceRef(ProcessTestData.existingSourceFactory, List())), node.Sink("f1\"'", SinkRef(ProcessTestData.existingSinkFactory, List()), None)),
      List(Edge("s1", "f1\"'", None)),
      ProcessingType.Streaming,
      Some(ValidationResult(Map(), List(), List())))


    Post("/processValidation",  posting.toEntity(invalidCharacters)) ~> route ~> check {
      status shouldEqual StatusCodes.BadRequest
      val entity = entityAs[String]
      entity should include ("Node id contains invalid characters")
    }

    val duplicateIds = DisplayableProcess("p1", ProcessProperties(Some(2), Some(false), ExceptionHandlerRef(List()), None),
      List(Source("s1", SourceRef(ProcessTestData.existingSourceFactory, List())), node.Sink("s1", SinkRef(ProcessTestData.existingSinkFactory, List()), None)),
      List(Edge("s1", "s1", None)),
      ProcessingType.Streaming,
      Some(ValidationResult(Map(), List(), List())))


    Post("/processValidation", posting.toEntity(duplicateIds)) ~> route ~> check {
      status shouldEqual StatusCodes.BadRequest
      val entity = entityAs[String]
      entity should include ("Duplicate node ids: s1")
    }
  }

  it should "find errors in process of bad shape" in {

    val invalidShapeProcess = DisplayableProcess("p1", ProcessProperties(Some(2), Some(false), ExceptionHandlerRef(List()), None),
      List(Source("s1", SourceRef(ProcessTestData.existingSourceFactory, List())), node.Filter("f1", Expression("spel", "false"))),
      List(Edge("s1", "f1", None)),
      ProcessingType.Streaming,
      Some(ValidationResult(Map(), List(), List())))

    Post("/processValidation", posting.toEntity(invalidShapeProcess)) ~> route ~> check {
      status shouldEqual StatusCodes.OK
      val entity = entityAs[String]
      entity should include("InvalidTailOfBranch")
    }
  }

  it should "find no errors in a good process" in {
    Post("/processValidation", posting.toEntity(ProcessTestData.validProcess)) ~> route ~> check {
      status shouldEqual StatusCodes.OK
    }
  }

}
