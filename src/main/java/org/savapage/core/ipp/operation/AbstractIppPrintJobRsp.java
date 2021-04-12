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
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.savapage.core.ipp.attribute.IppAttr;
import org.savapage.core.ipp.attribute.IppAttrGroup;
import org.savapage.core.ipp.attribute.IppAttrValue;
import org.savapage.core.ipp.attribute.IppDictJobDescAttr;
import org.savapage.core.ipp.attribute.syntax.IppJobState;
import org.savapage.core.ipp.attribute.syntax.IppText;
import org.savapage.core.ipp.encoding.IppDelimiterTag;

/**
 *
 * @author Rijk Ravestein
 *
 */
public abstract class AbstractIppPrintJobRsp extends AbstractIppResponse {

    /**
     *
     * @param operation
     *            IPP operation
     * @param request
     *            IPP request.
     * @param ostr
     *            IPP output.
     * @param jobStateSuccess
     *            IPP Job State when IPP operation was successful..
     * @throws IOException
     *             If IO error.
     */
    public void process(final AbstractIppJobOperation operation,
            final AbstractIppPrintJobReq request, final OutputStream ostr,
            final String jobStateSuccess) throws IOException {

        final List<IppAttrGroup> attrGroups = new ArrayList<>();

        IppAttrGroup group = null;
        IppAttrValue value = null;
        IppAttr attr = null;
        IppStatusCode requestStatus = null;

        final String jobState;
        final String jobStateReasons;

        /*
         * First test on an exception, and DRM violation since this overrules
         * everything.
         */
        if (request.hasDeferredException()
                || request.isDrmViolationDetected()) {

            /*
             * To be applied with HttpServletResponse.SC_INTERNAL_SERVER_ERROR.
             * Intended effect in IPP client: job will NOT print, and is NOT
             * held in the local queue.
             */
            requestStatus = IppStatusCode.OK;

            jobState = IppJobState.STATE_ABORTED;
            jobStateReasons = "aborted-by-system";

        } else if (operation.isAuthorized()) {

            requestStatus = request.getResponseStatusCode();

            if (requestStatus == IppStatusCode.OK) {
                jobState = jobStateSuccess;
                jobStateReasons = "job-completed-successfully";
            } else {
                jobState = IppJobState.STATE_CANCELED;
                jobStateReasons = "aborted-by-system";
            }

        } else {
            /*
             * To be applied with HttpServletResponse.SC_OK. Intended effect in
             * IPP client: job will NOT print, and is held in the local queue.
             * After IP authenticated in User Web App, job can be restarted in
             * local queue. Mantis #1181.
             */
            requestStatus = IppStatusCode.CLI_NOAUTH;
            jobState = IppJobState.STATE_CANCELED;
            jobStateReasons = "account-authorization-failed";
        }

        /*
         * Group 1: Operation Attributes
         */
        group = this.createOperationGroup();
        attrGroups.add(group);

        /*
         * (detailed) messages
         */
        if (!operation.isAuthorized()) {

            attr = new IppAttr("status-message", new IppText());
            value = new IppAttrValue(attr);
            value.addValue(
                    "before printing login to the SavaPage WebApp first");
            group.addAttribute(value);

            attr = new IppAttr("detailed-status-message", new IppText());
            value = new IppAttrValue(attr);
            value.addValue("You are printing to an untrusted SavaPage Queue. "
                    + "Make sure you are logged into the SavaPage WebApp, "
                    + "and inspect your local printer queue for held jobs.");
            group.addAttribute(value);

        } else if (request.hasDeferredException()) {

            attr = new IppAttr("status-message", new IppText());
            value = new IppAttrValue(attr);
            value.addValue("Internal Savapage Error");
            group.addAttribute(value);

            String msg = request.getDeferredException().getMessage();
            if (StringUtils.isNotBlank(msg)) {
                attr = new IppAttr("detailed-status-message", new IppText());
                value = new IppAttrValue(attr);
                value.addValue(msg);
                group.addAttribute(value);
            }

        } else if (request.isDrmViolationDetected()) {

            attr = new IppAttr("status-message", new IppText());
            value = new IppAttrValue(attr);
            value.addValue("PostScript Re-Distill not allowed");
            group.addAttribute(value);

            attr = new IppAttr("detailed-status-message", new IppText());
            value = new IppAttrValue(attr);
            value.addValue("SavaPage is set to disallow printing Encrypted "
                    + "PDF documents.");
            group.addAttribute(value);
        }

        /**
         * Group 2: Unsupported Attributes
         */

        /**
         * Group 3: Job Object Attributes
         */
        final int jobId = request.getJobId();

        group = new IppAttrGroup(IppDelimiterTag.JOB_ATTR);
        attrGroups.add(group);

        final IppDictJobDescAttr dict = IppDictJobDescAttr.instance();

        attr = dict.getAttr(IppDictJobDescAttr.ATTR_JOB_URI);
        value = new IppAttrValue(attr);
        value.addValue(request.getJobUri(jobId));
        group.addAttribute(value);

        attr = dict.getAttr(IppDictJobDescAttr.ATTR_JOB_ID);
        value = new IppAttrValue(attr);
        value.addValue(String.valueOf(jobId));
        group.addAttribute(value);

        attr = dict.getAttr(IppDictJobDescAttr.ATTR_JOB_STATE);
        value = new IppAttrValue(attr);
        value.addValue(jobState);
        group.addAttribute(value);

        attr = dict.getAttr(IppDictJobDescAttr.ATTR_JOB_STATE_REASONS);
        value = new IppAttrValue(attr);
        value.addValue(jobStateReasons);
        group.addAttribute(value);

        attr = dict.getAttr(IppDictJobDescAttr.ATTR_JOB_STATE_MESSAGE);
        value = new IppAttrValue(attr);
        if (requestStatus == IppStatusCode.OK) {
            value.addValue("Open SavaPage Web App to see the print.");
        } else {
            if (operation.isAuthorized()) {
                value.addValue("Something went wrong");
            } else {
                value.addValue("Login to the SavaPage WebApp before printing!");
            }
        }
        group.addAttribute(value);

        //
        this.write(operation, requestStatus, attrGroups, ostr,
                request.getAttributesCharset());
    }

}
