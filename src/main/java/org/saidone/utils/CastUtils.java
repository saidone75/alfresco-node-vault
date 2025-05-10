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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
     * Casts a list of unknown element type to a list of strings.
     * <p>
     * If the input list is null, an empty list is returned.
     * Performs a runtime cast of all elements to String.
     *
     * @param list the input list with unknown element type
     * @return a list containing the elements cast to String, or an empty list if input is null
     * @throws ClassCastException if any element cannot be cast to String
     */
    public List<String> castToListOfStrings(List<?> list) {
        return list != null ? list
                .stream()
                .map(String.class::cast)
                .collect(Collectors.toList()
                ) : new ArrayList<>();
    }

    /**
     * Casts an object representing a map to a map with String keys and Object values.
     * <p>
     * If the input map is null, an empty map is returned.
     * Performs a runtime cast of each entry's key to String and value to Object.
     *
     * @param map the input object expected to be of type Map<?, ?>
     * @return a new map with string keys and object values, or an empty map if input is null
     * @throws ClassCastException if any map entry key cannot be cast to String
     */
    public Map<String, Object> castToMapOfStringObject(Object map) {
        return map != null ? ((Map<?, ?>) map)
                .entrySet()
                .stream()
                .collect(Collectors.toMap(
                        e -> (String) e.getKey(),
                        e -> (Object) e.getValue()
                )) : new HashMap<>();
    }

}
