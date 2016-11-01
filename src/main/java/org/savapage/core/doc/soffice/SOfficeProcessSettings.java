/*
 * This file is part of the SavaPage project <https://www.savapage.org>.
 * Copyright (c) 2011-2016 Datraverse B.V.
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
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * For more information, please contact Datraverse B.V. at this
 * address: info@datraverse.com
 */
package org.savapage.core.doc.soffice;

/**
 *
 * @author Rijk Ravestein
 *
 */
public class SOfficeProcessSettings extends SOfficeSettings {

    /**
     * The default retry interval in milliseconds.
     */
    public static final long DEFAULT_RETRY_INTERVAL = 250L;

    /**
     *
     */
    private final SOfficeUnoUrl unoUrl;

    /**
     * The retry interval in milliseconds.
     */
    private long retryInterval = DEFAULT_RETRY_INTERVAL;

    /**
     * Constructor.
     *
     * @param unoUrl
     * @param config
     */
    public SOfficeProcessSettings(final SOfficeUnoUrl unoUrl,
            final SOfficeSettings config) {

        this.unoUrl = unoUrl;

        this.setTemplateProfileDir(config.getTemplateProfileDir());
        this.setWorkDir(config.getWorkDir());
        this.setOfficeLocation(config.getOfficeLocation());
        this.setProcessRetryTimeout(config.getProcessRetryTimeout());
        this.setTaskExecutionTimeout(config.getTaskExecutionTimeout());
        this.setTasksCountForProcessRestart(
                config.getTasksCountForProcessRestart());

    }

    public SOfficeUnoUrl getUnoUrl() {
        return unoUrl;
    }

    public long getRetryInterval() {
        return retryInterval;
    }

    public void setRetryInterval(long retryInterval) {
        this.retryInterval = retryInterval;
    }

}