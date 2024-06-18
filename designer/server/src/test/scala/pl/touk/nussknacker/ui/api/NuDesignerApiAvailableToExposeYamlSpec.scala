package pl.touk.nussknacker.ui.api

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks._
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions
import pl.touk.nussknacker.security.AuthCredentials.PassedAuthCredentials
import pl.touk.nussknacker.test.utils.domain.ReflectionBasedUtils
import pl.touk.nussknacker.test.utils.{InvalidExample, OpenAPIExamplesValidator, OpenAPISchemaComponents}
import pl.touk.nussknacker.ui.api.description.StatisticsApiEndpoints.Dtos.StatisticName
import pl.touk.nussknacker.ui.security.api.AuthManager
import pl.touk.nussknacker.ui.security.api.AuthManager.ImpersonationConsideringInputEndpoint
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
        StatisticName.values.map(_.value).toSet
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
      .map(_.map(PassedAuthCredentials))(_.map(_.value))
      .withPossibleImpersonation(false)

    Try(clazz.getConstructor(classOf[EndpointInput[PassedAuthCredentials]]))
      .map(_.newInstance(basicAuth))
      .orElse {
        Try(clazz.getDeclaredConstructor())
          .map(_.newInstance())
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
