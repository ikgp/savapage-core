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

import java.util.Locale;

import org.savapage.core.jpa.PrintOut;
import org.savapage.core.util.LocaleHelper;

/**
 * Proxy Print Modes.
 *
 * <p>
 * <b>Important</b>: the length of {@link PrintModeEnum#toString()} MUST NOT
 * exceed the column width of {@link PrintOut#setPrintMode(String)}.
 *
 * @author Rijk Ravestein
 *
 */
public enum PrintModeEnum {

    /**
     * Secure WebApp Print.
     */
    AUTH,

    /**
     * Direct Automatic Print.
     */
    AUTO,

    /**
     * Hold Release Print.
     */
    HOLD,

    /**
     * Fast Release Print.
     */
    FAST,

    /**
     * Direct (Push) WebApp Print.
     */
    PUSH,

    /**
     * Job Ticket Print.
     */
    TICKET,

    /**
     * Job Ticket Copy-off-the-glass MFP Print.
     */
    TICKET_C,

    /**
     * Job Ticket External Print, outside SavaPage by third-party printer
     * driver.
     */
    TICKET_E;

    /**
     * @param locale
     *            The {@link Locale}.
     * @return The localized text.
     */
    public String uiText(final Locale locale) {
        return LocaleHelper.uiText(this, locale);
    }

    /**
     * @return {@code true} if one of the job tickets variants.
     */
    public boolean isJobTicket() {
        return this == PrintModeEnum.TICKET || this == PrintModeEnum.TICKET_C
                || this == PrintModeEnum.TICKET_E;

    }
}
