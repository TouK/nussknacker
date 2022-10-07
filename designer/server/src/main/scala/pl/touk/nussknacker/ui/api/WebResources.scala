package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model.headers.{CacheDirectives, `Cache-Control`}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.{Directives, Route}
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.io.IOUtils
import pl.touk.nussknacker.engine.version.BuildInfo
import pl.touk.nussknacker.ui.config.{UsageStatisticsReportsConfig, UsageStatisticsUrl}

class WebResources(publicPath: String, usageStatisticsReports: UsageStatisticsReportsConfig) extends Directives with LazyLogging {

  //see config.js comment
  private lazy val mainContent = {
    val data = getClass.getClassLoader.getResourceAsStream("web/static/main.html")
    val content = Option(data).map(IOUtils.toString).getOrElse {
      logger.error("Failed to find web/static/main.html - probably frontend resources are not packaged in jar. Frontend won't work properly!")
      ""
    }
    content.replace("__publicPath__", publicPath)
  }

  val route: Route = handleAssets("submodules") ~ handleAssets("static") ~ get {
    //main.html instead of index.html to not interfere with flink's static resources...
    noCache {
      val data = ByteString(withUsageStatisticsReporting(mainContent))
      complete(HttpEntity.Default(ContentTypes.`text/html(UTF-8)`, data.length, Source.single(data)))
    }
  }


  private def handleAssets(webSubfolder: String) = {
    pathPrefix(webSubfolder) {
      get {
        encodeResponse {
          noCache {
            extractRequest { matched =>
              logger.debug(s"Try to get static data from $webSubfolder for:  ${matched.uri.path}.")
              getFromResourceDirectory(s"web/$webSubfolder")
            }
          }
        }
      }
    }
  }

  private def noCache = respondWithHeader(`Cache-Control`(List(CacheDirectives.public, CacheDirectives.`max-age`(0))))

  private def withUsageStatisticsReporting(html: String) = {
    if(usageStatisticsReports.enabled) html.replace("</body>", s"""<img src="${UsageStatisticsUrl(usageStatisticsReports.fingerprint, BuildInfo.version)}" alt="" hidden /></body>""")
    else html
  }

}
