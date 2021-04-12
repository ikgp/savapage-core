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
package org.savapage.core.ipp.operation;

import java.io.IOException;
import java.io.InputStream;

import org.savapage.core.ipp.IppProcessingException;
import org.savapage.core.ipp.attribute.IppAttrValue;
import org.savapage.core.ipp.attribute.IppDictJobDescAttr;
import org.savapage.core.jpa.User;
import org.savapage.core.print.server.DocContentPrintProcessor;

/**
 *
 * @author Rijk Ravestein
 *
 */
public final class IppValidateJobReq extends AbstractIppRequest {

    /** */
    private DocContentPrintProcessor printInReqHandler = null;

    @Override
    protected void process(final AbstractIppOperation operation,
            final InputStream istr) {
        // no code intended
    }

    /**
     * Just the attributes, not the print job data.
     *
     * @param operation
     *            IPP operation
     * @param istr
     *            IPP input stream
     * @throws Exception
     *             If error.
     */
    public void processAttributes(final IppValidateJobOperation operation,
            final InputStream istr) throws IOException {

        final String authWebAppUser;

        if (operation.isAuthUserIppRequester()) {
            authWebAppUser = operation.getAuthenticatedUser();
        } else {
            authWebAppUser = null;
        }

        /*
         * Step 1: Create generic PrintIn handler.
         *
         * This should be a first action because this handler holds the deferred
         * exception.
         */
        this.printInReqHandler =
                new DocContentPrintProcessor(operation.getQueue(),
                        operation.getRemoteAddr(), null, authWebAppUser);

        /*
         * Step 2: Read the IPP attributes.
         */
        readAttributes(operation, istr);

        /*
         * Then, assign user.
         */
        final String requestingUserName = this.getRequestingUserName();
        final String assignedUserId;

        if (operation.isAuthUserIppRequester()) {
            assignedUserId = operation.getAuthenticatedUser();
        } else {
            assignedUserId = requestingUserName;
        }

        /*
         * Check...
         */
        this.printInReqHandler.setJobName(this.getJobName());
        this.printInReqHandler.processAssignedUser(assignedUserId,
                requestingUserName);
    }

    /**
     *
     * @return
     */
    public String getJobName() {

        final IppAttrValue ippValue =
                getAttrValue(IppDictJobDescAttr.ATTR_JOB_NAME);

        if (ippValue == null || ippValue.getValues().isEmpty()) {
            return "";
        }
        return ippValue.getValues().get(0);
    }

    /**
     * The user object from the database representing the user who printed this
     * job.
     *
     * @return {@code null} when unknown.
     */
    public User getUserDb() {
        return this.printInReqHandler.getUserDb();
    }

    /**
     * Is the trusted user present?
     *
     * @return
     */
    public boolean isTrustedUser() {
        return this.printInReqHandler.isTrustedUser();
    }

    public boolean hasDeferredException() {
        return this.printInReqHandler.getDeferredException() != null;
    }

    public IppProcessingException getDeferredException() {
        return (IppProcessingException) printInReqHandler
                .getDeferredException();
    }

    public void setDeferredException(IppProcessingException e) {
        printInReqHandler.setDeferredException(e);
    }

}
