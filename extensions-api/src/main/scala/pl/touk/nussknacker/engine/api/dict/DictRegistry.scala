package pl.touk.nussknacker.engine.api.dict

import cats.data.Validated
import pl.touk.nussknacker.engine.api.dict.DictRegistry._

/**
 * Provide operations on key/label for dictionaries. For some dictionaries, resolving key/label can be not supported (None will be returned).
 */
trait DictRegistry extends AutoCloseable {

  /**
   * Returns key if exists matching label in dictionary with id = dictId
   */
  def keyByLabel(dictId: String, label: String): Validated[DictLookupError, String]

  /**
   * Returns Some(label) if exists matching key in dictionary with id = dictId
   * Returns None if registry not support fetching of label for given dictId.
   */
  def labelByKey(dictId: String, key: String): Validated[DictLookupError, Option[String]]

  def toEngineRegistry: EngineDictRegistry = new EngineDictRegistry {
    override def labelByKey(dictId: String, label: String): Validated[DictLookupError, Option[String]] =
      DictRegistry.this.labelByKey(dictId, label)

    override def close(): Unit = {}
  }

}

trait EngineDictRegistry extends DictRegistry {

  final override def keyByLabel(dictId: String, label: String): Validated[DictLookupError, String] =
    throw new IllegalAccessException("DictRegistry.keyByLabel shouldn't be used on engine side")

}

object DictRegistry {

  sealed trait DictLookupError

  case class DictNotDeclared(dictId: String) extends DictLookupError

  case class DictEntryWithLabelNotExists(dictId: String, label: String, possibleLabels: Option[List[String]]) extends DictLookupError

  case class DictEntryWithKeyNotExists(dictId: String, key: String, possibleKeys: Option[List[String]]) extends DictLookupError

}
