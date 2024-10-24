/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package pl.touk.nussknacker.engine.flink.api.typeinfo.caseclass

import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.typeutils._
import pl.touk.nussknacker.engine.flink.api.typeinfo.caseclass.ScalaCaseClassSerializer.lookupConstructor

import java.io.{ObjectInputStream, ObjectStreamClass}
import scala.reflect.runtime.universe
import scala.util.{Failure, Success, Try}

/**
 * This is a non macro-generated, concrete Scala case class serializer.
 *
 * <p>We need this serializer to replace the previously macro generated, anonymous
 * [[CaseClassSerializer]].
 */
@SerialVersionUID(1L)
class ScalaCaseClassSerializer[T <: Product](
    clazz: Class[T],
    scalaFieldSerializers: Array[TypeSerializer[_]]
) extends CaseClassSerializer[T](clazz, scalaFieldSerializers) {

  @transient
  private var constructor = lookupConstructor(clazz)

  override def createInstance(fields: Array[AnyRef]): T = {
    constructor(fields)
  }

  override def snapshotConfiguration(): TypeSerializerSnapshot[T] = {
    new ScalaCaseClassSerializerSnapshot[T](this)
  }

  /**
   * This method is required as long as java.io.ObjectStreamClass is used for serialization.
   * Look for {@link ObjectStreamClass#invokeReadObject(Object, ObjectInputStream) }
   **/
  private def readObject(in: ObjectInputStream): Unit = {
    in.defaultReadObject()
    constructor = lookupConstructor(clazz)
  }

}

object ScalaCaseClassSerializer extends LazyLogging {

  def lookupConstructor[T](cls: Class[T]): Array[AnyRef] => T = {
    val rootMirror  = universe.runtimeMirror(cls.getClassLoader)
    val classSymbol = rootMirror.classSymbol(cls)

    require(
      classSymbol.isStatic,
      s"""
         |The class ${cls.getSimpleName} is an instance class, meaning it is not a member of a
         |toplevel object, or of an object contained in a toplevel object,
         |therefore it requires an outer instance to be instantiated, but we don't have a
         |reference to the outer instance. Please consider changing the outer class to an object.
         |""".stripMargin
    )

    val primaryConstructorSymbol = classSymbol.toType
      .decl(universe.termNames.CONSTRUCTOR)
      .alternatives
      .collectFirst {
        case constructorSymbol: universe.MethodSymbol @unchecked if constructorSymbol.isPrimaryConstructor =>
          constructorSymbol
      }
      .head
      .asMethod

    val classMirror             = rootMirror.reflectClass(classSymbol)
    val constructorMethodMirror = classMirror.reflectConstructor(primaryConstructorSymbol)

    arr: Array[AnyRef] => {
      Try(constructorMethodMirror.apply(arr.toIndexedSeq: _*).asInstanceOf[T]) match {
        case Success(value) => value
        case Failure(exc) =>
          logger.warn(
            s"Casting info, " +
              s"class: ${cls.getName}," +
              s"constructorMethodMirror: ${constructorMethodMirror.getClass.getName}" +
              s"arr: ${arr.mkString("Array(", ", ", ")")}" +
              s"class symbol: ${classSymbol.getClass.getName}"
          )

          throw exc
      }
    }
  }

}
