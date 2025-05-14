/*
 *  Alfresco Node Vault - archive today, accelerate tomorrow
 *  Copyright (C) 2025 Saidone
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.saidone.utils;

import lombok.experimental.UtilityClass;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Utility class providing methods for safely casting collections to specific generic types.
 * <p>
 * This class offers methods to cast a list of unknown type to a list of strings,
 * and to cast an object representing a map to a map with string keys and object values.
 * All methods return empty collections if the provided input is null.
 */
@UtilityClass
public class CastUtils {

    /**
     * Safely casts a list of objects to a list containing only non-null strings.
     * <br>
     * This method filters out null values and elements that are not instances of {@code String}.
     * If the input list is {@code null}, an empty list is returned.
     *
     * @param list the input list containing elements of any type
     * @return a list containing only non-null strings from the original list,
     * or an empty list if input is {@code null}
     */
    public List<String> castToListOfStrings(List<?> list) {
        if (list == null) return Collections.emptyList();
        return list.stream()
                .filter(Objects::nonNull)
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .toList();
    }

    /**
     * Casts an input object to a map with {@code String} keys and {@code Object} values.
     * <br>
     * If the provided object is {@code null}, an empty map is returned.
     * If the provided object is not an instance of {@code Map<?, ?>}, an {@code IllegalArgumentException} is thrown.
     * The method performs a runtime cast of each key to {@code String}; if any key cannot be cast, a {@code ClassCastException} will be thrown.
     *
     * @param object the object to cast, expected to be a map with string keys
     * @return a map with string keys and object values, or an empty map if the input is null
     * @throws IllegalArgumentException if the input object is not a map
     * @throws ClassCastException       if a map key cannot be cast to {@code String}
     */
    public Map<String, Object> castToMapOfStringObject(Object object) {
        if (object == null) {
            return new HashMap<>();
        }
        if (!(object instanceof Map<?, ?> inputMap)) {
            throw new IllegalArgumentException(String.format("Input object is not a Map: %s", object.getClass().getName()));
        }
        return inputMap.entrySet()
                .stream()
                .collect(Collectors.toMap(
                        e -> (String) e.getKey(),
                        Map.Entry::getValue
                ));
    }

}
