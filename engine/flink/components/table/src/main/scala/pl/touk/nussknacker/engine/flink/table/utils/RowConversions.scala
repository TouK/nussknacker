package pl.touk.nussknacker.engine.flink.table.utils

import io.circe.{Codec, Decoder}
import io.circe.syntax.EncoderOps
import org.apache.flink.api.common.typeinfo.{TypeInformation, Types}
import org.apache.flink.api.java.typeutils.RowTypeInfo
import org.apache.flink.types.Row
import pl.touk.nussknacker.engine.api.{Context, ContextId, ContextIdPathPart, TraceId}
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.flink.api.typeinformation.TypeInformationDetection
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap

object RowConversions {

  import scala.jdk.CollectionConverters._

  def contextToRow(context: Context, validationContext: ValidationContext): Row = {
    val parentContextAsRow = for {
      parentValidationContext <- validationContext.parent
      parentContext           <- context.parentContext
    } yield contextToRow(parentContext, parentValidationContext)
    // Worth to notice that we serialize parent at the end
    val row          = Row.withPositions(parentContextAsRow.map(_ => 4).getOrElse(3))
    val variablesRow = encodeVariables(context.variables, validationContext)
    row.setField(0, serializeContextId(context.id))
    row.setField(1, variablesRow)
    row.setField(2, context.traceId.map(_.value).orNull)
    parentContextAsRow.foreach(row.setField(3, _))
    row
  }

  private def encodeVariables(variables: Map[String, Any], validationContext: ValidationContext): Row = {
    val row = Row.withNames()
    variables.foreach { case (variableName, variableValue) =>
      val encodedValue = validationContext
        .get(variableName)
        .map(ToTableTypeEncoder.encode(variableValue, _))
        .getOrElse(variableValue)
      row.setField(variableName, encodedValue)
    }
    row
  }

  def rowToContext(row: Row): Context = {
    def rowToScalaMap(row: Row): Map[String, AnyRef] = {
      val fieldNames = row.getFieldNames(true).asScala
      fieldNames.map { fieldName =>
        fieldName -> row.getField(fieldName)
      }.toMap
    }
    Context(
      deserializeContextId(row.getField(0).asInstanceOf[String]),
      rowToScalaMap(row.getField(1).asInstanceOf[Row]),
      // Order of serialization traceId and parent are replaced, because parent can contain many fields
      Option(row).filter(_.getArity >= 4).map(_.getField(3).asInstanceOf[Row]).map(rowToContext),
      Option(row.getField(2)).map(_.asInstanceOf[String]).map(TraceId(_))
    )
  }

  implicit class TypeInformationDetectionExtension(typeInformationDetection: TypeInformationDetection) {

    def contextRowTypeInfo(validationContext: ValidationContext): TypeInformation[_] = {
      val (fieldNames, typeInfos) =
        validationContext.localVariables
          .mapValuesNow(ToTableTypeEncoder.alignTypingResult)
          .mapValuesNow(typeInformationDetection.forType)
          .unzip
      val variablesRow = new RowTypeInfo(typeInfos.toArray[TypeInformation[_]], fieldNames.toArray)

      val parenTypeInfo = validationContext.parent.map(contextRowTypeInfo).toList

      Types.ROW(Types.STRING :: variablesRow :: Types.STRING :: parenTypeInfo: _*)
    }

  }

  private implicit def contextIdPathPartCodec: Codec[ContextIdPathPart] =
    Codec.forProduct2("n", "v")(ContextIdPathPart.apply)(t => (t.nodeId, t.value))

  private implicit def contextIdCodec: Codec[ContextId] =
    Codec.forProduct5("sn", "nid", "tid", "idx", "path")(
      (
          scenarioName: String,
          nodeId: String,
          taskId: Long,
          index: Long,
          path: List[ContextIdPathPart]
      ) => ContextId(scenarioName, nodeId, taskId, index, path),
    )(cid => (cid.scenarioName, cid.originatingNodeId, cid.taskId, cid.index, cid.path))

  private def serializeContextId(contextId: ContextId): String = {
    contextId.asJson.noSpaces
  }

  private def deserializeContextId(str: String): ContextId = {
    (for {
      json      <- io.circe.parser.parse(str)
      contextId <- Decoder[ContextId].decodeJson(json)
    } yield contextId) match {
      case Left(_) =>
        throw new IllegalStateException(s"Cannot deserialize contextId from [$str]")
      case Right(contextId) =>
        contextId
    }
  }

}
