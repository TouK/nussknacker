package pl.touk.nussknacker.engine.process.typeinformation.internal

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.java.typeutils.ListTypeInfo
import pl.touk.nussknacker.engine.api.{Context, ContextId, ContextIdTransformation}
import pl.touk.nussknacker.engine.flink.api.typeinfo.option.OptionTypeInfo
import pl.touk.nussknacker.engine.flink.typeinformation.{ConcreteCaseClassTypeInfo, FixedValueTypeInformationHelper}

object ContextTypeHelpers {

  private def infoFromVariablesAndParent(
      variables: TypeInformation[Map[String, Any]],
      parentCtx: TypeInformation[Option[Context]]
  ): TypeInformation[Context] =
    ConcreteCaseClassTypeInfo(
      ("id", contextIdInfo),
      ("variables", variables),
      ("parentContext", parentCtx)
    )

  def infoFromVariablesAndParentOption(
      variables: TypeInformation[Map[String, Any]],
      parentOpt: Option[TypeInformation[Context]]
  ): TypeInformation[Context] = {
    val parentCtx = new OptionTypeInfo[Context, Option[Context]](
      parentOpt.getOrElse(FixedValueTypeInformationHelper.nullValueTypeInfo)
    )
    infoFromVariablesAndParent(variables, parentCtx)
  }

  private def contextIdInfo: TypeInformation[ContextId] = {
    ConcreteCaseClassTypeInfo(
      ("scenarioId", TypeInformation.of(classOf[String])),
      ("originatingNodeId", TypeInformation.of(classOf[String])),
      ("taskId", TypeInformation.of(classOf[Long])),
      ("index", TypeInformation.of(classOf[Long])),
      ("transformations", new ListTypeInfo[ContextIdTransformation](contextIdTransformationInfo)),
    )
  }

  private def contextIdTransformationInfo: TypeInformation[ContextIdTransformation] = {
    ConcreteCaseClassTypeInfo(
      ("nodeId", TypeInformation.of(classOf[String])),
      ("transformation", TypeInformation.of(classOf[String])),
    )
  }

//  class ScalaListTypeInfo[T](elementTypeInfo: TypeInformation[T]) extends TypeInformation[util.List[T]] {
//
//    private val underlying = new ListTypeInfo[T](elementTypeInfo)
//
//    override def isBasicType: Boolean = underlying.isBasicType
//
//    override def isTupleType: Boolean = underlying.isTupleType
//
//    override def getArity: Int = underlying.getArity
//
//    override def getTotalFields: Int = underlying.getTotalFields
//
//    override def getTypeClass: Class[java.util.List[T]] = underlying.getTypeClass
//
//    override def isKeyType: Boolean = underlying.isKeyType
//
//    override def createSerializer(config: SerializerConfig): TypeSerializer[List[T]] = {
//      val elementTypeSerializer: TypeSerializer[T] = elementTypeInfo.createSerializer(config)
//      new ScalaListSerializer[T](elementTypeSerializer)
//    }
//
//    override def createSerializer(config: ExecutionConfig): TypeSerializer[List[T]] = {
//      createSerializer(config.getSerializerConfig)
//    }
//
//    override def canEqual(obj: Any): Boolean = underlying.canEqual(obj)
//
//    override def toString: String = underlying.toString
//
//    override def equals(obj: Any): Boolean = underlying.equals(obj)
//
//    override def hashCode(): Int = underlying.hashCode()
//
//  }
//
//  class ScalaListSerializer[T](typeSerializer: TypeSerializer[T]) extends TypeSerializer[List[T]] {
//
//    private val underlying = new ListSerializer[T](typeSerializer)
//
//    override def isImmutableType: Boolean = underlying.isImmutableType
//
//    override def duplicate(): TypeSerializer[util.List[T]] = underlying.duplicate()
//
//    override def createInstance(): util.List[T] = underlying.createInstance()
//
//    override def copy(from: List[T]): java.util.List[T] = {
//      println("AAAAAAAAAAAAAAAAA")
//      val f = from match {
//        case Nil => java.util.List.of[T]()
//        case other => other
//      }
//      val newList = new java.util.ArrayList[T](f.size())
//      val it = f.iterator()
//      while (it.hasNext) {
//        val element = it.next()
//        newList.add(typeSerializer.copy(element))
//      }
//      newList
//    }
//
//    override def copy(from: util.List[T], reuse: util.List[T]): util.List[T] = copy(from)
//
//    override def getLength: Int = underlying.getLength
//
//    override def serialize(record: util.List[T], target: DataOutputView): Unit = underlying.serialize(record, target)
//
//    override def deserialize(source: DataInputView): util.List[T] = underlying.deserialize(source)
//
//    override def deserialize(reuse: util.List[T], source: DataInputView): util.List[T] =
//      underlying.deserialize(reuse, source)
//
//    override def copy(source: DataInputView, target: DataOutputView): Unit = underlying.copy(source, target)
//
//    override def snapshotConfiguration(): TypeSerializerSnapshot[util.List[T]] = underlying.snapshotConfiguration()
//
//    override def toString: String = underlying.toString
//
//    override def equals(obj: Any): Boolean = underlying.equals(obj)
//
//    override def hashCode(): Int = underlying.hashCode()
//
//  }

}
