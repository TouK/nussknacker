package pl.touk.nussknacker.ui.api

import org.reflections.Reflections
import org.reflections.util.ConfigurationBuilder
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.ui.security.api.AuthCredentials
import pl.touk.nussknacker.ui.services.NuDesignerExposedApiHttpService
import pl.touk.nussknacker.ui.util.Project
import sttp.apispec.openapi.circe.yaml.RichOpenAPI
import sttp.tapir.EndpointInput.Auth
import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter
import sttp.tapir.{Endpoint, EndpointInput, auth}

import java.lang.reflect.{Method, Modifier}
import scala.jdk.CollectionConverters._
import scala.util.Try

// if the test fails it probably means that you should regenerate the Nu Designer OpenAPI document
// you can do it but running manually the object `GenerateDesignerOpenApiYamlFile` with main method or
// using SBT's task: `sbt generateDesignerOpenApi`
class NuDesignerApiAvailableToExposeYamlSpec extends AnyFunSuite with Matchers {

  test("Nu Designer OpenAPI document with all available to expose endpoints has to be up to date") {
    val currentNuDesignerOpenApiYamlContent =
      (Project.root / "docs-internal" / "api" / "nu-designer-openapi.yaml").contentAsString
    NuDesignerApiAvailableToExpose.generateOpenApiYaml should be(currentNuDesignerOpenApiYamlContent)
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
    val baseEndpointDefinitionsClass = classOf[BaseEndpointDefinitions]
    val reflections = new Reflections(
      new ConfigurationBuilder().forPackages(baseEndpointDefinitionsClass.getPackageName)
    )
    reflections
      .getSubTypesOf(baseEndpointDefinitionsClass)
      .asScala
      .toList
      .sortBy(_.getName)
  }

  private def findEndpointsInClass(clazz: Class[_ <: BaseEndpointDefinitions]) = {
    val endpointDefinitions = createInstanceOf(clazz)
    clazz.getDeclaredMethods.toList
      .filter(isEndpointMethod)
      .sortBy(_.getName)
      .map(instantiateEndpointDefinition(endpointDefinitions, _))
  }

  private def createInstanceOf(clazz: Class[_ <: BaseEndpointDefinitions]) = {
    Try(clazz.getConstructor(classOf[EndpointInput[AuthCredentials]]))
      .map(_.newInstance(auth.basic[AuthCredentials]()))
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
