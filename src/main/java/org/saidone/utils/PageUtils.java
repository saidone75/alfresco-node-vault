/*
 * Alfresco Node Vault - ad aeternam documentorum conservationem
 * Copyright (C) 2025-2026 Saidone
 * SPDX-License-Identifier: GPL-3.0-or-later
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
import org.saidone.exception.InvalidRequestException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/**
 * Utility class for building pagination requests from API parameters.
 */
@UtilityClass
public class PageUtils {

    /**
     * Creates a {@link PageRequest} validating the supplied parameters.
     *
     * @param page zero-based page number
     * @param size page size
     * @param sort sort order
     * @return the page request
     * @throws InvalidRequestException if {@code page} is negative or {@code size} is not positive
     */
    public PageRequest pageRequest(int page, int size, Sort sort) {
        if (page < 0) {
            throw new InvalidRequestException("Page number must not be negative");
        }
        if (size < 1) {
            throw new InvalidRequestException("Page size must be greater than zero");
        }
        return PageRequest.of(page, size, sort);
    }

}
