package pl.touk.nussknacker.engine.lite.components.requestresponse.jsonschema.common.openapi

import io.circe.syntax._
import pl.touk.nussknacker.engine.api.ProcessAdditionalFields

object SwaggerOApiUtils {

  def prepareProcessDescription(additionalFields: Option[ProcessAdditionalFields]): String = {
      val descriptionOpt: List[(String, String)] = additionalFields
        .flatMap(_.description).map(a => List(("Description", a))).getOrElse(Nil)
      val properties = descriptionOpt
      properties.map(v => s"**${v._1}**: ${v._2}").mkString("\\\n")
  }

  def prepareDescription(runnerInfo: RunnerOshInfo): String = {
    val description: Map[String, String] = Map(
      "Confluence" -> s"<a href='${runnerInfo.confluenceUrl}' target='blank'>Confluence</a>",
      "Nussknacker" -> s"<a href='${runnerInfo.nkUrl}' target='blank'>Nussknacker</a>"
    )
    prettyPrintMap(description)
  }

  def prettyPrintMap(m: Map[String, String]): String = m.map(v => s"<b>${v._1}</b>: ${v._2}").mkString(" <br/> ")

  case class RunnerOshInfo(name: String, systemName: String, url: String, nkUrl: String, confluenceUrl: String)

}
