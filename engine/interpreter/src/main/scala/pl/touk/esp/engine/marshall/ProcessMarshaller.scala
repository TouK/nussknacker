package pl.touk.esp.engine.marshall

import argonaut.{EncodeJson, PrettyParams, _}
import Argonaut._
import ArgonautShapeless._
import argonaut.derive._
import cats.data.Validated
import pl.touk.esp.engine.api.{MetaData, TypeSpecificData, UserDefinedProcessAdditionalFields}
import pl.touk.esp.engine.canonicalgraph.CanonicalProcess
import pl.touk.esp.engine.canonicalgraph.canonicalnode._
import pl.touk.esp.engine.canonize.ProcessCanonizer
import pl.touk.esp.engine.graph.{EspProcess, node}
import pl.touk.esp.engine.graph.node.{Case => _, FilterNode => _, SplitNode => _, SwitchNode => _, _}
import pl.touk.esp.engine.marshall.ProcessUnmarshallError._

class ProcessMarshaller(implicit
                        additionalNodeDataFieldsCodec: CodecJson[Option[node.UserDefinedAdditionalNodeFields]] = ProcessMarshaller.additionalNodeDataFieldsCodec,
                        additionalProcessFieldsCodec: CodecJson[Option[UserDefinedProcessAdditionalFields]] = ProcessMarshaller.additionalProcessFieldsCodec ) {

  //TODO: w sumie to potzebne w UI, ale tam cos mialem problemy z kompilacja... :|
  val typeSpecificEncoder =  CodecJson.derived[TypeSpecificData]

  private implicit def typeFieldJsonSumCodecFor[S]: JsonSumCodecFor[S] =
    JsonSumCodecFor(JsonSumCodec.typeField)

  private implicit lazy val flatNodeEncode: EncodeJson[FlatNode] =
    EncodeJson.of[NodeData].contramap[FlatNode](_.data)

  private lazy val flatNodeDecode: DecodeJson[CanonicalNode] =
    DecodeJson.of[NodeData].map(FlatNode)

  private implicit lazy val filterEncode: EncodeJson[FilterNode] =
    EncodeJson[FilterNode](filter =>
      EncodeJson.of[NodeData].encode(filter.data).withObject(_
        :+ "nextFalse" -> listOfCanonicalNodeEncoder.encode(filter.nextFalse)
      )
    )
  private lazy val filterDecode: DecodeJson[CanonicalNode] =
    for {
      data <- DecodeJson.of[Filter]
      nextFalse <- DecodeJson(j => listOfCanonicalNodeDecoder.tryDecode(j --\ "nextFalse"))
    } yield FilterNode(data, nextFalse)

  private implicit lazy val switchEncode: EncodeJson[SwitchNode] =
    EncodeJson[SwitchNode](switch =>
      EncodeJson.of[NodeData].encode(switch.data).withObject(_
        :+ "nexts" -> EncodeJson.of[List[Case]].encode(switch.nexts)
        :+ "defaultNext" -> listOfCanonicalNodeEncoder.encode(switch.defaultNext)
      )
    )

  private lazy val switchDecode: DecodeJson[CanonicalNode] =
    for {
      data <- DecodeJson.of[Switch]
      nexts <- DecodeJson(j => DecodeJson.of[List[Case]].tryDecode(j --\  "nexts"))
      defaultNext <- DecodeJson(j => listOfCanonicalNodeDecoder.tryDecode(j --\ "defaultNext"))
    } yield SwitchNode(data, nexts, defaultNext)

  private implicit lazy val splitEncode: EncodeJson[SplitNode] =
    EncodeJson[SplitNode](switch =>
      EncodeJson.of[NodeData].encode(switch.data).withObject(_
        :+ "nexts" -> EncodeJson.of[List[List[CanonicalNode]]].encode(switch.nexts)
      )
    )
  private lazy val splitDecode: DecodeJson[CanonicalNode] =
    for {
      data <- DecodeJson.of[Split]
      nexts <- DecodeJson(j => DecodeJson.of[List[List[CanonicalNode]]].tryDecode(j --\  "nexts"))
    } yield SplitNode(data, nexts)

  private lazy val subprocessEncode: EncodeJson[Subprocess] =
    EncodeJson[Subprocess](subprocess =>
      EncodeJson.of[NodeData].encode(subprocess.data).withObject(_
        :+ "outputs" -> EncodeJson.of[Map[String, List[CanonicalNode]]].encode(subprocess.outputs)
      )
    )

  private lazy val subprocessDecode: DecodeJson[CanonicalNode] =
    for {
      data <- DecodeJson.of[SubprocessInput]
      nexts <- DecodeJson(j => DecodeJson.of[Map[String, List[CanonicalNode]]].tryDecode(j --\  "outputs"))
    } yield Subprocess(data, nexts)


  private implicit lazy val nodeEncode: EncodeJson[CanonicalNode] =
    EncodeJson[CanonicalNode] {
      case flat: FlatNode => flatNodeEncode(flat)
      case filter: FilterNode => filterEncode(filter)
      case switch: SwitchNode => switchEncode(switch)
      case split: SplitNode => splitEncode(split)
      case subprocess: Subprocess => subprocessEncode(subprocess)

    }

  //order is important here! flatNodeDecode has to be the last
  private implicit lazy val nodeDecode: DecodeJson[CanonicalNode] =
  filterDecode ||| switchDecode ||| splitDecode||| subprocessDecode ||| flatNodeDecode

  // Without this nested lists were serialized to colon(head, tail) instead of json array
  private implicit lazy val listOfCanonicalNodeEncoder: EncodeJson[List[CanonicalNode]] = ListEncodeJson[CanonicalNode]
  private implicit lazy val listOfCanonicalNodeDecoder: DecodeJson[List[CanonicalNode]] = CanBuildFromDecodeJson[CanonicalNode, List]

  def toJson(node: EspProcess, prettyParams: PrettyParams) : String = {
    val canonical = ProcessCanonizer.canonize(node)
    toJson(canonical, prettyParams)
  }

  def toJson(canonical: CanonicalProcess, prettyParams: PrettyParams): String = {
    canonical.asJson.pretty(prettyParams.copy(dropNullKeys = true, preserveOrder = true))
  }

  def fromJson(json: String): Validated[ProcessJsonDecodeError, CanonicalProcess] = {
    Validated.fromEither(json.decodeEither[CanonicalProcess]).leftMap(ProcessJsonDecodeError)
  }

}

object ProcessMarshaller {
  def emptyOptionCodec[T]: CodecJson[Option[T]] = CodecJson.apply[Option[T]](_ => jEmptyObject, c => DecodeResult.ok(Option.empty[T]))

  val additionalNodeDataFieldsCodec: CodecJson[Option[node.UserDefinedAdditionalNodeFields]] = emptyOptionCodec

  val additionalProcessFieldsCodec: CodecJson[Option[UserDefinedProcessAdditionalFields]] = emptyOptionCodec
}