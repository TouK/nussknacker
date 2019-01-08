package pl.touk.nussknacker.restmodel

import argonaut.{EncodeJson, _}
import argonaut.derive.{DerivedInstances, JsonSumCodec, JsonSumCodecFor, SingletonInstances}
import pl.touk.nussknacker.engine.api.{TypeSpecificData, UserDefinedProcessAdditionalFields}
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.definition.TestingCapabilities
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node
import pl.touk.nussknacker.engine.graph.node.NodeData
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.engine.util.json.Codecs
import pl.touk.nussknacker.restmodel.displayedgraph.{DisplayableProcess, ProcessProperties, ValidatedDisplayableProcess}
import pl.touk.nussknacker.restmodel.displayedgraph.displayablenode.{Edge, EdgeType, NodeAdditionalFields, ProcessAdditionalFields}
import pl.touk.nussknacker.restmodel.processdetails.{ProcessDetails, ProcessHistoryEntry}
import pl.touk.nussknacker.restmodel.validation.ValidationResults
import pl.touk.nussknacker.restmodel.validation.ValidationResults.{NodeValidationErrorType, ValidationResult}

trait RestModelCodecs extends Codecs with Argonauts with SingletonInstances with DerivedInstances {

  import pl.touk.nussknacker.engine.api.typed.TypeEncoders._

  private implicit def typeFieldJsonSumCodecFor[S]: JsonSumCodecFor[S] =
    JsonSumCodecFor(JsonSumCodec.typeField)

  //not sure why it works, another argonaut issue...
  implicit def typeCodec: CodecJson[TypeSpecificData] = new ProcessMarshaller().typeSpecificEncoder
  
  //argonaut does not like covariation so wee need to cast
  implicit def nodeAdditionalFieldsOptCodec: CodecJson[Option[node.UserDefinedAdditionalNodeFields]] = {
    CodecJson.derived[Option[NodeAdditionalFields]]
      .asInstanceOf[CodecJson[Option[node.UserDefinedAdditionalNodeFields]]]
  }

  implicit def processAdditionalFieldsOptCodec: CodecJson[Option[UserDefinedProcessAdditionalFields]] = {
    CodecJson.derived[Option[ProcessAdditionalFields]]
      .asInstanceOf[CodecJson[Option[UserDefinedProcessAdditionalFields]]]
  }

  implicit def testingCapabilitiesCodec: CodecJson[TestingCapabilities] = CodecJson.derive[TestingCapabilities]

  implicit def propertiesCodec: CodecJson[ProcessProperties] = CodecJson.derive[ProcessProperties]

  implicit def validationResultEncode: CodecJson[ValidationResult] = CodecJson.derive[ValidationResult]

  //fixme how to do this automatically?
  implicit def edgeTypeEncode: EncodeJson[EdgeType] = EncodeJson[EdgeType] {
    case EdgeType.FilterFalse => jObjectFields("type" -> jString("FilterFalse"))
    case EdgeType.FilterTrue => jObjectFields("type" -> jString("FilterTrue"))
    case EdgeType.SwitchDefault => jObjectFields("type" -> jString("SwitchDefault"))
    case ns: EdgeType.NextSwitch => jObjectFields("type" -> jString("NextSwitch"), "condition" -> ns.condition.asJson)
    case EdgeType.SubprocessOutput(name) => jObjectFields("type" -> jString("SubprocessOutput"), "name" -> name.asJson)
  }

  implicit def edgeTypeDecode: DecodeJson[EdgeType] = DecodeJson[EdgeType] { c =>
    for {
      edgeType <- (c --\ "type").as[String]
      edgeTypeObj <- {
        if (edgeType == "FilterFalse") DecodeResult.ok(EdgeType.FilterFalse)
        else if (edgeType == "FilterTrue") DecodeResult.ok(EdgeType.FilterTrue)
        else if (edgeType == "SwitchDefault") DecodeResult.ok(EdgeType.SwitchDefault)
        else if (edgeType == "NextSwitch") (c --\ "condition").as[Expression].map(condition => EdgeType.NextSwitch(condition))
        else if (edgeType == "SubprocessOutput") (c --\ "name").as[String].map(name => EdgeType.SubprocessOutput(name))

        else throw new IllegalArgumentException(s"Unknown edge type: $edgeType")
      }
    } yield edgeTypeObj
  }

  implicit val processTypeCodec: CodecJson[ProcessType.Value] = Codecs.enumCodec(ProcessType)

  implicit def displayableProcessCodec: CodecJson[DisplayableProcess] = CodecJson.derive[DisplayableProcess]

  implicit def validatedDisplayableProcessCodec: CodecJson[ValidatedDisplayableProcess] = CodecJson.derive[ValidatedDisplayableProcess]

  //FIXME: what should we do here? not always we have classs!
  implicit val typingResultDummyDecode: DecodeJson[TypingResult] = DecodeJson(_ => DecodeResult.ok(typing.Unknown))

  implicit val nodeErrorsCodec: CodecJson[ValidationResults.NodeValidationErrorType.Value] = Codecs.enumCodec(NodeValidationErrorType)

  implicit def processHistoryEncode: EncodeJson[ProcessHistoryEntry] = EncodeJson.derive[ProcessHistoryEntry]

  //TODO: this is here to make UiCodecsSpec work. EncodeJson.derive doesn't work, and we need implicit, otherwise lists are encoded as case classes
  val encodeNodeData: EncodeJson[NodeData] = EncodeJson.of[NodeData]

}