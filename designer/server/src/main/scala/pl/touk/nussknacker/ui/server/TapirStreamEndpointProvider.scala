package pl.touk.nussknacker.ui.server

import akka.stream.Materializer
import akka.stream.scaladsl.{Source, StreamConverters}
import akka.util.ByteString
import sttp.capabilities.Streams
import sttp.capabilities.akka.AkkaStreams
import sttp.tapir.CodecFormat.OctetStream
import sttp.tapir.EndpointIO.StreamBodyWrapper
import sttp.tapir.{Codec, CodecFormat, DecodeResult, EndpointIO, Schema, StreamBodyIO}

import java.io.InputStream
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

trait TapirStreamEndpointProvider {
  def mat: Materializer

  lazy val streamBodyEndpointInOut: StreamBodyWrapper[Source[ByteString, Any], InputStream] = StreamBodyWrapper(
    StreamBodyIO(
      InputStreamStreams,
      BinaryStreamCodec.codec(mat),
      EndpointIO.Info.empty,
      None,
      Nil
    )
  )

  private trait InputStreamStreams extends Streams[InputStreamStreams] {
    override type BinaryStream = InputStream
  }

  private object InputStreamStreams extends InputStreamStreams

  private object BinaryStreamCodec {

    def codec(implicit mat: Materializer): Codec[Source[ByteString, Any], InputStream, OctetStream] =
      new Codec[AkkaStreams.BinaryStream, InputStream, OctetStream] {

        override def rawDecode(source: Source[ByteString, Any]): DecodeResult[InputStream] = {
          val result = source.runWith(StreamConverters.asInputStream(FiniteDuration(5, TimeUnit.SECONDS)))
          DecodeResult.Value(result)
        }

        override def encode(is: InputStream): Source[ByteString, Any] = StreamConverters.fromInputStream(() => is)

        override def schema: Schema[InputStream] = Schema.schemaForInputStream

        override def format: OctetStream = CodecFormat.OctetStream()
      }

  }

}
