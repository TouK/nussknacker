package pl.touk.nussknacker.engine.flink.table.sink

import org.apache.flink.api.common.functions.{RichFlatMapFunction, RuntimeContext}
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.java.typeutils.ResultTypeQueryable
import org.apache.flink.streaming.api.datastream.{DataStream, DataStreamSink}
import org.apache.flink.streaming.api.functions.sink.DiscardingSink
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment
import org.apache.flink.table.types.logical.RowType
import org.apache.flink.types.Row
import org.apache.flink.util.Collector
import pl.touk.nussknacker.engine.api.component.{ComponentType, NodeComponentInfo}
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.api.{Context, LazyParameter, ValueWithContext}
import pl.touk.nussknacker.engine.flink.api.exception.{ExceptionHandler, WithExceptionHandler}
import pl.touk.nussknacker.engine.flink.api.process.{FlinkCustomNodeContext, FlinkSink}
import pl.touk.nussknacker.engine.flink.api.typeinformation.TypeInformationDetection
import pl.touk.nussknacker.engine.flink.table.TableDefinition
import pl.touk.nussknacker.engine.flink.table.extractor.SqlStatementReader.SqlStatement
import pl.touk.nussknacker.engine.flink.table.utils.ToTableTypeSchemaBasedEncoder
import pl.touk.nussknacker.engine.flink.table.utils.DataTypesExtensions._

class TableSink(
    tableDefinition: TableDefinition,
    sqlStatements: List[SqlStatement],
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

  override def registerSink(
      dataStream: DataStream[ValueWithContext[Value]],
      flinkNodeContext: FlinkCustomNodeContext
  ): DataStreamSink[_] = {
    val env      = dataStream.getExecutionEnvironment
    val tableEnv = StreamTableEnvironment.create(env)

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

    sqlStatements.foreach(tableEnv.executeSql)

    val statementSet = tableEnv.createStatementSet()
    statementSet.add(inputValueTable.insertInto(s"`${tableDefinition.tableName}`"))
    statementSet.attachAsDataStream()

    /*
      Flink docs show something like this when integrating table api with inserts into dataStream. For details read:
      https://nightlies.apache.org/flink/flink-docs-master/docs/dev/table/data_stream_api/.
     */
    dataStream.addSink(new DiscardingSink())
  }

}

class EncodeAsTableTypeFunction private (
    override protected val exceptionHandlerPreparer: RuntimeContext => ExceptionHandler,
    nodeId: String,
    sinkRowType: RowType,
    producedType: TypeInformation[Row]
) extends RichFlatMapFunction[ValueWithContext[AnyRef], Row]
    with ResultTypeQueryable[Row]
    with WithExceptionHandler {

  override def flatMap(valueWithContext: ValueWithContext[AnyRef], out: Collector[Row]): Unit = {
    exceptionHandler
      .handling(Some(NodeComponentInfo(nodeId, ComponentType.Sink, "table")), valueWithContext.context) {
        ToTableTypeSchemaBasedEncoder.encodeAsRow(valueWithContext.value, sinkRowType)
      }
      .foreach(out.collect)
  }

  override def getProducedType: TypeInformation[Row] = producedType

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
      flinkNodeContext.exceptionHandlerPreparer,
      flinkNodeContext.nodeId,
      sinkRowType,
      producedType
    )
  }

}
