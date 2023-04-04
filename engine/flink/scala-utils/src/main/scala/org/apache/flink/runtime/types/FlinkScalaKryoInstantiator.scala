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
package org.apache.flink.runtime.types

import _root_.java.io.Serializable
import com.twitter.chill._
/*
This code is copied as is from Twitter Chill 0.7.4 because we need to user a newer chill version
but want to ensure that the serializers that are registered by default stay the same.
The only changes to the code are those that are required to make it compile and pass checkstyle
checks in our code base.
 */

/**
  * This class has a no-arg constructor, suitable for use with reflection instantiation It has no
  * registered serializers, just the standard Kryo configured for Kryo.
  */
class EmptyFlinkScalaKryoInstantiator extends KryoInstantiator {
  override def newKryo = {
    val k = new KryoBase
    k.setRegistrationRequired(false)
    k.setInstantiatorStrategy(new org.objenesis.strategy.StdInstantiatorStrategy)

    // Handle cases where we may have an odd classloader setup like with libjars
    // for hadoop
    val classLoader = Thread.currentThread.getContextClassLoader
    k.setClassLoader(classLoader)

    k
  }
}

object FlinkScalaKryoInstantiator extends Serializable {
  private val mutex = new AnyRef with Serializable // some serializable object
  @transient private var kpool: KryoPool = null

  /** Return a KryoPool that uses the FlinkScalaKryoInstantiator */
  def defaultPool: KryoPool = mutex.synchronized {
    if (null == kpool) {
      kpool = KryoPool.withByteArrayOutputStream(guessThreads, new FlinkScalaKryoInstantiator)
    }
    kpool
  }

  private def guessThreads: Int = {
    val cores = Runtime.getRuntime.availableProcessors
    val GUESS_THREADS_PER_CORE = 4
    GUESS_THREADS_PER_CORE * cores
  }
}

/** Makes an empty instantiator then registers everything */
class FlinkScalaKryoInstantiator extends EmptyFlinkScalaKryoInstantiator {
  override def newKryo = {
    val k = super.newKryo
    val reg = new AllScalaRegistrar
    reg(k)
    val javaWrapperScala2_13Registrar = new JavaWrapperScala2_13Registrar
    javaWrapperScala2_13Registrar(k)
    k
  }
}


// In Scala 2.13 all java collections class wrappers were rewritten from case class to regular class. Now kryo does not
// serialize them properly, so this class was added to fix this issue. It might not be needed in the future, when flink
// or twitter-chill updates kryo.
class JavaWrapperScala2_13Registrar extends IKryoRegistrar {
  def apply(newK: Kryo): Unit = {
    newK.register(JavaWrapperScala2_13Serializers.mapSerializer.wrapperClass, JavaWrapperScala2_13Serializers.mapSerializer)
    newK.register(JavaWrapperScala2_13Serializers.setSerializer.wrapperClass, JavaWrapperScala2_13Serializers.setSerializer)
    newK.register(JavaWrapperScala2_13Serializers.listSerializer.wrapperClass, JavaWrapperScala2_13Serializers.listSerializer)
  }
}
