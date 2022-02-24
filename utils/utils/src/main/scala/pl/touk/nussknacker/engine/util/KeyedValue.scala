package pl.touk.nussknacker.engine.util

case class KeyedValue[+K, +V](key: K, value: V) {

  def tupled: (K, V) = (key, value)

  def mapKey[NK](f: K => NK): KeyedValue[NK, V] =
    copy(key = f(key))

  def mapValue[NV](f: V => NV): KeyedValue[K, NV] =
    copy(value = f(value))

}
