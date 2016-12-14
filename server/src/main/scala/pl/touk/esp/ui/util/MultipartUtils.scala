package pl.touk.esp.ui.util

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, Multipart}
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString

import scala.concurrent.{ExecutionContext, Future}

object MultipartUtils {

  def readFile(byteSource: Source[ByteString, Any])(implicit ec: ExecutionContext, mat: Materializer): Future[String] =
    byteSource.runWith(Sink.seq).map(_.flatten.toArray).map(new String(_))

  def prepareMultiPart(content: String, name: String, fileName: String = "file.json") =
    Multipart.FormData(Multipart.FormData.BodyPart.Strict(name,
      HttpEntity(ContentTypes.`text/plain(UTF-8)`, content), Map("filename" -> fileName)))




}
