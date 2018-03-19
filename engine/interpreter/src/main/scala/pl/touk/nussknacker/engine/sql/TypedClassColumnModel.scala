package pl.touk.nussknacker.engine.sql

import java.lang.reflect.Member

import pl.touk.nussknacker.engine.api.process.{ClassExtractionSettings, ClassMemberPredicate}
import pl.touk.nussknacker.engine.api.typed.ClazzRef
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.definition.TypeInfos.ClazzDefinition
import pl.touk.nussknacker.engine.sql.CreateColumnModel.MapSqlType
import pl.touk.nussknacker.engine.types.EspTypeUtils

object TypedClassColumnModel {
  def create(typed: Typed): ColumnModel = {
    val claz = typed.possibleTypes.head.klass
    val definition = EspTypeUtils.clazzDefinition(claz)(classExtractionSettings(claz))
    getColumns(definition)
  }
  def unapply(typed: Typed): Some[ColumnModel] = {
    Some(create(typed))
  }

  def classExtractionSettings(claz: Class[_]) = ClassExtractionSettings(Seq(new CreateColumnClassExtractionPredicate(claz)))

  private[sql] def getColumns(clazzDefinition: ClazzDefinition): ColumnModel = {
    val typeClass: ClazzRef => Option[SqlType] = {
      case MapSqlType(t) => Some(t)
      case _ => None
    }
    val columns = clazzDefinition.methods
      .mapValues { m =>
        typeClass(m.refClazz)
      } collect { case (name, Some(typ)) =>
      Column(name, typ)
    }
    ColumnModel(columns.toList)
  }

  class CreateColumnClassExtractionPredicate(claz: Class[_]) extends ClassMemberPredicate {
    private val declaredFieldsNames = claz.getDeclaredFields.toList.map(_.getName)

    override def matches(member: Member): Boolean = {
      !declaredFieldsNames.contains(member.getName)
    }
  }

}
