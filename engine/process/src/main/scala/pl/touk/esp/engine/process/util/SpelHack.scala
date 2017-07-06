package pl.touk.esp.engine.process.util

import com.esotericsoftware.kryo.io.{Input, Output}
import com.esotericsoftware.kryo.{Kryo, Serializer}
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment

import scala.collection.JavaConversions._

//By default SpEL list (i.e "{'a', 'b'}") is represented as java.util.Collections.UnmodifiableCollection, which
//Kryo won't serialize properly since Kry uses java.util.Collection.add() method which in case of UnmodifiableCollection
//throws an exception. That's why we need out own serialization of java.util.List
object SpelHack extends Serializer[java.util.List[_]](false, true) with Serializable {

  override def write(kryo: Kryo, out: Output, obj: java.util.List[_]): Unit = {
    //Write the size:
    out.writeInt(obj.size(), true)
    val it = obj.iterator()
    while (it.hasNext) {
      kryo.writeClassAndObject(out, it.next())
      // After each intermediate object, flush
      out.flush()
    }

  }

  override def read(kryo: Kryo, in: Input, obj: Class[java.util.List[_]]): java.util.List[_] = {
    val size = in.readInt(true)
    // Go ahead and be faster, and not as functional cool, and be mutable in here
    var idx = 0
    val builder = List.canBuildFrom[AnyRef]()
    builder.sizeHint(size)

    while (idx < size) {
      val item = kryo.readClassAndObject(in)
      builder += item
      idx += 1
    }
    seqAsJavaList(builder.result())
  }

}
