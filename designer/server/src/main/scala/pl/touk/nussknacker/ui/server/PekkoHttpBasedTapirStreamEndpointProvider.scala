package pl.touk.nussknacker.ui.server

import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.{Source, StreamConverters}
import org.apache.pekko.util.ByteString
import sttp.capabilities.Streams
import sttp.capabilities.pekko.PekkoStreams
import sttp.tapir.CodecFormat.OctetStream
import sttp.tapir.EndpointIO.StreamBodyWrapper
import sttp.tapir.{Codec, CodecFormat, DecodeResult, EndpointIO, EndpointInput, EndpointOutput, Schema, StreamBodyIO}

import java.io.InputStream
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

class PekkoHttpBasedTapirStreamEndpointProvider(implicit mat: Materializer) extends TapirStreamEndpointProvider {
  override def streamBodyEndpointInput: EndpointInput[InputStream]   = streamBodyIO
  override def streamBodyEndpointOutput: EndpointOutput[InputStream] = streamBodyIO

  private lazy val streamBodyIO: StreamBodyWrapper[Source[ByteString, Any], InputStream] = StreamBodyWrapper(
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
      new Codec[PekkoStreams.BinaryStream, InputStream, OctetStream] {

        override def rawDecode(source: Source[ByteString, Any]): DecodeResult[InputStream] = {
          val result = source.runWith(StreamConverters.asInputStream(FiniteDuration(5, TimeUnit.SECONDS)))
          DecodeResult.Value(result)
        }

        override def encode(is: InputStream): Source[ByteString, Any] = is.available() match {
          case 0 => Source.empty
          case _ => StreamConverters.fromInputStream(() => is)
        }

        override def schema: Schema[InputStream] = Schema.schemaForInputStream

        override def format: OctetStream = CodecFormat.OctetStream()
      }

  }

}
