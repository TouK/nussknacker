package pl.touk.nussknacker.restmodel

import argonaut.{EncodeJson, _}
import argonaut.derive.{DerivedInstances, JsonSumCodec, JsonSumCodecFor, SingletonInstances}
import io.circe.{Decoder, Encoder}
import pl.touk.nussknacker.engine.api.ArgonautCirce.toDecoder
import pl.touk.nussknacker.engine.api.{ProcessAdditionalFields, TypeSpecificData}
import pl.touk.nussknacker.engine.api.typed.{TypeEncoders, typing}
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.definition.TestingCapabilities
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node
import pl.touk.nussknacker.engine.graph.node.NodeData
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.engine.util.json.Codecs
import pl.touk.nussknacker.restmodel.displayedgraph.{DisplayableProcess, ProcessProperties, ValidatedDisplayableProcess}
import pl.touk.nussknacker.restmodel.displayedgraph.displayablenode.{Edge, EdgeType, NodeAdditionalFields}
import pl.touk.nussknacker.restmodel.processdetails.{DeploymentAction, DeploymentHistoryEntry, ProcessDetails, ProcessHistoryEntry}
import pl.touk.nussknacker.restmodel.validation.ValidationResults
import pl.touk.nussknacker.restmodel.validation.ValidationResults.{NodeValidationErrorType, ValidationResult}

object ArgonautRestModelCodecs extends Codecs with Argonauts with SingletonInstances with DerivedInstances {

  import pl.touk.nussknacker.engine.api.typed.TypeEncoders._

  private implicit def typeFieldJsonSumCodecFor[S]: JsonSumCodecFor[S] =
    JsonSumCodecFor(JsonSumCodec.typeField)

  //not sure why it works, another argonaut issue...
  implicit def typeCodec : CodecJson[TypeSpecificData] = new ProcessMarshaller().typeSpecificEncoder

  //FIXME: if I add type annotation here, everything breaks...
  implicit val nodeDataCodec = CodecJson.derived[NodeData]

  //argonaut does not like covariation so wee need to cast
  implicit def nodeAdditionalFieldsOptCodec: CodecJson[Option[node.UserDefinedAdditionalNodeFields]] = {
    CodecJson.derived[Option[NodeAdditionalFields]]
      .asInstanceOf[CodecJson[Option[node.UserDefinedAdditionalNodeFields]]]
  }

}

object DummyTypingResultDecoder {

  implicit val typingResultDecoder: Decoder[TypingResult] = Decoder.decodeJson.map(_ => typing.Unknown)

}

//TODO: hopefuly temporary
object CirceRestCodecs {

  import ArgonautRestModelCodecs._
  import DummyTypingResultDecoder.typingResultDecoder
  import io.circe.generic.semiauto._
  import pl.touk.nussknacker.engine.api.ArgonautCirce._

  implicit val nodeDataEncoder: Encoder[NodeData] = toEncoder(nodeDataCodec)

  implicit val nodeDataDecoder: Decoder[NodeData] = toDecoder(nodeDataCodec)

  implicit val validatedEncoder: Encoder[ValidatedDisplayableProcess] = deriveEncoder

  implicit val validatedDecoder: Decoder[ValidatedDisplayableProcess] = deriveDecoder

  implicit val displayableEncoder: Encoder[DisplayableProcess] = deriveEncoder

  implicit val displayableDecoder: Decoder[DisplayableProcess] = deriveDecoder


}