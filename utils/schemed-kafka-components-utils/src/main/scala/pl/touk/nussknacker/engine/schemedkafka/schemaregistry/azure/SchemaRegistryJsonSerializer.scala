package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.azure

import com.azure.core.http.HttpHeaders
import com.azure.core.util.serializer.{CollectionFormat, JacksonAdapter, SerializerAdapter, SerializerEncoding}

import java.lang.reflect.Type
object SchemaRegistryJsonSerializer extends SerializerAdapter {

  private val adapter = JacksonAdapter.createDefaultSerializerAdapter

  override def serialize(obj: AnyRef, encoding: SerializerEncoding): String = {
    if (encoding ne SerializerEncoding.JSON) return adapter.serialize(obj, encoding)
    obj match {
      case str: String => str
      case _ => adapter.serialize(obj, encoding)
    }
  }

  override def serializeRaw(obj: AnyRef): String = adapter.serializeRaw(obj)

  override def serializeList(list: java.util.List[_], format: CollectionFormat): String =
    adapter.serializeList(list, format)

  override def deserialize[T](value: String, typ: Type, encoding: SerializerEncoding): T =
    adapter.deserialize[T](value, typ, encoding)

  override def deserialize[T](headers: HttpHeaders, typ: Type): T =
    adapter.deserialize[T](headers, typ)

}