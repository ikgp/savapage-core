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
package org.savapage.core.template.email;

import java.io.File;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.savapage.core.template.TemplateAttrEnum;
import org.savapage.core.template.dto.TemplateDto;
import org.savapage.core.template.dto.TemplateMailTicketDto;

/**
 *
 * @author Rijk Ravestein
 *
 */
public final class MailPrintTicketReceived extends EmailTemplateMixin {

    /** */
    private final TemplateMailTicketDto ticket;

    /**
     *
     * @param customHome
     *            The directory with the custom Email template files.
     * @param ticket
     *            The ticket.
     */
    public MailPrintTicketReceived(final File customHome,
            final TemplateMailTicketDto ticket) {
        super(customHome);
        this.ticket = ticket;
    }

    @Override
    protected final Map<String, TemplateDto> onRender(final Locale locale) {
        final Map<String, TemplateDto> map = new HashMap<>();
        map.put(TemplateAttrEnum.MAIL_TICKET.asAttr(), this.ticket);
        return map;
    }

}
