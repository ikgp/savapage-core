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

import org.savapage.core.util.LocaleHelper;

/**
 * Role Security Identity (SID). A <i>role</i> is a job function or title which
 * defines an authority level.
 *
 * @author Rijk Ravestein
 *
 */
public enum ACLRoleEnum {

    /**
     * Job ticket user.
     */
    JOB_TICKET_CREATOR,

    /**
     * Handle a job ticket created by a {@link #JOB_TICKET_CREATOR}.
     */
    JOB_TICKET_OPERATOR,

    /**
     * Handle a Mail Ticket.
     */
    MAIL_TICKET_OPERATOR,

    /**
     * Operate a Point-of-Sale.
     */
    WEB_CASHIER,

    /**
     * Can create a print job.
     */
    PRINT_CREATOR,

    /**
     * Agrees that a delegate prints on his behalf.
     */
    PRINT_DELEGATOR,

    /**
     * Print on behalf of a {@link #PRINT_DELEGATOR}.
     */
    PRINT_DELEGATE,

    /**
     * Print Site Operator.
     */
    PRINT_SITE_OPERATOR;

    /**
     * @param locale
     *            The {@link Locale}.
     * @return The localized text.
     */
    public String uiText(final Locale locale) {
        return LocaleHelper.uiText(this, locale);
    }

}
