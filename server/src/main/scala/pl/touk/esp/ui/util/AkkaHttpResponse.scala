package pl.touk.esp.ui.util

import akka.http.scaladsl.model.headers.ContentDispositionTypes
import akka.http.scaladsl.model.{HttpResponse, ResponseEntity, StatusCodes, headers}

object AkkaHttpResponse {
  def asFile(entity: ResponseEntity, fileName: String) = {
    HttpResponse(status = StatusCodes.OK, entity = entity,
      headers = List(headers.`Content-Disposition`(ContentDispositionTypes.attachment, Map("filename" -> fileName)))
    )
  }
}
