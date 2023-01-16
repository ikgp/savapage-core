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
package org.savapage.core.services;

import java.io.InputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.savapage.core.SpException;
import org.savapage.core.UnavailableException;
import org.savapage.core.config.IConfigProp;
import org.savapage.core.dao.enums.IppQueueAttrEnum;
import org.savapage.core.dao.enums.IppRoutingEnum;
import org.savapage.core.dao.enums.ReservedIppQueueEnum;
import org.savapage.core.jpa.IppQueue;
import org.savapage.core.jpa.IppQueueAttr;
import org.savapage.core.jpa.User;
import org.savapage.core.print.server.DocContentPrintException;
import org.savapage.core.print.server.DocContentPrintReq;
import org.savapage.core.print.server.DocContentPrintRsp;

/**
 *
 * @author Rijk Ravestein
 *
 */
public interface QueueService {

    /** */
    int MAX_TIME_SERIES_INTERVALS_DAYS = 40;

    /**
     * Encapsulation of IppQueue attribute map.
     */
    class IppQueueAttrLookup {

        /**
         *
         */
        private final Map<String, String> lookup;

        /**
         *
         * @param queue
         *            The IppQueue.
         */
        public IppQueueAttrLookup(final IppQueue queue) {
            this.lookup = new HashMap<>();
            if (queue.getAttributes() != null) {
                for (final IppQueueAttr attr : queue.getAttributes()) {
                    lookup.put(attr.getName(), attr.getValue());
                }
            }
        }

        /**
         * Gets the attribute value.
         *
         * @param key
         *            The attribute key.
         * @return The attribute value, or {@code null} when not found.
         */
        public String get(final IppQueueAttrEnum key) {
            return lookup.get(key.getDbName());
        }

        /**
         * Gets the attribute value.
         *
         * @param key
         *            The attribute key.
         * @param dfault
         *            The default value.
         * @return The attribute value, or the default when not found.
         */
        public String get(final IppQueueAttrEnum key, final String dfault) {
            String value = this.get(key);
            if (value == null) {
                value = dfault;
            }
            return value;
        }

        /**
         * Checks if the the value of the attribute key represents "true" value.
         *
         * @param key
         *            The attribute key.
         * @param dfault
         *            The default value.
         * @return {@code true} when key value represents true.
         */
        public boolean isTrue(final IppQueueAttrEnum key,
                final boolean dfault) {

            final boolean bValue;

            final String value = this.get(key);

            if (value == null) {
                bValue = dfault;
            } else {
                bValue = value.equals(IConfigProp.V_YES);
            }

            return bValue;
        }

    }

    /**
     * Creates the reserved queues when they do not exist.
     */
    void lazyCreateReservedQueues();

    /**
     * Does {@link IppQueue} represent a raw print queue.
     *
     * @param queue
     *            The {@link IppQueue} .
     * @return {@code true} when queue represents a raw print queue.
     */
    boolean isRawPrintQueue(IppQueue queue);

    /**
     * Gets the reserved {@link IppQueue}. When it does not exist it is created.
     *
     * @param reservedQueue
     *            The {@link ReservedIppQueueEnum}..
     * @return The {@link IppQueue}.
     */
    IppQueue getOrCreateReservedQueue(ReservedIppQueueEnum reservedQueue);

    /**
     * Reads the database to check if document store journal is disabled.
     *
     * @param id
     *            The database primary key.
     * @return {@code true} when document journal store is disabled.
     */
    boolean isDocStoreJournalDisabled(Long id);

    /**
     * Checks if document journal is disabled.
     * <p>
     * Traverses the internal {@link IppQueueAttr} list of a {@link IppQueue} to
     * find the {@link IppQueueAttrEnum} value.
     * </p>
     *
     * @param queue
     *            The {@link IppQueue}.
     * @return {@code true} when document store journal is disabled.
     */
    boolean isDocStoreJournalDisabled(IppQueue queue);

    /**
     * Does {@link IppQueue} represent an active queue. A driver (regular and
     * reserved) queue is active when not deleted and not disabled. A driverless
     * reserved queue is active when enabled either by configuration or queue
     * setting (not deleted and not disabled).
     *
     * @param queue
     *            The {@link IppQueue} .
     * @return {@code true} when queue is active.
     */
    boolean isActiveQueue(IppQueue queue);

    /**
     * Checks if urlPath represents a reserved queue (the check is
     * case-insensitive).
     *
     * @param urlPath
     *            The URL path.
     * @return {@link ReservedIppQueueEnum} or {@code null} when URL path is a
     *         user-defined queue.
     */
    ReservedIppQueueEnum getReservedQueue(String urlPath);

    /**
     * Checks if urlPath represents a reserved queue (the check is
     * case-insensitive).
     *
     * @param urlPath
     *            The URL path.
     * @return {@code true} when URL path represents a reserved queue.
     */
    boolean isReservedQueue(String urlPath);

    /**
     * Does {@link IppQueue} support IPP Routing.
     *
     * @param queue
     *            The queue.
     * @return {@code true} when queue supports IPP Routing.
     */
    boolean isIppRoutingQueue(IppQueue queue);

    /**
     * Does {@link IppQueue} represent the Mail Print queue.
     *
     * @param queue
     *            The {@link IppQueue} .
     * @return {@code true} when queue represents the Mail Print queue.
     */
    boolean isMailPrintQueue(IppQueue queue);

    /**
     * Does {@link IppQueue} represent the Web Print queue.
     *
     * @param queue
     *            The queue.
     * @return {@code true} when queue represents the Web Print queue.
     */
    boolean isWebPrintQueue(IppQueue queue);

    /**
     * Traverses the internal {@link IppQueueAttr} list of a {@link IppQueue} to
     * remove a {@link IppQueueAttr}.
     *
     * @param queue
     *            The {@link IppQueue}.
     * @param name
     *            The {@link IppQueueAttrEnum}.
     *
     * @return The {@link IppQueueAttr} that was removed, or {@code null} when
     *         not found.
     */
    IppQueueAttr removeAttribute(IppQueue queue, IppQueueAttrEnum name);

    /**
     * Traverses the internal {@link IppQueueAttr} list of a {@link IppQueue} to
     * get the {@link IppQueueAttr}.
     *
     * @param queue
     *            The {@link IppQueue}.
     * @param name
     *            The {@link IppQueueAttrEnum}.
     *
     * @return {@code null} when not found.
     */
    IppQueueAttr getAttribute(IppQueue queue, IppQueueAttrEnum name);

    /**
     * Traverses the attributes of a {@link IppQueue} to get the value of an
     * attribute.
     *
     * @param queue
     *            The {@link IppQueue}.
     * @param attr
     *            The {@link IppQueueAttrEnum}.
     *
     * @return {@code null} when not found.
     */
    String getAttrValue(IppQueue queue, IppQueueAttrEnum attr);

    /**
     * Traverses the attributes of a {@link IppQueue} to get the value of
     * {@link IppQueueAttrEnum#IPP_ROUTING}.
     *
     * @param queue
     *            The {@link IppQueue}.
     * @return {@code null} when not found.
     */
    IppRoutingEnum getIppRouting(IppQueue queue);

    /**
     * Traverses the attributes of a {@link IppQueue} to get the {@link Map} of
     * {@link IppQueueAttrEnum#IPP_ROUTING_OPTIONS}.
     *
     * @param queue
     *            The {@link IppQueue}.
     * @return {@code null} when not found.
     */
    Map<String, String> getIppRoutingOptions(IppQueue queue);

    /**
     * Traverses the attributes of a {@link IppQueue} to get the
     * {@link IppQueueAttrEnum#IPP_ROUTING_TARGET}.
     *
     * @param queue
     *            The {@link IppQueue}.
     * @return {@code null} when not found.
     */
    String getIppRoutingTarget(IppQueue queue);

    /**
     * Creates or updates the attribute value to the database.
     *
     * @param queue
     *            The {@link IppQueue}.
     * @param attrEnum
     *            The name of the {@link IppQueueAttr}.
     * @param attrValue
     *            The value.
     */
    void setQueueAttrValue(IppQueue queue, IppQueueAttrEnum attrEnum,
            String attrValue);

    /**
     * Creates, updates or removes a queue boolean attribute in the database.
     *
     * @param queue
     *            The {@link IppQueue}.
     * @param attrEnum
     *            The name of the {@link IppQueueAttr}.
     * @param attrValue
     *            The value.
     */
    void setQueueAttrValue(IppQueue queue, IppQueueAttrEnum attrEnum,
            Boolean attrValue);

    /**
     * Deletes the attribute from the database.
     *
     * @param queue
     *            The {@link IppQueue}.
     * @param attrEnum
     *            The name of the {@link IppQueueAttr}.
     * @return {@code false} when attribute was not found.
     */
    boolean deleteQueueAttrValue(IppQueue queue, IppQueueAttrEnum attrEnum);

    /**
     * Sets queue instance as logically deleted (database is NOT updated).
     *
     * @param queue
     *            The {@link IppQueue} .
     * @param deletedDate
     *            The date.
     * @param deletedBy
     *            The actor.
     */
    void setLogicalDeleted(IppQueue queue, Date deletedDate, String deletedBy);

    /**
     * Reverses a logical delete (database is NOT updated).
     *
     * @param queue
     *            The queue.
     */
    void undoLogicalDeleted(IppQueue queue);

    /**
     * Adds totals of a job to an {@link IppQueue} (database is NOT updated).
     *
     * @param queue
     *            The {@link IppQueue} .
     * @param jobDate
     *            The date.
     * @param jobPages
     *            The number of pages.
     * @param jobBytes
     *            The number of bytes.
     */
    void addJobTotals(IppQueue queue, Date jobDate, int jobPages,
            long jobBytes);

    /**
     * Logs a PrintIn job, by adding a data point to the time series (database
     * IS updated).
     *
     * @param queue
     *            The {@link IppQueue}
     * @param observationTime
     *            The observation time (time of the PrintIn job).
     * @param jobPages
     *            The number of pages.
     */
    void logPrintIn(IppQueue queue, Date observationTime, Integer jobPages);

    /**
     * Prints a document {@link InputStream} originating from a {@link User} to
     * a reserved Queue.
     *
     * @param reservedQueue
     *            The {@link ReservedIppQueueEnum}.
     * @param userId
     *            The unique ID of the {@link User} requesting the print.
     * @param printReq
     *            The {@link DocContentPrintReq}.
     * @param istrContent
     *            The {@link InputStream} with the content.
     * @return The {@link DocContentPrintRsp}.
     * @throws DocContentPrintException
     *             When something goes wrong during printing.
     * @throws UnavailableException
     *             When PDF conversion is unavailable.
     */
    DocContentPrintRsp printDocContent(ReservedIppQueueEnum reservedQueue,
            String userId, DocContentPrintReq printReq, InputStream istrContent)
            throws DocContentPrintException, UnavailableException;

    /**
     * Checks if remote client IP address has access to a queue.
     *
     * @param queue
     *            The queue.
     * @param queueNameForLogging
     *            The queue name used for logging when errors are encountered.
     * @param clientIpAddr
     *            The IP address of the requesting user.
     * @return {@code true} when IP access to queue is allowed, {@code false}
     *         when not.
     * @throws SpException
     *             If queue is deleted.
     */
    boolean hasClientIpAccessToQueue(IppQueue queue,
            String queueNameForLogging, String clientIpAddr);

    /**
     * Checks if the reserved queue is enabled.
     *
     * @param queue
     *            The {@link ReservedIppQueueEnum}.
     * @return {@code true} when enabled.
     */
    boolean isQueueEnabled(ReservedIppQueueEnum queue);

    /**
     * Finds the {@link IppQueue} by primary key and locks database row.
     * <p>
     * Use this method to force serialization among transactions attempting to
     * update {@link IppQueue} data.
     * </p>
     *
     * @param id
     *            The primary key.
     * @return The {@link IppQueue} instance.
     */
    IppQueue lockQueue(Long id);

}
