//package pl.touk.nussknacker.ui.services
//
//import org.reflections.Reflections
//import org.reflections.util.ConfigurationBuilder
//import pl.touk.nussknacker.ui.api.BaseEndpointDefinitions
//import pl.touk.nussknacker.ui.security.api.AuthCredentials
//import sttp.apispec.openapi.circe.yaml.RichOpenAPI
//import sttp.tapir.EndpointInput.Auth
//import sttp.tapir.docs.openapi.{OpenAPIDocsInterpreter, OpenAPIDocsOptions}
//import sttp.tapir.{Endpoint, auth}
//
//import scala.jdk.CollectionConverters._
//import java.lang.reflect.{Method, Modifier}
//import scala.util.Try
//
//object NuDesignerApiAvailableToExpose {
//
//  val name = "Nussknacker Designer API"
//
//  val openAPIDocsOptions: OpenAPIDocsOptions = OpenAPIDocsOptions.default
//    .copy(markOptionsAsNullable = true)
//
//  def generateOpenApiYaml: String = {
//    val endpoints = findApiEndpointsClasses().flatMap(findEndpointsInClass)
//    val docs = OpenAPIDocsInterpreter(openAPIDocsOptions).toOpenAPI(
//      es = endpoints,
//      title = NuDesignerApiAvailableToExpose.name,
//      version = ""
//    )
//
//    docs.toYaml
//  }
//
//  private def findApiEndpointsClasses() = {
//    val baseEndpointDefinitionsClass = classOf[BaseEndpointDefinitions]
//    val reflections = new Reflections(new ConfigurationBuilder().forPackages(baseEndpointDefinitionsClass.getPackageName))
//    reflections
//      .getSubTypesOf(baseEndpointDefinitionsClass).asScala
//      .toList
//      .sortBy(_.getName)
//  }
//
//  private def findEndpointsInClass(clazz: Class[_ <: BaseEndpointDefinitions]) = {
//    val endpointDefinitions = createInstanceOf(clazz)
//    clazz
//      .getDeclaredMethods.toList
//      .filter(isEndpointMethod)
//      .sortBy(_.getName)
//      .map(instantiateEndpointDefinition(endpointDefinitions, _))
//  }
//
//  private def createInstanceOf(clazz: Class[_ <: BaseEndpointDefinitions]) = {
//    Try(clazz.getConstructor(classOf[Auth[AuthCredentials, _]]))
//      .map(_.newInstance(auth.basic[AuthCredentials]()))
//      .orElse {
//        Try(clazz.getDeclaredConstructor())
//          .map(_.newInstance())
//      }
//      .getOrElse(throw new IllegalStateException(s"Class ${clazz.getName} is required to have either one parameter constructor or constructor iwhtout parameters"))
//  }
//
//  private def isEndpointMethod(method: Method) = {
//    method.getReturnType == classOf[Endpoint[_, _, _, _, _]] &&
//      Modifier.isPublic(method.getModifiers) &&
//      method.getParameterCount == 0 &&
//      !method.getName.startsWith("base")
//  }
//
//  private def instantiateEndpointDefinition(instance: BaseEndpointDefinitions, method: Method) = {
//    method.invoke(instance).asInstanceOf[Endpoint[_, _, _, _, _]]
//  }
//
//}

// todo: to remove