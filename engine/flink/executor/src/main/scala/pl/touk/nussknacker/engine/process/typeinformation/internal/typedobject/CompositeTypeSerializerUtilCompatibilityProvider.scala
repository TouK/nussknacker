package pl.touk.nussknacker.engine.process.typeinformation.internal.typedobject

import org.apache.flink.api.common.typeutils.CompositeTypeSerializerUtil.IntermediateCompatibilityResult
import org.apache.flink.api.common.typeutils.{CompositeTypeSerializerUtil, TypeSerializer, TypeSerializerSnapshot}

trait CompositeTypeSerializerUtil {

  def constructIntermediateCompatibilityResult[T](
      newNestedSerializerSnapshots: Array[TypeSerializer[_]],
      oldNestedSerializerSnapshots: Array[TypeSerializerSnapshot[_]]
  ): IntermediateCompatibilityResult[T]

}

object DefaultCompositeTypeSerializerUtil extends CompositeTypeSerializerUtil {

  override def constructIntermediateCompatibilityResult[T](
      newNestedSerializerSnapshots: Array[TypeSerializer[_]],
      oldNestedSerializerSnapshots: Array[TypeSerializerSnapshot[_]]
  ): IntermediateCompatibilityResult[T] = {
    CompositeTypeSerializerUtil.constructIntermediateCompatibilityResult(
      newNestedSerializerSnapshots.map(_.snapshotConfiguration()),
      oldNestedSerializerSnapshots
    )
  }

}

trait CompositeTypeSerializerUtilCompatibilityProvider extends CompositeTypeSerializerUtil
