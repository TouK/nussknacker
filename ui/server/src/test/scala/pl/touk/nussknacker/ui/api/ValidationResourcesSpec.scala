package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest._
import pl.touk.nussknacker.engine.api.StreamMetaData
import pl.touk.nussknacker.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node
import pl.touk.nussknacker.engine.graph.node.{NodeData, Source}
import pl.touk.nussknacker.engine.graph.service.ServiceRef
import pl.touk.nussknacker.engine.graph.sink.SinkRef
import pl.touk.nussknacker.engine.graph.source.SourceRef
import pl.touk.nussknacker.ui.api.helpers.TestCodecs._
import pl.touk.nussknacker.ui.api.helpers.TestFactory
import pl.touk.nussknacker.ui.api.helpers.TestFactory._
import pl.touk.nussknacker.ui.api.helpers.TestProcessingTypes
import pl.touk.nussknacker.ui.process.displayedgraph.displayablenode.Edge
import pl.touk.nussknacker.ui.process.displayedgraph.{DisplayableProcess, ProcessProperties}
import pl.touk.nussknacker.ui.security.api.Permission
import pl.touk.nussknacker.ui.validation.ValidationResults.ValidationResult

class ValidationResourcesSpec extends FlatSpec with ScalatestRouteTest with Matchers with Inside {

  val route = withPermissions(new ValidationResources(processValidation), testPermissionRead)

  it should "find errors in a bad process" in {
    Post("/processValidation", posting.toEntity(ProcessTestData.invalidProcess)) ~> route ~> check {
      status shouldEqual StatusCodes.OK
      val entity = entityAs[String]
      entity should include ("MissingSourceFactory")
    }
  }

  it should "return fatal error for bad ids" in {
    val invalidCharacters = newDisplayableProcess("p1",
      List(Source("s1", SourceRef(ProcessTestData.existingSourceFactory, List())), node.Sink("f1\"'", SinkRef(ProcessTestData.existingSinkFactory, List()), None)),
      List(Edge("s1", "f1\"'", None))
    )

    Post("/processValidation",  posting.toEntity(invalidCharacters)) ~> route ~> check {
      status shouldEqual StatusCodes.BadRequest
      val entity = entityAs[String]
      entity should include ("Node id contains invalid characters")
    }

    val duplicateIds = newDisplayableProcess("p1",
      List(Source("s1", SourceRef(ProcessTestData.existingSourceFactory, List())), node.Sink("s1", SinkRef(ProcessTestData.existingSinkFactory, List()), None)),
      List(Edge("s1", "s1", None))
    )

    Post("/processValidation", posting.toEntity(duplicateIds)) ~> route ~> check {
      status shouldEqual StatusCodes.BadRequest
      val entity = entityAs[String]
      entity should include ("Duplicate node ids: s1")
    }
  }

  it should "find errors in process of bad shape" in {
    val invalidShapeProcess = newDisplayableProcess("p1",
      List(Source("s1", SourceRef(ProcessTestData.existingSourceFactory, List())), node.Filter("f1", Expression("spel", "false"))),
      List(Edge("s1", "f1", None))
    )

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

  it should "warn if process has disabled filter or processor" in {
    val nodes = List(
      node.Source("source1", SourceRef(ProcessTestData.existingSourceFactory, List())),
      node.Filter("filter1", Expression("spel", "false"), isDisabled = Some(true)),
      node.Processor("proc1", ServiceRef(ProcessTestData.existingServiceId, List.empty), isDisabled = Some(true)),
      node.Sink("sink1", SinkRef(ProcessTestData.existingSinkFactory, List.empty))
    )
    val edges = List(Edge("source1", "filter1", None), Edge("filter1", "proc1", None), Edge("proc1", "sink1", None))
    val processWithDisabledFilterAndProcessor = newDisplayableProcess("p1", nodes, edges)

    Post("/processValidation", posting.toEntity(processWithDisabledFilterAndProcessor)) ~> route ~> check {
      status shouldEqual StatusCodes.OK
      val validation = responseAs[String].decodeOption[ValidationResult].get
      validation.warnings.invalidNodes("filter1").head.message should include("Node is disabled")
      validation.warnings.invalidNodes("proc1").head.message should include("Node is disabled")
    }
  }

  def newDisplayableProcess(id: String, nodes: List[NodeData], edges: List[Edge]) = {
    DisplayableProcess(
      id = id,
      properties = ProcessProperties(StreamMetaData(Some(2), Some(false)), ExceptionHandlerRef(List()), false, None, subprocessVersions = Map.empty),
      nodes = nodes,
      edges = edges,
      processingType = TestProcessingTypes.Streaming
    )
  }

}
