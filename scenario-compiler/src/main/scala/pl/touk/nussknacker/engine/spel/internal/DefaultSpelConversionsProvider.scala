package pl.touk.nussknacker.engine.spel.internal

import org.springframework.core.convert.TypeDescriptor
import org.springframework.core.convert.converter.{ConditionalConverter, Converter, ConverterFactory}
import org.springframework.core.convert.support.GenericConversionService
import org.springframework.util.NumberUtils
import pl.touk.nussknacker.engine.api.spel.SpelConversionsProvider
import pl.touk.nussknacker.engine.api.typed.TypeConversionHandler.{StringConversion, stringConversions}

/**
  * This class creates SpEL's ConversionService. We don't use DefaultConversionService because it has some conversions
  * (mainly between similar types) that would cause ambiguity e.g. ObjectToObjectConverter. Also this default service
  * is lack of conversions between String and java time API (ZoneId, ZoneOffset, etc.), it has only support for legacy
  * java Date/Calendar API
  */
object DefaultSpelConversionsProvider extends DefaultSpelConversionsProvider

class DefaultSpelConversionsProvider extends SpelConversionsProvider {

  override def getConversionService: GenericConversionService = {
    val service = new GenericConversionService
    service.addConverterFactory(new NumberToNumberConverterFactory())

    stringConversions.foreach { case conversion @ StringConversion(convert) =>
      service.addConverter(classOf[String], conversion.klass, (source: String) => convert(source))
    }

    // This is used only to prevent errors when calling function with
    // varArgs with exactly one argument.
    // TODO: Remove it once Spring is updated; it should work with version 5.3.22
    service.addConverter(new ObjectToArrayConverter(service))
    // For purpose of concise usage of numbers in spel templates
    service.addConverter(classOf[Number], classOf[String], (source: Number) => source.toString)
    service.addConverter(new ArrayToListConversionHandler.ArrayToListConverter(service))
    service
  }

}

class NumberToNumberConverterFactory extends ConverterFactory[Number, Number] with ConditionalConverter {
  override def matches(sourceType: TypeDescriptor, targetType: TypeDescriptor): Boolean = sourceType != targetType

  override def getConverter[T <: Number](targetType: Class[T]): Converter[Number, T] =
    (source: Number) => NumberUtils.convertNumberToTargetClass(source, targetType)
}
