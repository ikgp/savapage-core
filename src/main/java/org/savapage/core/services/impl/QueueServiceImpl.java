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
package org.savapage.core.services.impl;

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.EnumUtils;
import org.apache.commons.lang3.StringUtils;
import org.savapage.core.SpException;
import org.savapage.core.UnavailableException;
import org.savapage.core.UnavailableException.State;
import org.savapage.core.cometd.AdminPublisher;
import org.savapage.core.cometd.PubLevelEnum;
import org.savapage.core.cometd.PubTopicEnum;
import org.savapage.core.concurrent.ReadLockObtainFailedException;
import org.savapage.core.concurrent.ReadWriteLockEnum;
import org.savapage.core.config.ConfigManager;
import org.savapage.core.config.IConfigProp.Key;
import org.savapage.core.dao.IAttrDao;
import org.savapage.core.dao.enums.DocLogProtocolEnum;
import org.savapage.core.dao.enums.IppQueueAttrEnum;
import org.savapage.core.dao.enums.IppRoutingEnum;
import org.savapage.core.dao.enums.ReservedIppQueueEnum;
import org.savapage.core.doc.DocContent;
import org.savapage.core.doc.DocContentTypeEnum;
import org.savapage.core.fonts.InternalFontFamilyEnum;
import org.savapage.core.i18n.PhraseEnum;
import org.savapage.core.jpa.IppQueue;
import org.savapage.core.jpa.IppQueueAttr;
import org.savapage.core.json.JsonRollingTimeSeries;
import org.savapage.core.json.TimeSeriesInterval;
import org.savapage.core.print.server.DocContentPrintException;
import org.savapage.core.print.server.DocContentPrintProcessor;
import org.savapage.core.print.server.DocContentPrintReq;
import org.savapage.core.print.server.DocContentPrintRsp;
import org.savapage.core.print.server.PrintInResultEnum;
import org.savapage.core.print.server.UnsupportedPrintJobContent;
import org.savapage.core.services.QueueService;
import org.savapage.core.services.ServiceContext;
import org.savapage.core.services.helpers.ExternalSupplierInfo;
import org.savapage.core.services.helpers.MailPrintData;
import org.savapage.core.util.InetUtils;
import org.savapage.core.util.JsonHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Rijk Ravestein
 *
 */
public final class QueueServiceImpl extends AbstractService
        implements QueueService {

    /** */
    private static final Logger LOGGER =
            LoggerFactory.getLogger(QueueServiceImpl.class);

    /** */
    private static final ConfigManager CONFIG_MNGR = ConfigManager.instance();

    @Override
    public boolean isRawPrintQueue(final IppQueue queue) {
        return queue.getUrlPath()
                .equals(ReservedIppQueueEnum.RAW_PRINT.getUrlPath());
    }

    @Override
    public boolean isMailPrintQueue(final IppQueue queue) {
        return queue.getUrlPath()
                .equals(ReservedIppQueueEnum.MAILPRINT.getUrlPath());
    }

    @Override
    public boolean isWebPrintQueue(final IppQueue queue) {
        return queue.getUrlPath()
                .equals(ReservedIppQueueEnum.WEBPRINT.getUrlPath());
    }

    @Override
    public IppQueueAttr removeAttribute(final IppQueue queue,
            final IppQueueAttrEnum name) {

        final List<IppQueueAttr> attributes = queue.getAttributes();

        if (attributes != null) {

            final String dbName = name.getDbName();

            final Iterator<IppQueueAttr> iter = attributes.iterator();
            while (iter.hasNext()) {
                final IppQueueAttr attr = iter.next();
                if (attr.getName().equals(dbName)) {
                    iter.remove();
                    return attr;
                }
            }
        }
        return null;
    }

    @Override
    public IppQueueAttr getAttribute(final IppQueue queue,
            final IppQueueAttrEnum name) {

        final List<IppQueueAttr> attributes = queue.getAttributes();

        if (attributes != null) {

            final String dbName = name.getDbName();

            for (final IppQueueAttr attr : attributes) {
                if (attr.getName().equals(dbName)) {
                    return attr;
                }
            }
        }
        return null;
    }

    @Override
    public String getAttrValue(final IppQueue queue,
            final IppQueueAttrEnum name) {

        final IppQueueAttr attr = this.getAttribute(queue, name);
        if (attr != null) {
            return attr.getValue();
        }
        return null;
    }

    @Override
    public IppRoutingEnum getIppRouting(final IppQueue queue) {
        return EnumUtils.getEnum(IppRoutingEnum.class,
                this.getAttrValue(queue, IppQueueAttrEnum.IPP_ROUTING));
    }

    @Override
    public Map<String, String> getIppRoutingOptions(final IppQueue queue) {
        return JsonHelper.createStringMapOrNull(
                this.getAttrValue(queue, IppQueueAttrEnum.IPP_ROUTING_OPTIONS));
    }

    @Override
    public String getIppRoutingTarget(final IppQueue queue) {
        return this.getAttrValue(queue, IppQueueAttrEnum.IPP_ROUTING_TARGET);
    }

    @Override
    public boolean isIppRoutingQueue(final IppQueue queue) {
        final IppRoutingEnum val = this.getIppRouting(queue);
        return val != null && val == IppRoutingEnum.TERMINAL;
    }

    @Override
    public void setQueueAttrValue(final IppQueue queue,
            final IppQueueAttrEnum attrEnum, final String attrValue) {

        this.setAttrValue(ippQueueAttrDAO().findByName(queue.getId(), attrEnum),
                queue, attrEnum, attrValue);
    }

    @Override
    public boolean deleteQueueAttrValue(final IppQueue queue,
            final IppQueueAttrEnum attrEnum) {

        final IppQueueAttr attr =
                ippQueueAttrDAO().findByName(queue.getId(), attrEnum);

        if (attr == null) {
            return false;
        }
        return ippQueueAttrDAO().delete(attr);
    }

    @Override
    public void setLogicalDeleted(final IppQueue queue, final Date deletedDate,
            final String deletedBy) {
        queue.setDeleted(true);
        queue.setDeletedDate(deletedDate);
        queue.setModifiedBy(deletedBy);
        queue.setModifiedDate(deletedDate);
    }

    @Override
    public void undoLogicalDeleted(final IppQueue queue) {
        queue.setDeleted(false);
        queue.setDeletedDate(null);
    }

    @Override
    public void addJobTotals(final IppQueue queue, final Date jobDate,
            final int jobPages, final long jobBytes) {

        queue.setTotalJobs(queue.getTotalJobs().intValue() + 1);
        queue.setTotalPages(queue.getTotalPages().intValue() + jobPages);
        queue.setTotalBytes(queue.getTotalBytes().longValue() + jobBytes);

        queue.setLastUsageDate(jobDate);
    }

    @Override
    public void logPrintIn(final IppQueue queue, final Date observationTime,
            final Integer jobPages) {

        try {
            addTimeSeriesDataPoint(queue,
                    IppQueueAttrEnum.PRINT_IN_ROLLING_DAY_PAGES,
                    observationTime, jobPages);

        } catch (IOException e) {
            throw new SpException("logPrintIn failed", e);
        }

    }

    /**
     * Adds an observation to a time series.
     *
     * @param queue
     *            The {@link IppQueue} to add the observation to.
     * @param attrEnum
     *            The {@link IppQueueAttrEnum}.
     * @param observationTime
     *            The observation time.
     * @param observation
     *            The observation.
     * @throws IOException
     *             When JSON error.
     */
    private void addTimeSeriesDataPoint(final IppQueue queue,
            final IppQueueAttrEnum attrEnum, final Date observationTime,
            final Integer observation) throws IOException {

        final JsonRollingTimeSeries<Integer> statsPages =
                new JsonRollingTimeSeries<>(TimeSeriesInterval.DAY,
                        MAX_TIME_SERIES_INTERVALS_DAYS, 0);

        final IppQueueAttr attr =
                ippQueueAttrDAO().findByName(queue.getId(), attrEnum);

        String json = null;

        if (attr != null) {
            json = attr.getValue();
        }

        if (StringUtils.isNotBlank(json)) {
            statsPages.init(json);
        }

        statsPages.addDataPoint(observationTime, observation);

        setAttrValue(attr, queue, attrEnum, statsPages.stringify());
    }

    /**
     * Writes (create or update) the attribute value to the database.
     *
     * @param attr
     *            The {@link IppQueueAttr}. If {@code null} a new attribute is
     *            created.
     * @param queue
     *            The {@link IppQueue}.
     * @param attrName
     *            The {@link IppQueueAttrEnum}.
     * @param attrValue
     *            The {@link IppQueueAttr} value.
     */
    private void setAttrValue(final IppQueueAttr attr, final IppQueue queue,
            final IppQueueAttrEnum attrName, final String attrValue) {

        if (attr == null) {

            final IppQueueAttr attrNew = new IppQueueAttr();

            attrNew.setQueue(queue);

            attrNew.setName(attrName.getDbName());
            attrNew.setValue(attrValue);

            ippQueueAttrDAO().create(attrNew);

        } else {

            attr.setValue(attrValue);

            ippQueueAttrDAO().update(attr);
        }
    }

    @Override
    public void setQueueAttrValue(final IppQueue queue,
            final IppQueueAttrEnum attribute, final Boolean attrValue) {

        final boolean boolValue = BooleanUtils.isTrue(attrValue);

        final IppQueueAttr queueAttr = this.getAttribute(queue, attribute);

        if (queueAttr == null) {

            if (boolValue) {

                final IppQueueAttr attr = new IppQueueAttr();

                attr.setQueue(queue);
                attr.setName(attribute.getDbName());
                attr.setValue(ippQueueAttrDAO().getDbBooleanValue(boolValue));

                queue.getAttributes().add(attr);

                ippQueueAttrDAO().create(attr);
            }

        } else {

            final boolean currentValue =
                    ippQueueAttrDAO().getBooleanValue(queueAttr);

            if (boolValue != currentValue) {

                if (boolValue) {
                    queueAttr.setValue(
                            ippQueueAttrDAO().getDbBooleanValue(boolValue));
                    ippQueueAttrDAO().update(queueAttr);
                } else {
                    this.removeAttribute(queue, attribute);
                    ippQueueAttrDAO().delete(queueAttr);
                }
            }
        }
    }

    @Override
    public IppQueue
            getOrCreateReservedQueue(final ReservedIppQueueEnum reservedQueue) {

        IppQueue queue = ippQueueDAO().find(reservedQueue);

        if (queue == null) {

            queue = createQueueDefault(reservedQueue.getUrlPath());
            ippQueueDAO().create(queue);

        } else if (reservedQueue == ReservedIppQueueEnum.AIRPRINT
                || reservedQueue == ReservedIppQueueEnum.WEBPRINT
                || reservedQueue == ReservedIppQueueEnum.MAILPRINT
                || reservedQueue == ReservedIppQueueEnum.IPP_PRINT_INTERNET
                || reservedQueue == ReservedIppQueueEnum.WEBSERVICE) {

            /*
             * Force legacy queues to untrusted: reserved queues are untrusted
             * by nature.
             */
            if (queue.getTrusted()) {
                queue.setTrusted(Boolean.FALSE);
                ippQueueDAO().update(queue);
            }
        }

        return queue;
    }

    /**
     * Creates a default {@link IppQueue} that is untrusted.
     *
     * @param urlPath
     *            The URL path.
     * @return The {@link IppQueue}.
     */
    private static IppQueue createQueueDefault(final String urlPath) {

        final IppQueue queue = new IppQueue();

        queue.setUrlPath(urlPath);
        queue.setTrusted(Boolean.FALSE);
        queue.setCreatedBy(ServiceContext.getActor());
        queue.setCreatedDate(ServiceContext.getTransactionDate());

        return queue;
    }

    @Override
    public void lazyCreateReservedQueues() {

        final boolean hasFtpModule = ConfigManager.isFtpPrintActivated();

        for (final ReservedIppQueueEnum value : ReservedIppQueueEnum.values()) {

            if (value == ReservedIppQueueEnum.FTP && !hasFtpModule) {
                continue;
            }

            this.getOrCreateReservedQueue(value);
        }
    }

    @Override
    public ReservedIppQueueEnum getReservedQueue(final String urlPath) {

        for (final ReservedIppQueueEnum value : ReservedIppQueueEnum.values()) {
            if (value.getUrlPath().equalsIgnoreCase(urlPath)) {
                return value;
            }
        }
        return null;
    }

    @Override
    public boolean isReservedQueue(final String urlPath) {
        return this.getReservedQueue(urlPath) != null;
    }

    /**
     * Checks boolean value of queue attribute.
     *
     * @param queue
     *            The queue.
     * @param attr
     *            The attribute.
     * @param defaultValue
     *            The default value when queue attribute is not present.
     * @return Queue attribute value.
     */
    private boolean isQueueAttr(final IppQueue queue,
            final IppQueueAttrEnum attr, final boolean defaultValue) {
        final String value = this.getAttrValue(queue, attr);
        if (value != null) {
            return value.equals(IAttrDao.V_YES);
        }
        return defaultValue;
    }

    @Override
    public boolean isDocStoreJournalDisabled(final Long id) {
        final IppQueueAttr attr = ippQueueAttrDAO().findByName(id,
                IppQueueAttrEnum.JOURNAL_DISABLE);
        return ippQueueAttrDAO().getBooleanValue(attr);
    }

    @Override
    public boolean isDocStoreJournalDisabled(final IppQueue queue) {
        return this.isQueueAttr(queue, IppQueueAttrEnum.JOURNAL_DISABLE, false);
    }

    @Override
    public boolean isActiveQueue(final IppQueue queue) {

        final ReservedIppQueueEnum queueReserved =
                this.getReservedQueue(queue.getUrlPath());

        if (queueReserved == null || queueReserved.isDriverPrint()
                || queueReserved == ReservedIppQueueEnum.WEBSERVICE) {

            return !(queue.getDeleted().booleanValue()
                    || queue.getDisabled().booleanValue());
        }

        final boolean enabled;

        switch (queueReserved) {
        case MAILPRINT:
            enabled = CONFIG_MNGR.isConfigValue(Key.PRINT_IMAP_ENABLE);
            break;
        case WEBPRINT:
            enabled = CONFIG_MNGR.isConfigValue(Key.WEB_PRINT_ENABLE);
            break;
        default:
            throw new IllegalStateException(String.format("%s is not handled.",
                    queueReserved.toString()));
        }
        return enabled;
    }

    @Override
    public DocContentPrintRsp printDocContent(
            final ReservedIppQueueEnum reservedQueue, final String userId,
            final DocContentPrintReq printReq, final InputStream istrContent)
            throws DocContentPrintException, UnavailableException {

        final DocLogProtocolEnum protocol = printReq.getProtocol();
        final String originatorEmail = printReq.getOriginatorEmail();
        final String originatorIp = printReq.getOriginatorIp();
        final String title = printReq.getTitle();
        final String fileName = printReq.getFileName();
        final DocContentTypeEnum contentType = printReq.getContentType();
        final InternalFontFamilyEnum preferredOutputFont =
                printReq.getPreferredOutputFont();

        final ExternalSupplierInfo extSupplierInfo;
        if (reservedQueue == ReservedIppQueueEnum.MAILPRINT) {
            final MailPrintData data = new MailPrintData();
            data.setFromAddress(originatorEmail);
            data.setSubject(null); // for now
            extSupplierInfo = new ExternalSupplierInfo();
            extSupplierInfo.setData(data);
            extSupplierInfo.setId(printReq.getMailPrintTicket());
        } else {
            extSupplierInfo = null;
        }

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Printing [" + fileName + "] ...");
        }

        final DocContentPrintRsp printRsp = new DocContentPrintRsp();
        final IppQueue queue;

        DocContentPrintProcessor processor = null;

        boolean isAuthorized = false;
        boolean isDbReadLock = false;

        try {
            ReadWriteLockEnum.DATABASE_READONLY.tryReadLock();
            isDbReadLock = true;

            queue = ippQueueDAO().find(reservedQueue);

            if (!this.isActiveQueue(queue)) {
                final StringBuilder msg = new StringBuilder();
                msg.append("queue /").append(queue.getUrlPath());
                if (BooleanUtils.isTrue(queue.getDeleted())) {
                    msg.append(" is deleted.");
                } else if (BooleanUtils.isTrue(queue.getDisabled())) {
                    msg.append(" is disabled.");
                }
                throw new UnavailableException(State.PERMANENT, msg.toString());
            }

            if (reservedQueue != ReservedIppQueueEnum.MAILPRINT
                    && !this.hasClientIpAccessToQueue(queue, queue.getUrlPath(),
                            printReq.getOriginatorIp())) {
                final StringBuilder msg = new StringBuilder();
                msg.append("IP ").append(printReq.getOriginatorIp())
                        .append(" not allowed for queue /")
                        .append(queue.getUrlPath());
                throw new UnavailableException(State.PERMANENT, msg.toString());
            }

            processor = new DocContentPrintProcessor(queue, originatorIp, title,
                    userId);
            /*
             * If we tracked the user down by his email address, we know he
             * already exists in the database, so a lazy user insert is no issue
             * (same argument for a Web Print).
             */
            processor.processAssignedUser(userId, userId);

            isAuthorized = processor.isAuthorized();

            if (isAuthorized) {

                if (contentType != null
                        && DocContent.isSupported(contentType)) {

                    processor.setPdfProvidedIsClean(
                            printReq.isContentTypePdfClean());

                    processor.process(istrContent, extSupplierInfo, protocol,
                            originatorEmail, contentType, preferredOutputFont);

                    printRsp.setResult(PrintInResultEnum.OK);
                    printRsp.setDocumentUuid(processor.getUuidJob());

                    if (processor.getPageProps() != null) {
                        printRsp.setNumberOfPages(processor.getNumberOfPages());
                    }

                } else {
                    throw new UnsupportedPrintJobContent(
                            "File type [" + fileName + "] NOT supported.");
                }

            } else {
                printRsp.setResult(PrintInResultEnum.USER_NOT_AUTHORIZED);
                LOGGER.warn(
                        String.format("User [%s] not authorized for Queue [%s]",
                                userId, reservedQueue.getUrlPath()));
            }

        } catch (ReadLockObtainFailedException e) {
            throw new DocContentPrintException(PhraseEnum.SYS_TEMP_UNAVAILABLE
                    .uiText(ServiceContext.getLocale()));
        } catch (UnavailableException e) {
            throw e;
        } catch (Exception e) {
            if (processor != null) {
                processor.setDeferredException(e);
            } else {
                throw new SpException(e.getMessage(), e);
            }
        } finally {
            if (isDbReadLock) {
                ReadWriteLockEnum.DATABASE_READONLY.setReadLock(false);
            }
        }
        /*
         * Get the deferred exception before evaluating the error state, because
         * deferred exceptions get nullified while being evaluated.
         */
        final DocContentPrintException printException;

        if (processor.hasDeferredException()) {
            final Exception e = processor.getDeferredException();
            if (e instanceof UnavailableException) {
                throw (UnavailableException) e;
            }
            printException = new DocContentPrintException(e.getMessage(), e);
        } else {
            if (processor.isDrmViolationDetected()) {
                printException = new DocContentPrintException(
                        "Input is DRM restricted.");
            } else {
                printException = null;
            }
        }
        /*
         * Evaluate to trigger the message handling.
         */
        processor.evaluateErrorState(isAuthorized, userId);

        if (printException != null) {
            throw printException;
        }

        if (processor.isPdfRepaired()) {
            LOGGER.warn("PDF [{}] is invalid and has been repaired.",
                    printReq.getFileName());
            AdminPublisher.instance().publish(PubTopicEnum.USER,
                    PubLevelEnum.WARN, String.format("User [%s] [%s] repaired.",
                            userId, printReq.getFileName()));
        }
        return printRsp;
    }

    @Override
    public boolean hasClientIpAccessToQueue(final IppQueue queue,
            final String queueNameForLogging, final String clientIpAddr) {

        /*
         * Is IppQueue present ... and can it be used?
         */
        if (queue == null || queue.getDeleted()) {
            throw new SpException(String.format("No queue found for [%s]",
                    queueNameForLogging));
        }

        /*
         * Check if client IP address is in range of the allowed IP CIDR.
         */
        final boolean hasPrintAccessToQueue = InetUtils
                .isIpAddrInCidrRanges(queue.getIpAllowed(), clientIpAddr);

        /*
         * Logging
         */
        if (LOGGER.isDebugEnabled()) {

            final StringBuilder msg = new StringBuilder(64);

            msg.append("Queue [").append(queue.getUrlPath()).append("] ");

            if (queue.getTrusted()) {
                msg.append("TRUSTED");
            } else {
                msg.append("NOT Trusted");
            }
            msg.append(". [").append(clientIpAddr).append("] ");
            if (hasPrintAccessToQueue) {
                msg.append("ALLOWED");
            } else {
                msg.append("NOT Allowed");
            }

            LOGGER.debug(msg.toString());
        }

        return hasPrintAccessToQueue;
    }

    @Override
    public boolean isQueueEnabled(final ReservedIppQueueEnum queue) {
        return !ippQueueDAO().find(queue).getDisabled();
    }

    @Override
    public IppQueue lockQueue(final Long id) {
        return ippQueueDAO().lock(id);
    }

}
