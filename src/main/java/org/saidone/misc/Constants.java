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

package org.saidone.misc;

/**
 * Collection of simple string constants shared across the application.
 * <p>
 * The prefixes are used by {@link org.saidone.component.BaseComponent} to
 * clearly mark lifecycle events in the log output.
 */
public interface Constants {

    /** Prefix displayed when a component is starting. */
    String START_PREFIX = ">>>>>";
    /** Prefix displayed when a component is stopping. */
    String STOP_PREFIX = "<<<<<";

}
