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
package org.savapage.core.services.helpers.account;

import java.math.BigDecimal;
import java.util.Locale;

import org.savapage.core.jpa.User;

/**
 *
 * @author Rijk Ravestein
 *
 */
public interface UserAccountContext {

    /**
     *
     * @return The context as enum.
     */
    UserAccountContextEnum asEnum();

    /**
     * Gets account balance of a {@link User}.
     *
     * @param user
     *            The non-null {@link User}.
     * @return Zero balance when User Account is NOT present.
     */
    BigDecimal getUserBalance(User user);

    /**
     * Gets the formatted balance of a {@link User}.
     *
     * @param user
     *            The non-null {@link User}.
     * @param locale
     *            The {@link Locale} for formatting.
     * @param currencySymbol
     *            The currency symbol (can be {@code null}).
     * @return Zero balance when User Account is NOT present.
     */
    String getFormattedUserBalance(User user, Locale locale,
            String currencySymbol);
}
