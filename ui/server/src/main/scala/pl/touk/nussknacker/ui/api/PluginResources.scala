package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.server.{Directives, Route}
import org.apache.commons.io.IOUtils
import pl.touk.http.argonaut.{Argonaut62Support, JsonMarshaller}
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.ProcessingTypeData.ProcessingType
import pl.touk.nussknacker.engine.plugin.{FrontendPluginConfiguration, FrontendPluginConfigurationProvider}
import pl.touk.nussknacker.engine.util.loader.ScalaServiceLoader
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.ExecutionContext

class PluginResources(modelData: Map[ProcessingType, ModelData])(implicit ec: ExecutionContext, jsonMarshaller: JsonMarshaller)
  extends Directives with Argonaut62Support with RouteWithUser {

  import argonaut.ArgonautShapeless._

  //TODO: non-unique names?
  private val plugins = modelData.mapValues { md =>
    val plugins = ScalaServiceLoader.load[FrontendPluginConfigurationProvider](md.modelClassLoader.classLoader)
    plugins.flatMap(_.createConfigurations(md.processConfig))
  }

  private val pluginResources = plugins.flatMap {
    case (processingType, pluginList) => pluginList.flatMap {
      case FrontendPluginConfiguration(pluginName, resources, _) =>
        val loader = (name: String) => IOUtils.toByteArray(modelData(processingType).modelClassLoader.classLoader.getResourceAsStream(name))
        resources.map { name =>
          ((pluginName, name), loader(name))
        }
    }
  }

  def route(implicit user: LoggedUser): Route =
    pathPrefix("plugins") {
      pathEnd {
        get {
          complete {
            plugins.values.flatten.toList.distinct
          }
        }
      } ~ path(Segment / "resources" / Segment) { (pluginName, resourceName) =>
        get {
          complete {
            pluginResources((pluginName, resourceName))
          }
        }
      }
    }

}