package pl.touk.nussknacker.engine.api.dict

import cats.data.Validated
import pl.touk.nussknacker.engine.api.dict.DictRegistry._

/**
 * Provide operations on key/label for dictionaries. For some dictionaries, resolving key/label can be not supported (None will be returned).
 */
trait DictRegistry {

  /**
   * Returns key if exists matching label in dictionary with id = dictId
   */
  def keyByLabel(dictId: String, label: String): Validated[DictLookupError, String]

  /**
   * Returns Some(label) if exists matching key in dictionary with id = dictId
   * Returns None if registry not support fetching of label for given dictId or when there is no entry with given key.
   * It should be loose because we need to give user ability to fix key in process when dictionary will change possible values.
   */
  def labelByKey(dictId: String, key: String): Validated[DictNotDeclared, Option[String]]

  def toEngineRegistry: EngineDictRegistry = new EngineDictRegistry {
    override def labelByKey(dictId: String, label: String): Validated[DictNotDeclared, Option[String]] =
      DictRegistry.this.labelByKey(dictId, label)
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

  // DictEntryWithKeyNotExists not exists because on this side of transformation we always need to be loose - see DictRegistry.labelByKey

}
