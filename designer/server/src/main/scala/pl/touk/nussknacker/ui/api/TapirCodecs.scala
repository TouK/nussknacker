package pl.touk.nussknacker.ui.api

import cats.implicits.toTraverseOps
import pl.touk.nussknacker.engine.api.definition.{FixedExpressionValue, ParameterEditor}
import pl.touk.nussknacker.engine.api.deployment.{ProcessAction, ProcessActionId}
import pl.touk.nussknacker.engine.api.editor.DualEditorMode
import pl.touk.nussknacker.engine.api.expression.ExpressionTypingInfo
import pl.touk.nussknacker.engine.api.graph.{Edge, ProcessProperties, ScenarioGraph}
import pl.touk.nussknacker.engine.api.parameter.{ParameterValueCompileTimeValidation, ValueInputWithFixedValues}
import pl.touk.nussknacker.engine.api.{LayoutData, ProcessAdditionalFields}
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, ScenarioVersion, VersionId}
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.graph.EdgeType
import pl.touk.nussknacker.engine.graph.evaluatedparam.BranchParameters
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.fragment.FragmentRef
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition.{FragmentClazzRef, FragmentParameter}
import pl.touk.nussknacker.engine.graph.node.{
  BranchEndData,
  BranchEndDefinition,
  FragmentOutputVarDefinition,
  Join,
  NodeData,
  Source,
  UserDefinedAdditionalNodeFields
}
import pl.touk.nussknacker.engine.graph.service.ServiceRef
import pl.touk.nussknacker.engine.graph.sink.SinkRef
import pl.touk.nussknacker.engine.graph.source.SourceRef
import pl.touk.nussknacker.engine.graph.variable.Field
import pl.touk.nussknacker.restmodel.definition.UIParameter
import pl.touk.nussknacker.restmodel.validation.ValidationResults.{
  NodeTypingData,
  NodeValidationError,
  UIGlobalError,
  ValidationResult,
  ValidationWarnings
}
import pl.touk.nussknacker.ui.server.HeadersSupport.{ContentDisposition, FileName}
import sttp.tapir.Codec.PlainCodec
import sttp.tapir.CodecFormat.TextPlain
import sttp.tapir.{Codec, CodecFormat, DecodeResult, Schema}

object TapirCodecs {

  object ScenarioNameCodec {
    def encode(scenarioName: ProcessName): String = scenarioName.value

    def decode(s: String): DecodeResult[ProcessName] = {
      val scenarioName = ProcessName.apply(s)
      DecodeResult.Value(scenarioName)
    }

    implicit val scenarioNameCodec: PlainCodec[ProcessName] = Codec.string.mapDecode(decode)(encode)

    implicit val schema: Schema[ProcessName] = Schema.string

  }

  object VersionIdCodec {
    def encode(versionId: VersionId): Long = versionId.value

    def decode(value: Long): DecodeResult[VersionId] = {
      DecodeResult.Value(VersionId.apply(value))
    }

    implicit val versionIdCodec: PlainCodec[VersionId] = Codec.long.mapDecode(decode)(encode)

    implicit val schema: Schema[VersionId] = Schema.derived
  }

  object ContentDispositionCodec {

    implicit val requiredFileNameCodec: Codec[List[String], FileName, TextPlain] =
      new Codec[List[String], FileName, TextPlain] {

        override def rawDecode(headers: List[String]): DecodeResult[FileName] =
          (
            headers
              .map(h => ContentDisposition(h).fileName)
              .sequence
              .flatMap(_.headOption)
          ) match {
            case Some(value) => DecodeResult.Value(value)
            case None        => DecodeResult.Missing
          }

        override def encode(v: FileName): List[String] = ContentDisposition(Some(v)).headerValue().toList

        override def schema: Schema[FileName] = Schema.string[FileName]

        override def format: TextPlain = CodecFormat.TextPlain()
      }

  }

  object HeaderCodec {

    implicit val requiredHeaderCodec: Codec[List[String], String, TextPlain] =
      new Codec[List[String], String, TextPlain] {

        override def rawDecode(headers: List[String]): DecodeResult[String] =
          headers.headOption match {
            case Some(value) => DecodeResult.Value(value)
            case None        => DecodeResult.Missing
          }

        override def encode(v: String): List[String] = List(v)

        override def schema: Schema[String] = Schema.string

        override def format: TextPlain = CodecFormat.TextPlain()
      }

    implicit val optionalHeaderCodec: Codec[List[String], Option[String], TextPlain] =
      new Codec[List[String], Option[String], TextPlain] {

        override def rawDecode(headers: List[String]): DecodeResult[Option[String]] =
          DecodeResult.Value(headers.headOption)

        override def encode(v: Option[String]): List[String] = v.toList

        override def schema: Schema[Option[String]] = Schema.schemaForOption(Schema.schemaForString)

        override def format: TextPlain = CodecFormat.TextPlain()
      }

  }

  object LayoutDataCodec {
    implicit val schema: Schema[LayoutData] = Schema.derived
  }

  object ProcessAdditionalFieldsCodec {
    implicit val schema: Schema[ProcessAdditionalFields] = Schema.derived
  }

  object FixedExpressionValueCodec {
    implicit val schema: Schema[FixedExpressionValue] = Schema.derived
  }

  object ParameterValueCompileTimeValidationCodec {
    implicit val schema: Schema[ParameterValueCompileTimeValidation] = Schema.derived
  }

  object ValueInputWithFixedValuesCodec {
    implicit val schema: Schema[ValueInputWithFixedValues] = Schema.derived
  }

  object ProcessIdCodec {
    implicit val schema: Schema[ProcessId] = Schema.derived
  }

  object ExpressionCodec {
    implicit val schema: Schema[Expression] = Schema.derived
  }

  object TypingResultCodec {
    implicit val schema: Schema[TypingResult] = Schema.string // TODO: Type that properly
  }

  object UIParameterCodec {
    implicit lazy val schema: Schema[UIParameter] = Schema.derived
  }

  object ValidationResultsCodec {
    implicit val schema: Schema[NodeTypingData] = Schema.derived
  }

  object UIGlobalErrorCodec {
    implicit val schema: Schema[UIGlobalError] = Schema.derived
  }

  object NodeValidationErrorCodec {
    implicit val schema: Schema[NodeValidationError] = Schema.derived
  }

  object ValidationWarningsCodec {
    implicit val schema: Schema[ValidationWarnings] = Schema.derived
  }

  object ValidationResultCodec {
    implicit val schema: Schema[ValidationResult] = Schema.derived
  }

  object ProcessActionCodec {
    implicit val schema: Schema[ProcessAction] = Schema.derived
  }

  object ProcessActionIdCodec {
    implicit val schema: Schema[ProcessActionId] = Schema.derived
  }

  object ExpressionTypingInfoCodec {
    implicit val schema: Schema[ExpressionTypingInfo] = Schema.string // TODO: type me
  }

  object ScenarioGraphCodec {
    implicit val schema: Schema[ScenarioGraph] = Schema.derived
  }

  object EdgeCodec {
    implicit val schema: Schema[Edge] = Schema.derived
  }

  object ProcessPropertiesCodec {
    implicit val schema: Schema[ProcessProperties] = Schema.derived
  }

  object ScenarioVersionCodec {
    implicit val schema: Schema[ScenarioVersion] = Schema.derived
  }

  object EdgeTypeCodec {
    implicit val schema: Schema[EdgeType] = Schema.derived
  }

  object BranchParametersCodec {
    implicit val schema: Schema[BranchParameters] = Schema.derived
  }

  object FragmentRefCodec {
    implicit val schema: Schema[FragmentRef] = Schema.derived
  }

  object UserDefinedAdditionalNodeFieldsCodec {
    implicit val schema: Schema[UserDefinedAdditionalNodeFields] = Schema.derived
  }

  object NodeDataCodec {
    implicit val schema: Schema[NodeData] = Schema.derived
  }

  object SourceCodec {
    implicit val schema: Schema[Source] = Schema.derived
  }

  object JoinCodec {
    implicit val schema: Schema[Join] = Schema.derived
  }

  object BranchEndDataCodec {
    implicit val schema: Schema[BranchEndData] = Schema.derived
  }

  object BranchEndDefinitionCodec {
    implicit val schema: Schema[BranchEndDefinition] = Schema.derived
  }

  object FragmentOutputVarDefinitionCodec {
    implicit val schema: Schema[FragmentOutputVarDefinition] = Schema.derived
  }

  object FragmentParameterCodec {
    implicit val schema: Schema[FragmentParameter] = Schema.derived
  }

  object FragmentClazzRefCodec {
    implicit val schema: Schema[FragmentClazzRef] = Schema.string
  }

  object ServiceRefCodec {
    implicit val schema: Schema[ServiceRef] = Schema.derived
  }

  object SinkRefCodec {
    implicit val schema: Schema[SinkRef] = Schema.derived
  }

  object SourceRefCodec {
    implicit val schema: Schema[SourceRef] = Schema.derived
  }

  object FieldCodec {
    implicit val schema: Schema[Field] = Schema.derived
  }

}
