package pl.touk.nussknacker.engine.marshall

import cats.data.Validated
import io.circe.{Decoder, Encoder, Json, JsonObject}
import pl.touk.nussknacker.engine.api.CirceUtil
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode._
import pl.touk.nussknacker.engine.graph.node.{Filter, FragmentInput, NodeData, Split, Switch}

object ProcessMarshaller {

  import io.circe.generic.extras.semiauto._
  import pl.touk.nussknacker.engine.api.CirceUtil._

  private implicit val nodeDataEncoder: Encoder[NodeData] = deriveConfiguredEncoder

  private implicit val nodeDataDecoder: Decoder[NodeData] = deriveConfiguredDecoder

  private implicit lazy val flatNodeEncode: Encoder[FlatNode] =
    Encoder.apply[NodeData].contramap[FlatNode](_.data)

  private def addFields(fields: (String, Json)*): JsonObject => JsonObject = obj => fields.foldLeft(obj)(_.+:(_))

  private lazy val flatNodeDecode: Decoder[CanonicalNode] =
    Decoder.apply[NodeData].map(FlatNode)

  private lazy val filterEncode: Encoder[FilterNode] =
    Encoder.instance[FilterNode](filter =>
      Encoder[NodeData]
        .apply(filter.data)
        .mapObject(
          addFields(
            "nextFalse" ->
              Encoder[List[CanonicalNode]].apply(filter.nextFalse)
          )
        )
    )

  private lazy val filterDecode: Decoder[CanonicalNode] =
    for {
      data      <- deriveConfiguredDecoder[Filter]
      nextFalse <- Decoder.instance(j => Decoder[List[CanonicalNode]].tryDecode(j.downField("nextFalse")))
    } yield FilterNode(data, nextFalse)

  private lazy val switchEncode: Encoder[SwitchNode] =
    Encoder.instance[SwitchNode](switch =>
      Encoder[NodeData]
        .apply(switch.data)
        .mapObject(
          addFields(
            "nexts"       -> Encoder[List[Case]].apply(switch.nexts),
            "defaultNext" -> Encoder[List[CanonicalNode]].apply(switch.defaultNext)
          )
        )
    )

  private lazy val switchDecode: Decoder[CanonicalNode] =
    for {
      data        <- deriveConfiguredDecoder[Switch]
      nexts       <- Decoder.instance(j => Decoder[List[Case]].tryDecode(j downField "nexts"))
      defaultNext <- Decoder.instance(j => Decoder[List[CanonicalNode]].tryDecode(j downField "defaultNext"))
    } yield SwitchNode(data, nexts, defaultNext)

  private implicit lazy val splitEncode: Encoder[SplitNode] =
    Encoder.instance[SplitNode](switch =>
      Encoder[NodeData]
        .apply(switch.data)
        .mapObject(
          addFields(
            "nexts" -> Encoder[List[List[CanonicalNode]]].apply(switch.nexts)
          )
        )
    )

  private lazy val splitDecode: Decoder[CanonicalNode] =
    for {
      data  <- deriveConfiguredDecoder[Split]
      nexts <- Decoder.instance(j => Decoder[List[List[CanonicalNode]]].tryDecode(j downField "nexts"))
    } yield SplitNode(data, nexts)

  private lazy val fragmentEncode: Encoder[Fragment] =
    Encoder.instance[Fragment](fragment =>
      Encoder[NodeData]
        .apply(fragment.data)
        .mapObject(
          addFields(
            "outputs" -> Encoder[Map[String, List[CanonicalNode]]].apply(fragment.outputs)
          )
        )
    )

  private lazy val fragmentDecode: Decoder[CanonicalNode] =
    for {
      data  <- deriveConfiguredDecoder[FragmentInput]
      nexts <- Decoder.instance(j => Decoder[Map[String, List[CanonicalNode]]].tryDecode(j downField "outputs"))
    } yield Fragment(data, nexts)

  private implicit lazy val nodeEncode: Encoder[CanonicalNode] =
    Encoder.instance[CanonicalNode] {
      case flat: FlatNode     => flatNodeEncode(flat)
      case filter: FilterNode => filterEncode(filter)
      case switch: SwitchNode => switchEncode(switch)
      case split: SplitNode   => splitEncode(split)
      case fragment: Fragment => fragmentEncode(fragment)
    }

  // order is important here! flatNodeDecode has to be the last
  // TODO: this can lead to difficult to debug errors, when e.g. fragment is incorrect it'll be parsed as flatNode...
  private implicit lazy val nodeDecode: Decoder[CanonicalNode] =
    filterDecode or switchDecode or splitDecode or fragmentDecode or flatNodeDecode

  private implicit lazy val caseDecode: Decoder[Case] = deriveConfiguredDecoder

  private implicit lazy val caseEncode: Encoder[Case] = deriveConfiguredEncoder

  implicit lazy val canonicalProcessEncoder: Encoder[CanonicalProcess] = deriveConfiguredEncoder

  implicit lazy val canonicalProcessDecoder: Decoder[CanonicalProcess] = deriveConfiguredDecoder

  def fromJsonUnsafe(jsonString: String): CanonicalProcess =
    fromJson(jsonString).valueOr(err => throw new IllegalArgumentException("Unmarshalling errors: " + err))

  def fromJsonUnsafe(json: Json): CanonicalProcess =
    fromJson(json).valueOr(err => throw new IllegalArgumentException("Unmarshalling errors: " + err))

  def fromJson(jsonString: String): Validated[String, CanonicalProcess] =
    Validated
      .fromEither(CirceUtil.decodeJson[Json](jsonString))
      .leftMap(_.getMessage)
      .andThen(fromJson)

  def fromJson(json: Json): Validated[String, CanonicalProcess] =
    Validated.fromEither(Decoder[CanonicalProcess].decodeJson(json)).leftMap(_.getMessage)

}
