package pl.touk.nussknacker.ui.api

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks._
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions
import pl.touk.nussknacker.security.AuthCredentials
import pl.touk.nussknacker.test.utils.domain.ReflectionBasedUtils
import pl.touk.nussknacker.test.utils.{InvalidExample, OpenAPIExamplesValidator, OpenAPISchemaComponents}
import pl.touk.nussknacker.ui.security.api.AnonymousAccess
import pl.touk.nussknacker.ui.services.NuDesignerExposedApiHttpService
import pl.touk.nussknacker.ui.util.Project
import sttp.apispec.openapi.circe.yaml.RichOpenAPI
import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter
import sttp.tapir.{Endpoint, EndpointInput, auth}

import java.lang.reflect.{Method, Modifier}
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
          "SORT_SCENARIOS",
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
          "CLICK_ROLL_UP_PANEL",
          "CLICK_EXPAND_PANEL",
          "MOVE_PANEL",
          "SEARCH_NODES_IN_SCENARIO",
          "SEARCH_COMPONENTS_IN_SCENARIO",
          "CLICK_OLDER_VERSION",
          "CLICK_NEWER_VERSION",
          "FIRED_KEY_STROKE",
          "CLICK_NODE_DOCUMENTATION",
          "CLICK_COMPONENTS_TAB",
          "CLICK_SCENARIO_SAVE",
          "CLICK_TEST_COUNTS",
          "CLICK_SCENARIO_CANCEL",
          "CLICK_SCENARIO_ARCHIVE_TOGGLE",
          "CLICK_SCENARIO_UNARCHIVE",
          "CLICK_SCENARIO_CUSTOM_ACTION",
          "CLICK_SCENARIO_CUSTOM_LINK"
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

  def generateOpenApiYaml: String = {
    val endpoints = findApiEndpointsClasses().flatMap(findEndpointsInClass)
    val docs = OpenAPIDocsInterpreter(NuDesignerExposedApiHttpService.openAPIDocsOptions).toOpenAPI(
      es = endpoints,
      title = NuDesignerExposedApiHttpService.openApiDocumentTitle,
      version = ""
    )

    docs.toYaml
  }

  private def findApiEndpointsClasses() = {
    ReflectionBasedUtils.findSubclassesOf[BaseEndpointDefinitions]("pl.touk.nussknacker.ui.api")
  }

  private def findEndpointsInClass(clazz: Class[_ <: BaseEndpointDefinitions]) = {
    val endpointDefinitions = createInstanceOf(clazz)
    clazz.getDeclaredMethods.toList
      .filter(isEndpointMethod)
      .sortBy(_.getName)
      .map(instantiateEndpointDefinition(endpointDefinitions, _))
  }

  private def createInstanceOf(clazz: Class[_ <: BaseEndpointDefinitions]) = {
    val basicAuth = auth
      .basic[Option[String]]()
      .map { AnonymousAccess.optionalStringToAuthCredentialsMapping(false) }

    Try(clazz.getConstructor(classOf[EndpointInput[AuthCredentials]]))
      .map(_.newInstance(basicAuth))
      .orElse {
        Try(clazz.getDeclaredConstructor())
          .map(_.newInstance())
      }
      .getOrElse(
        throw new IllegalStateException(
          s"Class ${clazz.getName} is required to have either one parameter constructor or constructor iwhtout parameters"
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
