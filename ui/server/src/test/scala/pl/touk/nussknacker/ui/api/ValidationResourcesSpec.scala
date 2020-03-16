package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model.{ContentTypeRange, StatusCodes}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import org.scalatest._
import pl.touk.nussknacker.engine.api.StreamMetaData
import pl.touk.nussknacker.engine.api.definition.{FixedValuesParameterEditor, FixedValuesValidator, LiteralIntValidator, MandatoryParameterValidator, StringParameterEditor}
import pl.touk.nussknacker.engine.api.process.AdditionalPropertyConfig
import pl.touk.nussknacker.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node
import pl.touk.nussknacker.engine.graph.node.{NodeData, Source}
import pl.touk.nussknacker.engine.graph.service.ServiceRef
import pl.touk.nussknacker.engine.graph.sink.SinkRef
import pl.touk.nussknacker.engine.graph.source.SourceRef
import pl.touk.nussknacker.restmodel.displayedgraph.displayablenode.Edge
import pl.touk.nussknacker.restmodel.displayedgraph.{DisplayableProcess, ProcessProperties}
import pl.touk.nussknacker.restmodel.validation.ValidationResults.ValidationResult
import pl.touk.nussknacker.ui.api.helpers.TestFactory._
import pl.touk.nussknacker.ui.api.helpers.{ProcessTestData, TestFactory, TestProcessingTypes}
import pl.touk.nussknacker.ui.uiresolving.UIProcessResolving
import pl.touk.nussknacker.ui.validation.ProcessValidation

class ValidationResourcesSpec extends FlatSpec with ScalatestRouteTest with Matchers with Inside with FailFastCirceSupport {

  val processValidation = new ProcessValidation(
    mapProcessingTypeDataProvider(TestProcessingTypes.Streaming -> ProcessTestData.validator),
    mapProcessingTypeDataProvider(TestProcessingTypes.Streaming -> Map(
      "requiredStringProperty" -> AdditionalPropertyConfig(None, Some(StringParameterEditor), Some(List(MandatoryParameterValidator)), Some("label")),
      "fixedValueOptionalProperty" -> AdditionalPropertyConfig(None, Some(FixedValuesParameterEditor(possibleValues)), Some(List(FixedValuesValidator(possibleValues))), None),
      "intOptionalProperty" -> AdditionalPropertyConfig(None, None, Some(List(LiteralIntValidator)), Some("label"))
    )),
    sampleResolver,
    emptyProcessingTypeDataProvider
  )
  val route: Route = withPermissions(new ValidationResources(new UIProcessResolving(processValidation, emptyProcessingTypeDataProvider)), testPermissionRead)

  private implicit final val string: FromEntityUnmarshaller[String] = Unmarshaller.stringUnmarshaller.forContentTypes(ContentTypeRange.*)

  it should "find errors in a bad process" in {
    Post("/processValidation", posting.toEntity(ProcessTestData.invalidProcess)) ~> route ~> check {
      status shouldEqual StatusCodes.OK
      val entity = entityAs[String]
      entity should include ("MissingSourceFactory")
    }
  }

  it should "find errors in process with Mandatory parameters" in {
    Post("/processValidation", posting.toEntity(ProcessTestData.invalidProcessWithEmptyMandatoryParameter)) ~> route ~> check {
      status shouldEqual StatusCodes.OK
      val entity = entityAs[String]
      entity should include ("No expression found for mandatory parameter")
    }
  }

  it should "find errors in process with NotBlank parameters" in {
    Post("/processValidation", posting.toEntity(ProcessTestData.invalidProcessWithBlankParameter)) ~> route ~> check {
      status shouldEqual StatusCodes.OK
      val entity = entityAs[String]
      entity should include ("Blank expression for not blank parameter")
    }
  }

  it should "find errors in process properties" in {
    Post("/processValidation", posting.toEntity(TestFactory.processWithInvalidAdditionalProperties)) ~> route ~> check {
      status shouldEqual StatusCodes.OK
      val entity = entityAs[String]
      entity should include ("Configured property requiredStringProperty (label) is missing")
      entity should include ("Property fixedValueOptionalProperty has invalid value")
      entity should include ("Unknown property unknown")
      entity should include ("Property intOptionalProperty (label) has value of invalid type")
    }
  }

  it should "find errors in process with wrong fixed expression value" in {
    Post("/processValidation", posting.toEntity(ProcessTestData.invalidProcessWithWrongFixedExpressionValue)) ~> route ~> check {
      status shouldEqual StatusCodes.OK
      val entity = entityAs[String]
      entity should include("Property expression has invalid value")
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
      val validation = responseAs[ValidationResult]
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
