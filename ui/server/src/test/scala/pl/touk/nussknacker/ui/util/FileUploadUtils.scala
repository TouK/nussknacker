package pl.touk.nussknacker.ui.util

import akka.http.scaladsl.model.Multipart.FormData
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, Multipart}
import argonaut.PrettyParams

object FileUploadUtils {

  def prepareMultiPart(content: String, partName: String, fileName: String = "process.json") : FormData = {
    Multipart.FormData(Multipart.FormData.BodyPart.Strict(
        partName, HttpEntity(ContentTypes.`text/plain(UTF-8)`, content), Map("filename" -> fileName)))

  }

}
