/*
 * This file is part of the SavaPage project <https://www.savapage.org>.
 * Copyright (c) 2011-2020 Datraverse B.V.
 * Author: Rijk Ravestein.
 *
 * SPDX-FileCopyrightText: 2011-2020 Datraverse B.V. <info@datraverse.com>
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * For more information, please contact Datraverse B.V. at this
 * address: info@datraverse.com
 */
package org.savapage.core.dao.enums;

import java.util.HashMap;
import java.util.Map;

import org.savapage.core.dao.IppQueueAttrDao;
import org.savapage.core.jpa.IppQueueAttr;

/**
 * {@link IppQueueAttr} names. See {@link IppQueueAttr#setName(String)}.
 *
 * @author Rijk Ravestein
 *
 */
public enum IppQueueAttrEnum {

    /**
     * See {@link IppRoutingEnum}.
     */
    IPP_ROUTING("ipp.routing"),

    /**
     * JSON representation of IPP attribute/value {@link Map}.
     */
    IPP_ROUTING_OPTIONS("ipp.routing.options"),

    /**
     * JSON representation of IPP attribute/value {@link String}.
     */
    IPP_ROUTING_TARGET("ipp.routing.target"),

    /**
     * Boolean: Y | N. When not present N is assumed.
     */
    JOURNAL_DISABLE("journal.disable"),

    /**
     * Statistic time series. Example:
     * <p>
     * {@code 1342562400000,2,1,4,0,0,2,0,..,0,8,1,0}
     * </p>
     */
    PRINT_IN_ROLLING_DAY_PAGES(
            IppQueueAttrDao.STATS_ROLLING_PREFIX + "-day.pages");

    /**
     *
     */
    private static class Lookup {

        /** */
        private final Map<String, IppQueueAttrEnum> enumLookup =
                new HashMap<String, IppQueueAttrEnum>();

        /** */
        Lookup() {
            for (IppQueueAttrEnum value : IppQueueAttrEnum.values()) {
                enumLookup.put(value.dbName, value);
            }
        }

        /**
         *
         * @param key
         *            The key (name).
         * @return The enum.
         */
        public IppQueueAttrEnum get(final String key) {
            return enumLookup.get(key);
        }
    }

    /**
         *
         */
    private final String dbName;

    /**
     * Ensure one-time initialization on class loading.
     */
    private static class LookupHolder {
        /** */
        public static final Lookup INSTANCE = new Lookup();
    }

    /**
     * Gets the IppQueueAttrEnum from the database name.
     *
     * @param dbName
     *            The database name
     * @return The {@link IppQueueAttrEnum}.
     */
    public static IppQueueAttrEnum asEnum(final String dbName) {
        return LookupHolder.INSTANCE.get(dbName);
    }

    /**
     *
     * @param name
     *            The database name.
     */
    IppQueueAttrEnum(final String name) {
        this.dbName = name;
    }

    /**
     * Gets the name used in the database.
     *
     * @return The database name.
     */
    public final String getDbName() {
        return this.dbName;
    }
}
