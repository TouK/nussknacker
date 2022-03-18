package pl.touk.nussknacker.engine.flink.util.source

import org.apache.flink.api.common.functions.RuntimeContext
import org.apache.flink.runtime.state.FunctionInitializationContext
import org.apache.flink.streaming.api.functions.source.datagen.{DataGenerator, DataGeneratorSource}

class ElementsEmitingControllableFunction[T](generator: DataGenerator[T], rowsPerSecond: Long = 1, numberOfRows: Long = 1) extends DataGeneratorSource[T](generator, rowsPerSecond, numberOfRows) {}

case class SimpleDataGenerator[T](data: List[T]) extends DataGenerator[T] {

  override def open(name: String, context: FunctionInitializationContext, runtimeContext: RuntimeContext): Unit = {}

  override def hasNext: Boolean = true

  override def next(): T = data.iterator.next()
}