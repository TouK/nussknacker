package pl.touk.nussknacker.ui.api

import cats.implicits.toTraverseOps
import pl.touk.nussknacker.engine.api.component.ProcessingMode
import pl.touk.nussknacker.engine.api.definition.{FixedExpressionValue, ParameterEditor, SimpleParameterEditor}
import pl.touk.nussknacker.engine.api.deployment.{ProcessAction, ProcessActionId}
import pl.touk.nussknacker.engine.api.editor.DualEditorMode
import pl.touk.nussknacker.engine.api.expression.ExpressionTypingInfo
import pl.touk.nussknacker.engine.api.graph.{Edge, ProcessProperties, ScenarioGraph}
import pl.touk.nussknacker.engine.api.parameter.{ParameterValueCompileTimeValidation, ValueInputWithFixedValues}
import pl.touk.nussknacker.engine.api.{LayoutData, ProcessAdditionalFields}
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, ScenarioVersion, VersionId}
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.deployment.EngineSetupName
import pl.touk.nussknacker.engine.graph.EdgeType
import pl.touk.nussknacker.engine.graph.evaluatedparam.{BranchParameters, Parameter}
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
import pl.touk.nussknacker.restmodel.scenariodetails.ScenarioWithDetailsForMigrations
import pl.touk.nussknacker.restmodel.validation.ValidationResults.{
  NodeTypingData,
  NodeValidationError,
  UIGlobalError,
  ValidationErrors,
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

  object ExpressionCodec {
    implicit val expressionSchema: Schema[Expression] = Schema.derived
  }

  object ParameterValueCompileTimeValidationCodec {
    import ExpressionCodec._
    implicit val parameterValueCompileTimeValidationSchema: Schema[ParameterValueCompileTimeValidation] = Schema.derived
  }

  object ValueInputWithFixedValuesCodec {
    import FixedExpressionValueCodec._
    implicit val valueInputWithFixedValuesSchema: Schema[ValueInputWithFixedValues] = Schema.derived
  }

  object ProcessIdCodec {
    implicit val processIdSchema: Schema[ProcessId] = Schema.derived
  }

  object TypingResultCodec {
    implicit val typingResultSchema: Schema[TypingResult] = Schema.string // TODO: Type me
  }

  object ParameterEditorCodec {
    import ExpressionCodec._
    import FixedExpressionValueCodec._
    implicit val parameterEditorSchema: Schema[ParameterEditor]             = Schema.derived
    implicit val simpleParameterEditorSchema: Schema[SimpleParameterEditor] = Schema.anyObject // TODO: type me
    implicit val dualEditorSchema: Schema[DualEditorMode]                   = Schema.string
    implicit val timeSchema: Schema[java.time.temporal.ChronoUnit]          = Schema.anyObject
  }

  object UIParameterCodec {

    import TypingResultCodec._
    import ParameterEditorCodec._
    import ExpressionCodec._

    implicit lazy val uiParameterSchema: Schema[UIParameter] = Schema.derived
  }

  object ExpressionTypingInfoCodec {
    implicit val expressionTypingInfoSchema: Schema[ExpressionTypingInfo] = Schema.string // TODO: type me
  }

  object NodeTypingDataCodec {

    import TypingResultCodec._
    import UIParameterCodec._
    import ExpressionTypingInfoCodec._

    implicit val nodeTypingDataSchema: Schema[NodeTypingData] = Schema.derived
  }

  object NodeValidationErrorCodec {
    implicit val nodeValidationErrorSchema: Schema[NodeValidationError] = Schema.derived
  }

  object UIGlobalErrorCodec {
    import NodeValidationErrorCodec._
    implicit val uiGlobalErrorSchema: Schema[UIGlobalError] = Schema.derived
  }

  object ValidationWarningsCodec {
    import NodeValidationErrorCodec._
    implicit val validationWarningSchema: Schema[ValidationWarnings] = Schema.derived
  }

  object ValidationErrorsCodec {
    import NodeValidationErrorCodec._
    import UIGlobalErrorCodec._
    implicit val validationErrorsSchema: Schema[ValidationErrors] = Schema.derived
  }

  object ValidationResultCodec {

    import ValidationErrorsCodec._
    import ValidationWarningsCodec._
    import NodeTypingDataCodec._

    implicit val validationResultSchema: Schema[ValidationResult] = Schema.derived
  }

  object ProcessActionIdCodec {
    implicit val processActionIdSchema: Schema[ProcessActionId] = Schema.derived
  }

  object ProcessActionCodec {

    import ProcessActionIdCodec._
    import ProcessIdCodec._
    import VersionIdCodec._

    implicit val processActionSchema: Schema[ProcessAction] = Schema.derived
  }

  object ProcessPropertiesCodec {
    import ProcessAdditionalFieldsCodec._
    implicit val processPropertiesSchema: Schema[ProcessProperties] = Schema.derived
  }

  object BranchEndDataCodec {
    import BranchEndDefinitionCodec._
    implicit val branchEndDataSchema: Schema[BranchEndData] = Schema.derived
  }

  object BranchEndDefinitionCodec {
    implicit val branchEndDefinitionSchema: Schema[BranchEndDefinition] = Schema.derived
  }

  object NodeDataCodec {

    import BranchEndDataCodec._
    import ParameterCodec._
    import UserDefinedAdditionalNodeFieldsCodec._
    import ServiceRefCodec._
    import ExpressionCodec._
    import FragmentRefCodec._
    import FragmentParameterCodec._
    import FieldCodec._
    import FragmentOutputVarDefinitionCodec._
    import BranchParametersCodec._
    import SinkRefCodec._
    import SourceRefCodec._

    implicit val nodeDataSchema: Schema[NodeData] = Schema.derived
  }

  object ScenarioGraphCodec {

    import ProcessPropertiesCodec._
    import NodeDataCodec._
    import EdgeCodec._

    implicit val scenarioGraphSchema: Schema[ScenarioGraph] = Schema.derived
  }

  object EdgeCodec {
    import EdgeTypeCodec._
    implicit val edgeSchema: Schema[Edge] = Schema.derived
  }

  object ScenarioVersionCodec {
    import VersionIdCodec._
    import ProcessActionCodec._
    implicit val scenarioVersionSchema: Schema[ScenarioVersion] = Schema.derived
  }

  object EdgeTypeCodec {
    import ExpressionCodec._
    implicit val edgeTypeSchema: Schema[EdgeType] = Schema.derived
  }

  object ParameterCodec {
    import ExpressionCodec._
    implicit val parameterSchema: Schema[Parameter] = Schema.derived
  }

  object BranchParametersCodec {
    import ParameterCodec._
    implicit val branchParameterSchema: Schema[BranchParameters] = Schema.derived
  }

  object FragmentRefCodec {
    import ParameterCodec._
    implicit val fragmentRefSchema: Schema[FragmentRef] = Schema.derived
  }

  object UserDefinedAdditionalNodeFieldsCodec {
    import LayoutDataCodec._
    implicit val userDefinedAdditionalNodeFieldsSchema: Schema[UserDefinedAdditionalNodeFields] = Schema.derived
  }

  object SourceCodec {
    import SourceRefCodec._
    import UserDefinedAdditionalNodeFieldsCodec._
    implicit val sourceSchema: Schema[Source] = Schema.derived
  }

  object JoinCodec {

    import ParameterCodec._
    import BranchParametersCodec._
    import UserDefinedAdditionalNodeFieldsCodec._

    implicit val joinSchema: Schema[Join] = Schema.derived
  }

  object FragmentOutputVarDefinitionCodec {
    import FieldCodec._
    implicit val fragmentOutputDefinitionSchema: Schema[FragmentOutputVarDefinition] = Schema.derived
  }

  object FragmentParameterCodec {

    import FragmentClazzRefCodec._
    import FixedExpressionValueCodec._
    import ValueInputWithFixedValuesCodec._
    import ParameterValueCompileTimeValidationCodec._

    implicit val fragmentParameterSchema: Schema[FragmentParameter] = Schema.derived
  }

  object FragmentClazzRefCodec {
    implicit val fragmentClazzRefSchema: Schema[FragmentClazzRef] = Schema.string
  }

  object ServiceRefCodec {
    import ParameterCodec._
    implicit val serviceRefSchema: Schema[ServiceRef] = Schema.derived
  }

  object SinkRefCodec {
    import ParameterCodec._
    implicit val sinkRefSchema: Schema[SinkRef] = Schema.derived
  }

  object SourceRefCodec {
    import ParameterCodec._
    implicit val sourceRefSchema: Schema[SourceRef] = Schema.derived
  }

  object FieldCodec {
    import ExpressionCodec._
    implicit val fieldSchema: Schema[Field] = Schema.derived
  }

  object ProcessingModeCodec {
    implicit val processingModeSchema: Schema[ProcessingMode] = Schema.string
  }

  object EngineSetupNameCodec {
    implicit val engineSetupNameSchema: Schema[EngineSetupName] = Schema.string
  }

  object ProcessNameCodec {
    implicit val processNameSchema: Schema[ProcessName] = Schema.string
  }

  object ScenarioWithDetailsForMigrationsCodec {

    import ProcessNameCodec._
    import ScenarioGraphCodec._
    import ValidationResultCodec._
    import ScenarioVersionCodec._

    implicit val scenarioWithDetailsForMigrationsSchema: Schema[ScenarioWithDetailsForMigrations] = Schema.derived
  }

}
