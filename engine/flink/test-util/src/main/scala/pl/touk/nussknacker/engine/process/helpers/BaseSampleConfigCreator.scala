package pl.touk.nussknacker.engine.process.helpers

import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.typeinfo.TypeInformation
import pl.touk.nussknacker.engine.api.Service
import net.ceedubs.ficus.Ficus._
import pl.touk.nussknacker.engine.api.process.{ExpressionConfig, ProcessObjectDependencies, SourceFactory, WithCategories}
import pl.touk.nussknacker.engine.flink.util.source.CollectionSource
import pl.touk.nussknacker.engine.util.process.EmptyProcessConfigCreator
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.process.helpers.SampleNodes.MockService
import pl.touk.nussknacker.engine.util.functions.conversion

import scala.reflect.ClassTag
import scala.reflect.runtime.universe._

class BaseSampleConfigCreator[T: ClassTag: TypeTag : TypeInformation](sourceList: List[T]) extends EmptyProcessConfigCreator {

  override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory]]
  = Map(
    "source" -> WithCategories(SourceFactory.noParam[T](new CollectionSource[T](new ExecutionConfig, sourceList, None, Typed.fromDetailedType[T]))),
    "noopSource" -> WithCategories(SourceFactory.noParam[T](new CollectionSource[T](new ExecutionConfig, List.empty, None, Typed.fromDetailedType[T]))))

  override def services(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[Service]]
  = Map("mockService" -> WithCategories(new MockService))

  override def expressionConfig(processObjectDependencies: ProcessObjectDependencies): ExpressionConfig = {
    val categories = processObjectDependencies.config.getAs[List[String]]("categories").getOrElse("Default" :: Nil)
    def withCategories[T](obj: T): WithCategories[T] = WithCategories(obj, categories: _*)

    ExpressionConfig(
      Map(
        "CONV" -> withCategories(conversion),
      ),
      List()
    )
  }

}
