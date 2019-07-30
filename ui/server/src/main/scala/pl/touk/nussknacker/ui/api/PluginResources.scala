package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.{Directives, Route}
import argonaut.Json
import pl.touk.http.argonaut.{Argonaut62Support, JsonMarshaller}
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.ProcessingTypeData.ProcessingType
import pl.touk.nussknacker.engine.plugin.FrontendPlugin
import pl.touk.nussknacker.engine.util.plugins.Plugin
import pl.touk.nussknacker.ui.security.api.LoggedUser
import argonaut.ArgonautShapeless._

import scala.concurrent.ExecutionContext

class PluginResources(modelData: Map[ProcessingType, ModelData])(implicit ec: ExecutionContext, jsonMarshaller: JsonMarshaller)
  extends Directives with Argonaut62Support with RouteWithUser {

  import argonaut.ArgonautShapeless._

  private val plugins: Map[String, FrontendPlugin] = Plugin.load[FrontendPlugin](getClass.getClassLoader).map(fp => (fp.name, fp)).toMap

  private val pluginConfigs: Map[String, UiPluginConfig] = plugins.mapValues { fp =>
    val typeSpecificConfigs = modelData
      .mapValues(md => fp.createTypeSpecific(md.modelClassLoader.classLoader, md.processConfig))
      .collect {
        case (name, Some(config)) => (name, config)
      }
    UiPluginConfig(fp.externalResources, fp.internalResources.keysIterator.toList, typeSpecificConfigs)
  }

  def route(implicit user: LoggedUser): Route =
    pathPrefix("plugins") {
      pathEnd {
        get {
          complete {
            pluginConfigs
          }
        }
      } ~
        pathPrefix(Segment / "resources") { pluginName =>
          fromOption(plugins.get(pluginName), "Plugin not found") { plugin =>
            path(Segment) { resourceName =>
              fromOption(plugin.internalResources.get(resourceName), "Resource not found") { bytes =>
                complete(bytes)
              }
            }
          }
        }
    }

  private def fromOption[T](maybeObj: Option[T], notFound: String)(run: T => Route): Route = {
    maybeObj match {
      case Some(obj) =>
        run(obj)
      case None =>
        complete(HttpResponse(status = StatusCodes.NotFound, entity = "Processing type not found"))
    }
  }


  case class UiPluginConfig(externalResources: List[String],
                            internalResources: List[String],
                            configs: Map[String, Json])

}