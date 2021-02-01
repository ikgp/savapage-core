/*
 * This file is part of the SavaPage project <https://www.savapage.org>.
 * Copyright (c) 2020 Datraverse B.V.
 * Author: Rijk Ravestein.
 *
 * SPDX-FileCopyrightText: © 2020 Datraverse B.V. <info@datraverse.com>
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
package org.savapage.core.i18n;

import java.util.Locale;

import org.savapage.core.util.LocaleHelper;

/**
 * UI Adjectives.
 *
 * @author Rijk Ravestein
 *
 */
public enum AdjectiveEnum {

    /** */
    ABSTRACT,

    /** */
    ACTIVE,

    /** */
    DOWLOADED, ERASED, PRINTED, RECEIVED,

    /** */
    APPROVED, REJECTED, REVERSED, REFUNDED,

    /** */
    ASCENDING, DESCENDING,

    /** */
    SCALED,

    /** */
    VALID, INVALID,

    /** */
    INTERNAL, EXTERNAL,

    /** */
    ALL, NONE,

    /** */
    UNKNOWN, KNOWN,

    /** */
    REOPENED,

    /** */
    REPAIRED,

    /** */
    ROLLED_BACK,

    /** */
    PREFERRED,

    /** */
    PENDING,

    /** */
    TRANSPARENT,

    /** */
    FIXED, CHANGEABLE,

    /** */
    OPEN, CLOSED,

    /** */
    PUBLIC, PRIVATE,

    /** */
    EDITED, SELECTED, CUT, COPIED, PASTED,

    /** */
    HELD, RELEASED;

    /**
     * @param locale
     *            The {@link Locale}.
     * @return The localized text.
     */
    public String uiText(final Locale locale) {
        return LocaleHelper.uiText(this, locale);
    }

}
