/**
 * Copyright (C) 2006-2018 Talend Inc. - www.talend.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.talend.sdk.component.runtime.manager.util;

import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.joining;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;

import org.talend.sdk.component.runtime.manager.ParameterMeta;

public class DefaultValueInspector {

    // for now we use instantiation to find the defaults assuming it will be cached
    // but we can move it later in design module to directly read it from the bytecode
    public Object createDemoInstance(final Object rootInstance, final ParameterMeta param) {
        if (rootInstance != null) {
            final Object field = findField(rootInstance, param);
            if (field != null) {
                return field;
            }
        }

        final Type javaType = param.getJavaType();
        if (Class.class.isInstance(javaType)) {
            return tryCreatingObjectInstance(javaType);
        } else if (ParameterizedType.class.isInstance(javaType)) {
            final ParameterizedType pt = ParameterizedType.class.cast(javaType);
            final Type rawType = pt.getRawType();
            if (Class.class.isInstance(rawType) && Collection.class.isAssignableFrom(Class.class.cast(rawType))
                    && pt.getActualTypeArguments().length == 1
                    && Class.class.isInstance(pt.getActualTypeArguments()[0])) {
                return tryCreatingObjectInstance(pt.getActualTypeArguments()[0]);
            }
        }
        return null;
    }

    public String findDefault(final Object instance, final ParameterMeta param) {
        if (instance == null) {
            return null;
        }
        final ParameterMeta.Type type = param.getType();
        switch (type) {
        case OBJECT:
            return null;
        case ENUM:
            return Enum.class.cast(instance).name();
        case STRING:
        case NUMBER:
        case BOOLEAN:
            return String.valueOf(instance);
        case ARRAY: // can be enhanced
            if (!param.getNestedParameters().isEmpty()) {
                return null;
            } else if (Collection.class.isInstance(instance)) {
                return ((Collection<Object>) instance).stream().map(String::valueOf).collect(joining(","));
            } else { // primitives
                return String.valueOf(instance);
            }
        default:
            throw new IllegalArgumentException("Unsupported type: " + param.getType());
        }
    }

    private Object findField(final Object rootInstance, final ParameterMeta param) {
        if (param.getPath().startsWith("$") || param.getName().startsWith("$")) { // virtual param
            return null;
        }
        Class<?> current = rootInstance.getClass();
        while (current != null) {
            try {
                final Field declaredField = current.getDeclaredField(findName(param));
                if (!declaredField.isAccessible()) {
                    declaredField.setAccessible(true);
                }
                return declaredField.get(rootInstance);
            } catch (final IllegalAccessException | NoSuchFieldException e) {
                // next
            }
            current = current.getSuperclass();
        }
        if (Collection.class.isInstance(rootInstance)) {
            final Collection<?> collection = Collection.class.cast(rootInstance);
            if (!collection.isEmpty()) {
                return findField(collection.iterator().next(), param);
            }
            return null;
        }
        throw new IllegalArgumentException("Didn't find field '" + param.getName() + "' in " + rootInstance);
    }

    private String findName(final ParameterMeta meta) {
        return ofNullable(meta.getSource()).map(ParameterMeta.Source::name).orElse(meta.getName());
    }

    private Object tryCreatingObjectInstance(final Type javaType) {
        final Class<?> type = Class.class.cast(javaType);
        if (type.isPrimitive()) {
            if (int.class == type) {
                return 0;
            }
            if (long.class == type) {
                return 0L;
            }
            if (double.class == type) {
                return 0.;
            }
            if (float.class == type) {
                return 0f;
            }
            if (short.class == type) {
                return (short) 0;
            }
            if (byte.class == type) {
                return (byte) 0;
            }
            if (boolean.class == type) {
                return false;
            }
            throw new IllegalArgumentException("Not a primitive: " + type);
        }
        if (type.getName().startsWith("java.") || type.getName().startsWith("javax.")) {
            return null;
        }
        try {
            return type.getConstructor().newInstance();
        } catch (final NoSuchMethodException | InstantiationException | IllegalAccessException
                | InvocationTargetException e) {
            // ignore, we'll skip the defaults
        }
        return null;
    }
}
