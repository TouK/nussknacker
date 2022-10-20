package org.apache.flink.runtime.types

import com.twitter.chill.{AllScalaRegistrar, KryoBase, KryoInstantiator, KryoPool}

import java.io.Serializable


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

/** Makes an empty instantiator then registers everything */
class FlinkScalaKryoInstantiator extends EmptyFlinkScalaKryoInstantiator {
  override def newKryo = {
    val k = super.newKryo
    val reg = new AllScalaRegistrar
    reg(k)
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