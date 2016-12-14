package pl.touk.nussknacker.engine.spel;

/*
 * Copyright 2002-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.lang.reflect.Array;
import java.lang.reflect.Executable;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.TypeConverter;
import org.springframework.util.ClassUtils;

/**
 * Utility methods used by the reflection resolver code to discover the appropriate
 * methods/constructors and fields that should be used in expressions.
 *
 * @author Andy Clement
 * @author Juergen Hoeller
 * @since 3.0
 */
//this is code taken from org.springframework.expression.spel.support.ReflectionHelper
//to make it better optimized. Our goal is to make TypeDescriptor with null Annotation[] parameter - it's a bit more expensive to use
//we overload code in createTypeDescriptor method
class SpelEspReflectionHelper {

    /**
     * Takes an input set of argument values and converts them to the types specified as the
     * required parameter types. The arguments are converted 'in-place' in the input array.
     * @param converter the type converter to use for attempting conversions
     * @param arguments the actual arguments that need conversion
     * @param executable the target Method or Constructor
     * @param varargsPosition the known position of the varargs argument, if any
     * ({@code null} if not varargs)
     * @return {@code true} if some kind of conversion occurred on an argument
     * @throws EvaluationException if a problem occurs during conversion
     */
    static boolean convertArguments(TypeConverter converter, Object[] arguments, Executable executable,
                                    Integer varargsPosition) throws EvaluationException {

        boolean conversionOccurred = false;
        if (varargsPosition == null) {
            for (int i = 0; i < arguments.length; i++) {
                TypeDescriptor targetType = createTypeDescriptor(MethodParameter.forExecutable(executable, i));
                Object argument = arguments[i];
                arguments[i] = converter.convertValue(argument, TypeDescriptor.forObject(argument), targetType);
                conversionOccurred |= (argument != arguments[i]);
            }
        }
        else {
            // Convert everything up to the varargs position
            for (int i = 0; i < varargsPosition; i++) {
                TypeDescriptor targetType = createTypeDescriptor(MethodParameter.forExecutable(executable, i));
                Object argument = arguments[i];
                arguments[i] = converter.convertValue(argument, TypeDescriptor.forObject(argument), targetType);
                conversionOccurred |= (argument != arguments[i]);
            }
            MethodParameter methodParam = MethodParameter.forExecutable(executable, varargsPosition);
            if (varargsPosition == arguments.length - 1) {
                // If the target is varargs and there is just one more argument
                // then convert it here
                TypeDescriptor targetType = new TypeDescriptor(methodParam);
                Object argument = arguments[varargsPosition];
                TypeDescriptor sourceType = TypeDescriptor.forObject(argument);
                arguments[varargsPosition] = converter.convertValue(argument, sourceType, targetType);
                // Three outcomes of that previous line:
                // 1) the input argument was already compatible (ie. array of valid type) and nothing was done
                // 2) the input argument was correct type but not in an array so it was made into an array
                // 3) the input argument was the wrong type and got converted and put into an array
                if (argument != arguments[varargsPosition] &&
                        !isFirstEntryInArray(argument, arguments[varargsPosition])) {
                    conversionOccurred = true; // case 3
                }
            }
            else {
                // Convert remaining arguments to the varargs element type
                TypeDescriptor targetType = createTypeDescriptor(methodParam).getElementTypeDescriptor();
                for (int i = varargsPosition; i < arguments.length; i++) {
                    Object argument = arguments[i];
                    arguments[i] = converter.convertValue(argument, TypeDescriptor.forObject(argument), targetType);
                    conversionOccurred |= (argument != arguments[i]);
                }
            }
        }
        return conversionOccurred;
    }

    /**
     * Check if the supplied value is the first entry in the array represented by the possibleArray value.
     * @param value the value to check for in the array
     * @param possibleArray an array object that may have the supplied value as the first element
     * @return true if the supplied value is the first entry in the array
     */
    private static boolean isFirstEntryInArray(Object value, Object possibleArray) {
        if (possibleArray == null) {
            return false;
        }
        Class<?> type = possibleArray.getClass();
        if (!type.isArray() || Array.getLength(possibleArray) == 0 ||
                !ClassUtils.isAssignableValue(type.getComponentType(), value)) {
            return false;
        }
        Object arrayValue = Array.get(possibleArray, 0);
        return (type.getComponentType().isPrimitive() ? arrayValue.equals(value) : arrayValue == value);
    }

    //Nussknacker: this is substituted implementation of method
    private static TypeDescriptor createTypeDescriptor(MethodParameter methodParameter) {
        final ResolvableType resolvableType = ResolvableType.forMethodParameter(methodParameter);
        final Class<?> aClass = resolvableType.resolve(methodParameter.getParameterType());
        final TypeDescriptor typeDescriptor = new TypeDescriptor(resolvableType, aClass, null) {};
        return typeDescriptor;
    }
}

