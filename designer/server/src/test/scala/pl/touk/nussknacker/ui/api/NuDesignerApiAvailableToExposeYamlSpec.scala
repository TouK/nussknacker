package pl.touk.nussknacker.ui.api

import akka.actor.ActorSystem
import akka.stream.Materializer
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks._
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions
import pl.touk.nussknacker.security.AuthCredentials.PassedAuthCredentials
import pl.touk.nussknacker.test.utils.domain.ReflectionBasedUtils
import pl.touk.nussknacker.test.utils.{InvalidExample, OpenAPIExamplesValidator, OpenAPISchemaComponents}
import pl.touk.nussknacker.ui.security.api.AuthManager.ImpersonationConsideringInputEndpoint
import pl.touk.nussknacker.ui.server.{AkkaHttpBasedTapirStreamEndpointProvider, TapirStreamEndpointProvider}
import pl.touk.nussknacker.ui.services.NuDesignerExposedApiHttpService
import pl.touk.nussknacker.ui.util.Project
import sttp.apispec.openapi.circe.yaml.RichOpenAPI
import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter
import sttp.tapir.{Endpoint, EndpointInput, auth}

import java.lang.reflect.{Method, Modifier}
import scala.concurrent.Await
import scala.util.Try

// if the test fails it probably means that you should regenerate the Nu Designer OpenAPI document
// you can do it but running manually the object `GenerateDesignerOpenApiYamlFile` with main method or
// using SBT's task: `sbt generateDesignerOpenApi`
// Warning! OpenAPI can be generated differently depending on the scala version.
class NuDesignerApiAvailableToExposeYamlSpec extends AnyFunSuite with Matchers {

  test("Nu Designer OpenAPI document with all available to expose endpoints should have examples matching schemas") {
    val generatedSpec            = NuDesignerApiAvailableToExpose.generateOpenApiYaml
    val examplesValidationResult = OpenAPIExamplesValidator.forTapir.validateExamples(generatedSpec)
    val clue = examplesValidationResult
      .map { case InvalidExample(_, _, operationId, isRequest, exampleId, errors) =>
        errors
          .map(_.getMessage)
          .distinct
          .map("    " + _)
          .mkString(s"$operationId > ${if (isRequest) "request" else "response"} > $exampleId\n", "\n", "")
      }
      .mkString("", "\n", "\n")
    withClue(clue) {
      examplesValidationResult.size shouldEqual 0
    }
  }

  test("Nu Designer OpenAPI document with all available to expose endpoints has to be up to date") {
    val currentNuDesignerOpenApiYamlContent =
      (Project.root / "docs-internal" / "api" / "nu-designer-openapi.yaml").contentAsString
    NuDesignerApiAvailableToExpose.generateOpenApiYaml should be(currentNuDesignerOpenApiYamlContent)
  }

  test("API enum compatibility test") {
    val data = Table(
      ("enumName", "enumValues"),
      (
        "StatisticName",
        Set(
          "SEARCH_SCENARIOS_BY_NAME",
          "FILTER_SCENARIOS_BY_STATUS",
          "FILTER_SCENARIOS_BY_PROCESSING_MODE",
          "FILTER_SCENARIOS_BY_CATEGORY",
          "FILTER_SCENARIOS_BY_AUTHOR",
          "FILTER_SCENARIOS_BY_OTHER",
          "SORT_SCENARIOS_BY_SORT_OPTION",
          "SEARCH_COMPONENTS_BY_NAME",
          "FILTER_COMPONENTS_BY_GROUP",
          "FILTER_COMPONENTS_BY_PROCESSING_MODE",
          "FILTER_COMPONENTS_BY_CATEGORY",
          "FILTER_COMPONENTS_BY_MULTIPLE_CATEGORIES",
          "FILTER_COMPONENTS_BY_USAGES",
          "CLICK_COMPONENT_USAGES",
          "SEARCH_COMPONENT_USAGES_BY_NAME",
          "FILTER_COMPONENT_USAGES_BY_STATUS",
          "FILTER_COMPONENT_USAGES_BY_CATEGORY",
          "FILTER_COMPONENT_USAGES_BY_AUTHOR",
          "FILTER_COMPONENT_USAGES_BY_OTHER",
          "CLICK_SCENARIO_FROM_COMPONENT_USAGES",
          "CLICK_GLOBAL_METRICS_TAB",
          "CLICK_ACTION_DEPLOY",
          "CLICK_ACTION_METRICS",
          "CLICK_VIEW_ZOOM_IN",
          "CLICK_VIEW_ZOOM_OUT",
          "CLICK_VIEW_RESET",
          "CLICK_EDIT_UNDO",
          "CLICK_EDIT_REDO",
          "CLICK_EDIT_COPY",
          "CLICK_EDIT_PASTE",
          "CLICK_EDIT_DELETE",
          "CLICK_EDIT_LAYOUT",
          "CLICK_SCENARIO_PROPERTIES",
          "CLICK_SCENARIO_COMPARE",
          "CLICK_SCENARIO_MIGRATE",
          "CLICK_SCENARIO_IMPORT",
          "CLICK_SCENARIO_JSON",
          "CLICK_SCENARIO_PDF",
          "CLICK_SCENARIO_ARCHIVE",
          "CLICK_TEST_GENERATED",
          "CLICK_TEST_ADHOC",
          "CLICK_TEST_FROM_FILE",
          "CLICK_TEST_GENERATE_FILE",
          "CLICK_TEST_HIDE",
          "CLICK_MORE_SCENARIO_DETAILS",
          "CLICK_EXPAND_PANEL",
          "CLICK_COLLAPSE_PANEL",
          "MOVE_TOOLBAR_PANEL",
          "SEARCH_NODES_IN_SCENARIO",
          "SEARCH_COMPONENTS_IN_SCENARIO",
          "CLICK_OLDER_VERSION",
          "CLICK_NEWER_VERSION",
          "CLICK_NODE_DOCUMENTATION",
          "CLICK_COMPONENTS_TAB",
          "CLICK_SCENARIO_SAVE",
          "CLICK_TEST_COUNTS",
          "CLICK_SCENARIO_CANCEL",
          "CLICK_SCENARIO_ARCHIVE_TOGGLE",
          "CLICK_SCENARIO_UNARCHIVE",
          "CLICK_SCENARIO_CUSTOM_ACTION",
          "CLICK_SCENARIO_CUSTOM_LINK",
          "DOUBLE_CLICK_RANGE_SELECT_NODES",
          "KEYBOARD_AND_CLICK_RANGE_SELECT_NODES",
          "KEYBOARD_COPY_NODE",
          "KEYBOARD_PASTE_NODE",
          "KEYBOARD_CUT_NODE",
          "KEYBOARD_SELECT_ALL_NODES",
          "KEYBOARD_REDO_SCENARIO_CHANGES",
          "KEYBOARD_UNDO_SCENARIO_CHANGES",
          "KEYBOARD_DELETE_NODES",
          "KEYBOARD_DESELECT_ALL_NODES",
          "KEYBOARD_FOCUS_SEARCH_NODE_FIELD"
        )
      ),
    )
    val generatedSpec = NuDesignerApiAvailableToExpose.generateOpenApiYaml

    forAll(data) { (enumName, enumValues) =>
      OpenAPISchemaComponents.enumValues(generatedSpec, enumName) shouldBe enumValues
    }
  }

}

object NuDesignerApiAvailableToExpose {

  def generateOpenApiYaml: String = withStreamProvider { streamProvider =>
    val endpoints = findApiEndpointsClasses().flatMap(findEndpointsInClass(streamProvider))
    val docs = OpenAPIDocsInterpreter(NuDesignerExposedApiHttpService.openAPIDocsOptions).toOpenAPI(
      es = endpoints,
      title = NuDesignerExposedApiHttpService.openApiDocumentTitle,
      version = ""
    )
    docs.toYaml
  }

  private def withStreamProvider[T](handle: TapirStreamEndpointProvider => T): T = {
    val actorSystem: ActorSystem                    = ActorSystem()
    val mat: Materializer                           = Materializer(actorSystem)
    val streamProvider: TapirStreamEndpointProvider = new AkkaHttpBasedTapirStreamEndpointProvider()(mat)
    val result                                      = handle(streamProvider)
    Await.result(actorSystem.terminate(), scala.concurrent.duration.Duration.Inf)
    result
  }

  private def findApiEndpointsClasses() = {
    ReflectionBasedUtils.findSubclassesOf[BaseEndpointDefinitions]("pl.touk.nussknacker.ui.api")
  }

  private def findEndpointsInClass(
      streamEndpointProvider: TapirStreamEndpointProvider
  )(
      clazz: Class[_ <: BaseEndpointDefinitions]
  ) = {
    val endpointDefinitions = createInstanceOf(streamEndpointProvider)(clazz)
    clazz.getDeclaredMethods.toList
      .filter(isEndpointMethod)
      .sortBy(_.getName)
      .map(instantiateEndpointDefinition(endpointDefinitions, _))
  }

  private def createInstanceOf(
      streamEndpointProvider: TapirStreamEndpointProvider,
  )(
      clazz: Class[_ <: BaseEndpointDefinitions],
  ) = {
    val basicAuth = auth
      .basic[Option[String]]()
      .map(_.map(PassedAuthCredentials))(_.map(_.value))
      .withPossibleImpersonation(false)

    Try(clazz.getConstructor(classOf[EndpointInput[PassedAuthCredentials]]))
      .map(_.newInstance(basicAuth))
      .orElse {
        Try(clazz.getDeclaredConstructor())
          .map(_.newInstance())
      }
      .orElse {
        Try(clazz.getConstructor(classOf[EndpointInput[PassedAuthCredentials]], classOf[TapirStreamEndpointProvider]))
          .map(_.newInstance(basicAuth, streamEndpointProvider))
      }
      .getOrElse(
        throw new IllegalStateException(
          s"Class ${clazz.getName} is required to have either one parameter constructor or constructor without parameters"
        )
      )
  }

  private def isEndpointMethod(method: Method) = {
    method.getReturnType == classOf[Endpoint[_, _, _, _, _]] &&
    Modifier.isPublic(method.getModifiers) &&
    method.getParameterCount == 0 &&
    !method.getName.startsWith("base")
  }

  private def instantiateEndpointDefinition(instance: BaseEndpointDefinitions, method: Method) = {
    method.invoke(instance).asInstanceOf[Endpoint[_, _, _, _, _]]
  }

}
