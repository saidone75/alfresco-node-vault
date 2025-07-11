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

package org.saidone.model.alfresco;

/**
 * Constants for the Alfresco Node Vault custom content model. Generated using
 * the Saidone content model tool.
 */
@SuppressWarnings("unused")
public interface AnvContentModel {

    /* generated with https://saidone.org/#/cm */

    String ANV_URI = "https://www.saidone.org/model/anv/1.0";
    String ANV_PREFIX = "anv";
    String ASP_ARCHIVE_LOCALNAME = "archive";
    String ASP_ARCHIVE = String.format("%s:%s", ANV_PREFIX, ASP_ARCHIVE_LOCALNAME);
    String ASP_RESTORED_LOCALNAME = "restored";
    String ASP_RESTORED = String.format("%s:%s", ANV_PREFIX, ASP_RESTORED_LOCALNAME);
    String PROP_WAS_LOCALNAME = "was";
    String PROP_WAS = String.format("%s:%s", ANV_PREFIX, PROP_WAS_LOCALNAME);

}