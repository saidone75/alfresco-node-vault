/*
 * Alfresco Node Vault - archive today, accelerate tomorrow
 * Copyright (C) 2025-2026 Saidone
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.saidone.utils;

import lombok.experimental.UtilityClass;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Utility class providing methods for casting collections to specific generic
 * types.
 * <p>
 * The helpers in this class convert loose {@code Object} instances into typed
 * {@link List} and {@link Map} representations. Inputs are expected to already
 * contain compatible elements; otherwise a {@link ClassCastException} will be
 * raised by the casting operations. When {@code null} is provided, the methods
 * return empty collections instead of {@code null}.
 */
@UtilityClass
public class CastUtils {

    /**
     * Casts an object to a {@link List} of {@link String} values.
     * <p>
     * The input must already be a {@link List}; otherwise an
     * {@link IllegalArgumentException} is thrown. Each element is cast to
     * {@link String}, so incompatible entries trigger a
     * {@link ClassCastException}. When {@code null} is supplied, an empty list is
     * returned.
     *
     * @param object the input value expected to be a {@link List}
     * @return a list containing string values, or an empty list when the input
     * is {@code null}
     * @throws IllegalArgumentException if {@code object} is not a {@link List}
     * @throws ClassCastException       if any element cannot be cast to
     *                                  {@link String}
     */
    public List<String> castToListOfStrings(Object object) {
        return castToListOfObjects(object, String.class);
    }

    /**
     * Casts an object to a {@link List} containing elements of the requested type.
     * <p>
     * The input must already be a {@link List}; otherwise an
     * {@link IllegalArgumentException} is thrown. Each element is cast using
     * {@link Class#cast(Object)}, so incompatible entries trigger a
     * {@link ClassCastException}. When {@code null} is supplied, an empty list is
     * returned.
     *
     * @param object      the input value expected to be a {@link List}
     * @param elementType the desired element type
     * @return a list containing elements cast to {@code elementType}, or an empty
     * list when the input is {@code null}
     * @throws IllegalArgumentException if {@code object} is not a {@link List}
     * @throws ClassCastException       if any element cannot be cast to
     *                                  {@code elementType}
     */
    public <T> List<T> castToListOfObjects(Object object, Class<T> elementType) {
        if (object == null) {
            return new ArrayList<>();
        }

        if (!(object instanceof List<?> inputList)) {
            throw new IllegalArgumentException(
                    String.format("Input object is not a List: %s", object.getClass().getName())
            );
        }

        return inputList.stream()
                .map(elementType::cast)
                .collect(Collectors.toList());
    }

    /**
     * Casts an object to a {@link Map} with {@link String} keys and
     * {@link Serializable} values.
     * <p>
     * The input must already be a {@link Map}; otherwise an
     * {@link IllegalArgumentException} is thrown. Keys are cast to
     * {@link String} and values to {@link Serializable}, so incompatible entries
     * cause {@link ClassCastException}s. When {@code null} is provided, an empty
     * map is returned.
     *
     * @param object the input value expected to be a {@link Map}
     * @return a map containing string keys and serializable values, or an empty
     * map when the input is {@code null}
     * @throws IllegalArgumentException if {@code object} is not a {@link Map}
     * @throws ClassCastException       if any key or value cannot be cast to the
     *                                  requested types
     */
    public Map<String, Serializable> castToMapOfStringSerializable(Object object) {
        return castToMapOfObjectObject(object, String.class, Serializable.class);
    }

    /**
     * Casts an object to a {@link Map} with {@link String} keys and values of the
     * requested {@link Serializable} type.
     * <p>
     * The input must be a {@link Map}; otherwise an
     * {@link IllegalArgumentException} is thrown. Keys are cast to
     * {@link String} and values to {@code valueType}, so incompatible entries
     * cause {@link ClassCastException}s. When {@code null} is provided, an empty
     * map is returned.
     *
     * @param object    the input value expected to be a {@link Map}
     * @param keyType   the expected key type
     * @param valueType the expected value type
     * @return a map containing entries cast to the requested types, or an empty
     * map when the input is {@code null}
     * @throws IllegalArgumentException if {@code object} is not a {@link Map}
     * @throws ClassCastException       if any key or value cannot be cast to the
     *                                  requested types
     */
    public <KT, KV> Map<KT, KV> castToMapOfObjectObject(Object object, Class<KT> keyType, Class<KV> valueType) {
        if (object == null) {
            return new HashMap<>();
        }

        if (!(object instanceof Map<?, ?> inputMap)) {
            throw new IllegalArgumentException(
                    String.format("Input object is not a Map: %s", object.getClass().getName())
            );
        }

        return inputMap.entrySet()
                .stream()
                .collect(Collectors.toMap(
                        e -> keyType.cast(e.getKey()),
                        e -> valueType.cast(e.getValue())
                ));
    }

}
