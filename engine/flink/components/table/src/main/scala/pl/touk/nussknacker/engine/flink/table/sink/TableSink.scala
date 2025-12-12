package pl.touk.nussknacker.engine.flink.table.sink

import org.apache.flink.api.common.functions.{OpenContext, RichFlatMapFunction, RuntimeContext}
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.java.typeutils.ResultTypeQueryable
import org.apache.flink.streaming.api.datastream.{DataStream, DataStreamSink}
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment
import org.apache.flink.table.types.logical.RowType
import org.apache.flink.types.Row
import org.apache.flink.util.Collector
import pl.touk.nussknacker.engine.api.{Context, LazyParameter, NodeId, ValueWithContext}
import pl.touk.nussknacker.engine.api.component.{ComponentType, NodeComponentInfo}
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.flink.api.FlinkEngineContextOps._
import pl.touk.nussknacker.engine.flink.api.exception.ExceptionHandler
import pl.touk.nussknacker.engine.flink.api.process.{FlinkCustomNodeContext, FlinkSink}
import pl.touk.nussknacker.engine.flink.api.typeinformation.TypeInformationDetection
import pl.touk.nussknacker.engine.flink.table.TableDefinition
import pl.touk.nussknacker.engine.flink.table.definition.FlinkDataDefinition
import pl.touk.nussknacker.engine.flink.table.utils.DataTypesExtensions._
import pl.touk.nussknacker.engine.flink.table.utils.ToTableTypeSchemaBasedEncoder

import scala.annotation.nowarn

class TableSink(
    tableDefinition: TableDefinition,
    flinkDataDefinition: FlinkDataDefinition,
    value: LazyParameter[AnyRef]
) extends FlinkSink {

  override type Value = AnyRef

  override def prepareValue(
      dataStream: DataStream[Context],
      flinkNodeContext: FlinkCustomNodeContext
  ): DataStream[ValueWithContext[Value]] = {
    dataStream.flatMap(
      flinkNodeContext.lazyParameterHelper.lazyMapFunction(value),
      flinkNodeContext.valueWithContextInfo.forType(value.returnType)
    )
  }

  @nowarn("cat=deprecation")
  override def registerSink(
      dataStream: DataStream[ValueWithContext[Value]],
      flinkNodeContext: FlinkCustomNodeContext
  ): DataStreamSink[_] = {
    val env      = dataStream.getExecutionEnvironment
    val tableEnv = StreamTableEnvironment.create(env)
    flinkDataDefinition.registerIn(tableEnv)

    /*
      DataStream to Table transformation:
      1. Map the dataStream[any record type] to dataStream[Row] with table types alignment
      2. Map dataStream[Row] to Table
      3. Insert rows from the input table to sink table
      4. Put the insert operation in the statementSet and do attachAsDataStream on it
      5. Continue with a DiscardingSink as DataStream
     */
    val sinkRowType     = tableDefinition.schema.toSinkRowDataType.getLogicalType.toRowTypeUnsafe
    val streamOfRows    = dataStream.flatMap(EncodeAsTableTypeFunction(flinkNodeContext, value.returnType, sinkRowType))
    val inputValueTable = tableEnv.fromDataStream(streamOfRows)

    val statementSet = tableEnv.createStatementSet()
    statementSet.add(inputValueTable.insertInto(tableDefinition.tableId.toString))
    statementSet.attachAsDataStream()

    /*
      Flink docs show something like this when integrating table api with inserts into dataStream. For details read:
      https://nightlies.apache.org/flink/flink-docs-master/docs/dev/table/data_stream_api/.
     */
    // FIXME abr Variant with SinkV2 causes java.math.BigDecimal cannot be cast to class java.lang.Integer in tests
    dataStream.addSink(new org.apache.flink.streaming.api.functions.sink.DiscardingSink[ValueWithContext[AnyRef]]())
//    dataStream.sinkTo(new org.apache.flink.streaming.api.functions.sink.v2.DiscardingSink[ValueWithContext[AnyRef]]())
  }

}

class EncodeAsTableTypeFunction private (
    exceptionHandlerPreparer: RuntimeContext => ExceptionHandler,
    nodeId: NodeId,
    sinkRowType: RowType,
    producedType: TypeInformation[Row]
) extends RichFlatMapFunction[ValueWithContext[AnyRef], Row]
    with ResultTypeQueryable[Row] {

  @transient private var exceptionHandler: ExceptionHandler = _

  override def open(openContext: OpenContext): Unit = {
    super.open(openContext)
    exceptionHandler = exceptionHandlerPreparer(getRuntimeContext)
  }

  override def flatMap(valueWithContext: ValueWithContext[AnyRef], out: Collector[Row]): Unit = {
    exceptionHandler
      .handling(Some(NodeComponentInfo(nodeId, ComponentType.Sink, "table")), valueWithContext.context) {
        ToTableTypeSchemaBasedEncoder.encodeAsRow(valueWithContext.value, sinkRowType)
      }
      .foreach(out.collect)
  }

  override def getProducedType: TypeInformation[Row] = producedType

  override def close(): Unit = {
    if (exceptionHandler != null) {
      exceptionHandler.close()
    }
    super.close()
  }

}

object EncodeAsTableTypeFunction {

  def apply(
      flinkNodeContext: FlinkCustomNodeContext,
      valueReturnType: TypingResult,
      sinkRowType: RowType
  ): EncodeAsTableTypeFunction = {
    val alignedType  = ToTableTypeSchemaBasedEncoder.alignTypingResult(valueReturnType, sinkRowType)
    val producedType = TypeInformationDetection.instance.forType[Row](alignedType)
    new EncodeAsTableTypeFunction(
      flinkNodeContext.exceptionHandlerPreparer.narrowToRuntimeCtx,
      flinkNodeContext.nodeId,
      sinkRowType,
      producedType
    )
  }

}
