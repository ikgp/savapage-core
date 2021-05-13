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
package org.savapage.core.config;

import java.io.File;

import org.savapage.common.SystemPropertyEnum;

/**
 * Paths relative to the {@code server} directory without leading or trailing
 * '/'.
 *
 * @author Rijk Ravestein
 *
 */
public enum ServerPathEnum {

    /**
     * The relative path of the client folder.
     */
    CLIENT("client"),

    /**
     * The relative path of the custom template files.
     */
    CUSTOM_TEMPLATE("custom/template"),

    /**
     * The relative path of the CUPS custom properties files.
     */
    CUSTOM_CUPS("custom/cups"),

    /**
     * The relative path of the custom i18n properties files.
     */
    CUSTOM_I18N("custom/i18n"),

    /**
     * The relative path of the CUPS custom i18n XML files.
     */
    CUSTOM_CUPS_I18N("custom/cups/i18n"),

    /**
     * The relative path of the HTML injectable files.
     */
    CUSTOM_HTML("custom/html"),

    /**
     * The relative path of the custom web files.
     */
    CUSTOM_WEB(ServerBasePath.CUSTOM_WEB),

    /**
     * The relative path of the custom web themes.
     */
    CUSTOM_WEB_THEMES("custom/web/themes"),

    /**
     * The relative path of the data folder.
     */
    DATA("data"),

    /**
     * The relative path of the data configuration folder.
     */
    DATA_CONF("data/conf"),

    /**
     * The relative path of the email outbox folder.
     */
    EMAIL_OUTBOX("data/email-outbox"),

    /**
     * Extensions.
     */
    EXT("ext"),

    /**
     * Extension JAR files.
     */
    EXT_LIB("ext/lib"),

    /**
     * Server jar files.
     */
    LIB_WEB("lib/web"),

    /**
     * The relative path of the default SafePages folder.
     */
    SAFEPAGES_DEFAULT("data/internal/safepages"),

    /**
     * Public letterheads.
     */
    LETTERHEADS("data/internal/letterheads"),

    /**
     * LibreJS license info injector.
     */
    LIBREJS(ServerBasePath.LIBREJS),

    /**
     * The relative path of the print-jobtickets folder.
     */
    PRINT_JOBTICKETS("data/print-jobtickets"),

    /**
     * The relative path of the doc archive folder.
     */
    DOC_ARCHIVE("data/doc-archive"),

    /**
     * The relative path of the doc journal folder.
     */
    DOC_JOURNAL("data/doc-journal"),

    /**
     * The relative path of the Atom Feeds folder.
     */
    FEEDS("data/feed");

    /** */
    private final String path;

    /**
     *
     * @param subdir
     *            Relative path in server directory.
     */
    ServerPathEnum(final String subdir) {
        this.path = subdir;
    }

    /**
     * @return Relative path in server directory.
     */
    public String getPath() {
        return this.path;
    }

    /**
     * @return Absolute path of server directory.
     */
    public File getPathAbsolute() {
        return new File(String.format("%s%c%s",
                SystemPropertyEnum.SAVAPAGE_SERVER_HOME.getValue(),
                File.separatorChar, this.getPath()));
    }
}
