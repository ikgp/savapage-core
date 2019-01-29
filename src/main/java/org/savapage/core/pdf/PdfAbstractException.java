/*
 * This file is part of the SavaPage project <https://www.savapage.org>.
 * Copyright (c) 2011-2019 Datraverse B.V.
 * Author: Rijk Ravestein.
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
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * For more information, please contact Datraverse B.V. at this
 * address: info@datraverse.com
 */
package org.savapage.core.pdf;

import java.util.Locale;

import org.savapage.core.i18n.PhraseEnum;

/**
 * An exception to report an encrypted PDF document.
 *
 * @author Rijk Ravestein
 *
 */
public abstract class PdfAbstractException extends Exception {

    /**
     *
     */
    private static final long serialVersionUID = 1L;

    /**
     * Message for logging.
     */
    private final PhraseEnum logPhrase;

    /**
     *
     * @param message
     *            Message.
     * @param phrase
     *            Message for logging.
     */
    public PdfAbstractException(final String message, final PhraseEnum phrase) {
        super(message);
        this.logPhrase = phrase;
    }

    /**
     *
     * @return English log message.
     */
    public String getLogMessage() {
        if (this.logPhrase == null) {
            return this.getMessage();
        }
        return this.logPhrase.uiText(Locale.ENGLISH);
    }
}
