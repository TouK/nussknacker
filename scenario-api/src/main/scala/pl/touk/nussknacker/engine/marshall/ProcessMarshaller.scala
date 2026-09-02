package pl.touk.nussknacker.engine.marshall

import cats.data.{NonEmptyList, Validated}
import io.circe.{Decoder, Encoder, Json, JsonObject}
import pl.touk.nussknacker.engine.api.CirceUtil
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode._
import pl.touk.nussknacker.engine.graph.node.{CustomNode, Filter, FragmentInput, NodeData, Split, Switch}

object ProcessMarshaller {

  import io.circe.generic.extras.semiauto._
  import pl.touk.nussknacker.engine.api.CirceUtil._

  private implicit val nodeDataEncoder: Encoder[NodeData] = deriveConfiguredEncoder

  private implicit val nodeDataDecoder: Decoder[NodeData] = CirceUtil.withNameFromIdFallback(deriveConfiguredDecoder)

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
      data      <- CirceUtil.withNameFromIdFallback(deriveConfiguredDecoder[Filter])
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
      data        <- CirceUtil.withNameFromIdFallback(deriveConfiguredDecoder[Switch])
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
      data  <- CirceUtil.withNameFromIdFallback(deriveConfiguredDecoder[Split])
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
      data  <- CirceUtil.withNameFromIdFallback(deriveConfiguredDecoder[FragmentInput])
      nexts <- Decoder.instance(j => Decoder[Map[String, List[CanonicalNode]]].tryDecode(j downField "outputs"))
    } yield Fragment(data, nexts)

  private lazy val customNodeWithOutputsEncode: Encoder[CustomNodeWithOutputs] =
    Encoder.instance[CustomNodeWithOutputs](custom =>
      Encoder[NodeData]
        .apply(custom.data)
        .mapObject(
          addFields(
            "outputs" -> Encoder[NonEmptyList[Output]].apply(custom.outputs)
          )
        )
    )

  // `List`, not `NonEmptyList`: an empty array is how an externally produced payload spells "no wired outputs",
  // and must not fail the whole scenario decode.
  private lazy val customNodeWithOutputsDecode: Decoder[CanonicalNode] =
    for {
      data    <- CirceUtil.withNameFromIdFallback(deriveConfiguredDecoder[CustomNode])
      outputs <- Decoder.instance(j => Decoder[List[Output]].tryDecode(j downField "outputs"))
    } yield CustomNodeWithOutputs(data, outputs)

  private implicit lazy val nodeEncode: Encoder[CanonicalNode] =
    Encoder.instance[CanonicalNode] {
      case flat: FlatNode                => flatNodeEncode(flat)
      case filter: FilterNode            => filterEncode(filter)
      case switch: SwitchNode            => switchEncode(switch)
      case split: SplitNode              => splitEncode(split)
      case fragment: Fragment            => fragmentEncode(fragment)
      case custom: CustomNodeWithOutputs => customNodeWithOutputsEncode(custom)
    }

  // order is important here! flatNodeDecode has to be the last
  // TODO: this can lead to difficult to debug errors, when e.g. fragment is incorrect it'll be parsed as flatNode...
  private lazy val fallbackChain: Decoder[CanonicalNode] =
    filterDecode or switchDecode or splitDecode or fragmentDecode or flatNodeDecode

  // A literal, not `classOf[CustomNode].getSimpleName`: the wire format must not follow a class rename. The encoder's
  // discriminator does come from the class name, so a rename desynchronizes the two sides and every fresh multi-output
  // node decodes back as a FlatNode - ProcessMarshallerSpec's round-trip test pins that at build time. With
  // `getSimpleName` a rename would keep tests green and surface only in production, on stored scenarios whose
  // `"CustomNode"` discriminator no NodeData decoder recognizes anymore.
  private val CustomNodeDiscriminator: String = "CustomNode"

  // Dispatched directly rather than through the `or` chain, so that a corrupt `outputs` subtree fails the decode
  // instead of degrading to a flat CustomNode and dropping the subtree silently. Fragments also carry `outputs`,
  // but their discriminator differs, so they stay on the fallbackChain.
  private implicit lazy val nodeDecode: Decoder[CanonicalNode] = Decoder.instance { c =>
    val hasOutputs   = c.downField("outputs").succeeded
    val isCustomNode = c.downField("type").as[String].contains(CustomNodeDiscriminator)
    if (hasOutputs && isCustomNode) customNodeWithOutputsDecode(c) else fallbackChain(c)
  }

  private implicit lazy val caseDecode: Decoder[Case] = deriveConfiguredDecoder

  private implicit lazy val caseEncode: Encoder[Case] = deriveConfiguredEncoder

  private implicit lazy val outputDecode: Decoder[Output] = deriveConfiguredDecoder

  private implicit lazy val outputEncode: Encoder[Output] = deriveConfiguredEncoder

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
