package pl.touk.nussknacker.ui.util

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, Multipart}
import akka.http.scaladsl.model.Multipart.FormData
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import sttp.client3._
import sttp.model.{MediaType, Part}

import java.nio.charset.StandardCharsets
import scala.collection.immutable.Seq
import scala.concurrent.{ExecutionContext, Future}

object MultipartUtils {

  def readFile(byteSource: Source[ByteString, Any])(implicit ec: ExecutionContext, mat: Materializer): Future[String] =
    readFileBytes(byteSource).map(new String(_, StandardCharsets.UTF_8))

  def readFileBytes(
      byteSource: Source[ByteString, Any]
  )(implicit ec: ExecutionContext, mat: Materializer): Future[Array[Byte]] =
    byteSource.runWith(Sink.seq).map(_.flatten.toArray)

  def prepareMultiPart(content: String, name: String, fileName: String = "file.json"): FormData.Strict =
    Multipart.FormData(
      Multipart.FormData.BodyPart
        .Strict(name, HttpEntity(ContentTypes.`text/plain(UTF-8)`, content), Map("filename" -> fileName))
    )

  def prepareMultiParts(nameContent: (String, String)*)(fileName: String = "file.json"): FormData.Strict = {
    val bodyPart = nameContent.map { case (name, content) =>
      Multipart.FormData.BodyPart.Strict(
        name = name,
        entity = HttpEntity(ContentTypes.`text/plain(UTF-8)`, content),
        additionalDispositionParams = Map("filename" -> fileName)
      )
    }
    Multipart.FormData(bodyPart: _*)
  }

  def sttpPrepareMultiParts(
      nameContent: (String, String)*
  )(fileName: String = "file.json"): Seq[Part[RequestBody[Any]]] = {
    nameContent.toList
      .map { case (name, content) =>
        multipart(name, content)
          .fileName(fileName)
          .contentType(MediaType.TextPlainUtf8)
      }
  }

}
