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

import static java.nio.file.FileVisitResult.CONTINUE;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.SortedSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.mail.MessagingException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.quartz.CronExpression;
import org.savapage.core.SpException;
import org.savapage.core.circuitbreaker.CircuitBreakerException;
import org.savapage.core.config.ConfigManager;
import org.savapage.core.config.IConfigProp.Key;
import org.savapage.core.dao.enums.DaoEnumHelper;
import org.savapage.core.dao.enums.ExternalSupplierEnum;
import org.savapage.core.dao.enums.ExternalSupplierStatusEnum;
import org.savapage.core.dao.enums.PrintModeEnum;
import org.savapage.core.dao.enums.PrinterAttrEnum;
import org.savapage.core.doc.DocContent;
import org.savapage.core.doc.PdfToBooklet;
import org.savapage.core.doc.PdfToGrayscale;
import org.savapage.core.doc.store.DocStoreException;
import org.savapage.core.doc.store.DocStoreTypeEnum;
import org.savapage.core.dto.JobTicketDomainDto;
import org.savapage.core.dto.JobTicketLabelDto;
import org.savapage.core.dto.JobTicketTagDto;
import org.savapage.core.dto.JobTicketUseDto;
import org.savapage.core.dto.RedirectPrinterDto;
import org.savapage.core.imaging.EcoPrintPdfTaskPendingException;
import org.savapage.core.ipp.IppJobStateEnum;
import org.savapage.core.ipp.attribute.IppDictJobTemplateAttr;
import org.savapage.core.ipp.attribute.syntax.IppKeyword;
import org.savapage.core.ipp.client.IppConnectException;
import org.savapage.core.ipp.helpers.IppOptionMap;
import org.savapage.core.ipp.rules.IppRuleValidationException;
import org.savapage.core.jpa.AccountTrx;
import org.savapage.core.jpa.DocLog;
import org.savapage.core.jpa.PrintOut;
import org.savapage.core.jpa.Printer;
import org.savapage.core.jpa.PrinterGroup;
import org.savapage.core.jpa.PrinterGroupMember;
import org.savapage.core.jpa.User;
import org.savapage.core.outbox.OutboxInfoDto.OutboxAccountTrxInfo;
import org.savapage.core.outbox.OutboxInfoDto.OutboxJobBaseDto;
import org.savapage.core.outbox.OutboxInfoDto.OutboxJobDto;
import org.savapage.core.pdf.PdfCreateInfo;
import org.savapage.core.print.proxy.AbstractProxyPrintReq;
import org.savapage.core.print.proxy.AbstractProxyPrintReq.Status;
import org.savapage.core.print.proxy.JsonProxyPrintJob;
import org.savapage.core.print.proxy.JsonProxyPrinter;
import org.savapage.core.print.proxy.JsonProxyPrinterOpt;
import org.savapage.core.print.proxy.JsonProxyPrinterOptChoice;
import org.savapage.core.print.proxy.ProxyPrintDocReq;
import org.savapage.core.print.proxy.ProxyPrintInboxReq;
import org.savapage.core.print.proxy.ProxyPrintJobStatusMonitor;
import org.savapage.core.print.proxy.TicketJobSheetDto;
import org.savapage.core.services.JobTicketService;
import org.savapage.core.services.OutboxService;
import org.savapage.core.services.ServiceContext;
import org.savapage.core.services.helpers.DocContentPrintInInfo;
import org.savapage.core.services.helpers.ExternalSupplierInfo;
import org.savapage.core.services.helpers.JobTicketExecParms;
import org.savapage.core.services.helpers.JobTicketLabelCache;
import org.savapage.core.services.helpers.JobTicketQueueInfo;
import org.savapage.core.services.helpers.JobTicketStats;
import org.savapage.core.services.helpers.JobTicketWrapperDto;
import org.savapage.core.services.helpers.PrintSupplierData;
import org.savapage.core.services.helpers.PrinterAttrLookup;
import org.savapage.core.services.helpers.ProxyPrintCostDto;
import org.savapage.core.services.helpers.ProxyPrintInboxPattern;
import org.savapage.core.services.helpers.ThirdPartyEnum;
import org.savapage.core.services.helpers.TicketJobSheetPdfCreator;
import org.savapage.core.services.helpers.email.EmailMsgParms;
import org.savapage.core.template.dto.TemplateDtoCreator;
import org.savapage.core.template.dto.TemplateJobTicketDto;
import org.savapage.core.template.email.JobTicketCanceled;
import org.savapage.core.template.email.JobTicketCompleted;
import org.savapage.core.template.email.JobTicketEmailTemplate;
import org.savapage.core.util.DateUtil;
import org.savapage.core.util.JsonHelper;
import org.savapage.ext.papercut.PaperCutHelper;
import org.savapage.ext.papercut.PaperCutIntegrationEnum;
import org.savapage.lib.pgp.PGPBaseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonMappingException;

/**
 *
 * @author Rijk Ravestein
 *
 */
public final class JobTicketServiceImpl extends AbstractService
        implements JobTicketService {

    /**
     * .
     */
    public static final String FILENAME_EXT_JSON = "json";

    /**
     * .
     */
    private static final String FILENAME_EXT_PDF = DocContent.FILENAME_EXT_PDF;

    /**
     * The logger.
     */
    private static final Logger LOGGER =
            LoggerFactory.getLogger(JobTicketServiceImpl.class);

    /**
     *
     */
    private static final int TICKET_NUMBER_CHUNK_WIDTH = 4;

    /** */
    private static final String TICKET_NUMBER_CHUNK_SEPARATOR = "-";

    /** */
    private static final String TICKET_NUMBER_PREFIX_LABEL_SEPARATOR = "/";

    /** */
    private static final String TICKET_NUMBER_SUFFIX_REOPEN = "+";

    /** By UUID. */
    private ConcurrentHashMap<UUID, OutboxJobDto> jobTicketCache;

    /** By Ticket ID. */
    private ConcurrentHashMap<String, OutboxJobDto> jobTicketsByNumber;

    /** By Ticket Number. */
    private ConcurrentHashMap<String, OutboxJobDto> reopenedTicketCache;

    /** */
    private JobTicketQueueInfo jobTicketQueueInfo;

    /**
     * Implementation of execution pattern for proxy printing from the user
     * inbox to job ticket.
     * <p>
     * <i>Important: all tickets created by this executor have same submit
     * date.</i>
     * </p>
     */
    private static final class ProxyPrintInbox extends ProxyPrintInboxPattern {

        /** */
        private final JobTicketServiceImpl serviceImpl;

        /**
         * The date the proxy print was submitted.
         */
        private final Date submitDate;

        /**
         * The requested delivery date.
         */
        private final Date deliveryDate;

        /**
         * The label (to be pre-pended to the generated ticket number). Can be
         * {@code null} or empty.
         */
        private final String label;

        /**
         * The job tickets created.
         */
        private final List<OutboxJobDto> ticketsCreated;

        /** */
        private Set<Long> printerGroupIDs;

        /**
         * @param service
         *            The parent service.
         * @param reqDeliveryDate
         *            The requested date of delivery (can be {@code null}).
         * @param reqLabel
         *            The label (to be pre-pended to the generated ticket
         *            number). Can be {@code null} or empty.
         *
         */
        ProxyPrintInbox(final JobTicketServiceImpl service,
                final Date reqDeliveryDate, final String reqLabel) {

            this.serviceImpl = service;
            this.submitDate = ServiceContext.getTransactionDate();
            this.deliveryDate = this.serviceImpl
                    .getTicketDeliveryDate(submitDate, reqDeliveryDate);
            this.label = reqLabel;
            this.ticketsCreated = new ArrayList<>();
        }

        @Override
        protected void onInit(final User lockedUser,
                final ProxyPrintInboxReq request) {
            this.printerGroupIDs = this.serviceImpl
                    .getTicketPrinterGroupIDs(request.getPrinterName());
        }

        @Override
        protected void onExit(final User lockedUser,
                final ProxyPrintInboxReq request) {
            request.setStatus(Status.WAITING_FOR_RELEASE);
        }

        @Override
        protected File onReservePdfToGenerate(final User lockedUser) {
            return getJobTicketFile(java.util.UUID.randomUUID(),
                    FILENAME_EXT_PDF);
        }

        @Override
        protected void onPdfGenerated(final User lockedUser,
                final ProxyPrintInboxReq request,
                final PdfCreateInfo createInfo, final int chunkIndex,
                final int chunkSize) {

            final UUID uuid = UUID.fromString(FilenameUtils
                    .getBaseName(createInfo.getPdfFile().getName()));

            try {
                final OutboxJobDto dto = this.serviceImpl.addJobticketToCache(
                        lockedUser, createInfo, uuid, request, this.submitDate,
                        this.deliveryDate, this.label, chunkIndex, chunkSize);

                dto.setPrinterGroupIDs(this.printerGroupIDs);

                ticketsCreated.add(dto);

            } catch (IOException e) {
                throw new SpException(e.getMessage(), e);
            }
        }

        /**
         * @return The job tickets created.
         */
        public List<OutboxJobDto> getTicketsCreated() {
            return ticketsCreated;
        }
    }

    @Override
    public void proxyPrintPdf(final User lockedUser,
            final ProxyPrintDocReq request, final PdfCreateInfo createInfo,
            final DocContentPrintInInfo printInfo, final Date deliveryDate,
            final JobTicketLabelDto label) throws IOException {

        final UUID uuid = UUID.fromString(request.getDocumentUuid());

        /*
         * Move PDF file to ticket Outbox.
         */
        final File pdfFileTicket = getJobTicketFile(uuid, FILENAME_EXT_PDF);
        FileUtils.moveFile(createInfo.getPdfFile(), pdfFileTicket);

        createInfo.setPdfFile(pdfFileTicket);

        /*
         * Add to cache.
         */
        final LinkedHashMap<String, Integer> uuidPageCount =
                new LinkedHashMap<>();
        uuidPageCount.put(uuid.toString(), request.getNumberOfPages());

        createInfo.setUuidPageCount(uuidPageCount);

        final OutboxJobDto dto = this.addJobticketToCache(lockedUser,
                createInfo, uuid, request, ServiceContext.getTransactionDate(),
                deliveryDate, this.createTicketLabel(label), 1, 1);

        final String msgKey = "msg-user-print-jobticket-print";

        request.setStatus(Status.WAITING_FOR_RELEASE);
        request.setUserMsgKey(msgKey);
        request.setUserMsg(localize(msgKey, dto.getTicketNumber()));
    }

    /**
     * Creates sibling JSON file with proxy print information and adds Job
     * Ticket to the cache.
     *
     * @param user
     *            The requesting {@link User}.
     * @param createInfo
     *            The {@link PdfCreateInfo} with the PDF file to be printed by
     *            the Job Ticket. Is {@code null} when Copy Job Ticket.
     * @param uuid
     *            The Job Ticket {@link UUID}.
     * @param request
     *            The {@link AbstractProxyPrintReq}.
     * @param submitDate
     *            The submit date.
     * @param deliveryDate
     *            The requested date of delivery.
     * @param label
     *            The label (to be pre-pended to the generated ticket number).
     *            Can be {@code null} or empty.
     * @param chunkIndex
     *            1-based index of chunkSize;
     * @param chunkSize
     *            Total number of chunks;
     *
     * @return The Job Ticket added to the cache.
     * @throws IOException
     *             When file IO error occurs.
     */
    private OutboxJobDto addJobticketToCache(final User user,
            final PdfCreateInfo createInfo, final UUID uuid,
            final AbstractProxyPrintReq request, final Date submitDate,
            final Date deliveryDate, final String label, final int chunkIndex,
            final int chunkSize) throws IOException {

        final OutboxJobDto dto = outboxService().createOutboxJob(request,
                submitDate, deliveryDate, createInfo);

        dto.setUserId(user.getId());
        dto.setChunkIndex(Integer.valueOf(chunkIndex));
        dto.setChunkSize(Integer.valueOf(chunkSize));

        //
        final StringBuilder ticketNumber = new StringBuilder();

        if (label != null && StringUtils.isNotBlank(label.trim())) {
            ticketNumber.append(label.trim())
                    .append(TICKET_NUMBER_PREFIX_LABEL_SEPARATOR);
        }

        ticketNumber.append(this.createTicketNumber());
        dto.setTicketNumber(ticketNumber.toString());

        //
        this.addJobticketToCache(uuid, createInfo, dto);
        return dto;
    }

    /**
     * Adds job ticket to cache.
     *
     * @param uuid
     *            The Job Ticket {@link UUID}.
     * @param createInfo
     *            The {@link PdfCreateInfo} with the PDF file to be printed by
     *            the Job Ticket. Is {@code null} when Copy Job Ticket.
     * @param dto
     *            Job Ticket.
     * @throws IOException
     *             If IO error.
     */
    private void addJobticketToCache(final UUID uuid,
            final PdfCreateInfo createInfo, final OutboxJobDto dto)
            throws IOException {

        final File jsonFileTicket = getJobTicketFile(uuid, FILENAME_EXT_JSON);

        if (createInfo == null) {
            dto.setFile(jsonFileTicket.getName());
        }

        try (Writer writer = new FileWriter(jsonFileTicket);) {
            JsonHelper.write(dto, writer);
        }

        this.jobTicketCache.put(uuid, dto);
        this.jobTicketsByNumber.put(dto.getTicketNumber(), dto);

        if (this.isReopenedTicketNumber(dto.getTicketNumber())) {
            this.reopenedTicketCache.put(dto.getTicketNumber(), dto);
        }

        this.incrementStats(dto);
    }

    /**
     * Gets the full file path of a Job Ticket file.
     *
     * @param uuid
     *            The {@link UUID}
     * @param fileExt
     *            The JSON or PDF file extension.
     * @return The job ticket JSON or PDF file.
     */
    private static File getJobTicketFile(final UUID uuid,
            final String fileExt) {
        return Paths
                .get(ConfigManager.getJobTicketsHome().toString(),
                        String.format("%s.%s", uuid.toString(), fileExt))
                .toFile();
    }

    /**
     *
     * @param dto
     * @return
     */
    private static JobTicketQueueInfo.Scope getScope(final OutboxJobDto dto) {
        if (dto.isCopyJobTicket()) {
            return JobTicketQueueInfo.Scope.COPY;
        } else {
            return JobTicketQueueInfo.Scope.PRINT;
        }
    }

    /**
     * @param queueInfo
     * @param dto
     */
    private static void incrementStats(final JobTicketQueueInfo queueInfo,
            final OutboxJobDto dto) {
        queueInfo.increment(getScope(dto), JobTicketStats.create(dto));
    }

    /**
     * @param queueInfo
     * @param dto
     */
    private static void decrementStats(final JobTicketQueueInfo queueInfo,
            final OutboxJobDto dto) {
        queueInfo.decrement(getScope(dto), JobTicketStats.create(dto));
    }

    /**
     * @param dto
     */
    private void incrementStats(final OutboxJobDto dto) {
        synchronized (this.jobTicketQueueInfo) {
            incrementStats(this.jobTicketQueueInfo, dto);
        }
    }

    /**
     * @param dto
     *            The ticket.
     */
    private void decrementStats(final OutboxJobDto dto) {
        synchronized (this.jobTicketQueueInfo) {
            decrementStats(this.jobTicketQueueInfo, dto);
        }
    }

    /**
     *
     * @param wrapper
     *            The ticket wrapper.
     */
    private void updateStats(final JobTicketWrapperDto wrapper) {

        synchronized (this.jobTicketQueueInfo) {
            this.jobTicketQueueInfo.decrement(getScope(wrapper.getTicket()),
                    wrapper.getInitialStats());
            incrementStats(this.jobTicketQueueInfo, wrapper.getTicket());
        }
    }

    /**
     * Initializes job ticket cache.
     *
     * @param svc
     *            This service.
     * @param jobTicketMap
     *            Cache on UUID.
     * @param jobTicketNumbers
     *            Cache on Ticket number
     * @param reopenedTicketCache
     *            Reopened ticket cache on Ticket number.
     * @param queueInfo
     *            The queue info to update.
     * @throws IOException
     *             When IO error.
     */
    private static void initTicketCache(final JobTicketServiceImpl svc,
            final ConcurrentHashMap<UUID, OutboxJobDto> jobTicketMap,
            final ConcurrentHashMap<String, OutboxJobDto> jobTicketNumbers,
            final ConcurrentHashMap<String, OutboxJobDto> reopenedTicketCache,
            final JobTicketQueueInfo queueInfo) throws IOException {

        final Set<UUID> uuidToDelete = new HashSet<>();

        final Map<String, Set<Long>> lookupGroupIDs = new HashMap<>();

        final SimpleFileVisitor<Path> visitor = new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(final Path file,
                    final BasicFileAttributes attrs) throws IOException {

                final String filePath = file.toString();

                if (!FilenameUtils.getExtension(filePath)
                        .equalsIgnoreCase(FILENAME_EXT_JSON)) {
                    return CONTINUE;
                }

                final UUID uuid =
                        UUID.fromString(FilenameUtils.getBaseName(filePath));

                try {
                    final OutboxJobDto dto =
                            JsonHelper.read(OutboxJobDto.class, file.toFile());

                    final String lookupKey = dto.getPrinter();
                    if (!lookupGroupIDs.containsKey(lookupKey)) {
                        lookupGroupIDs.put(lookupKey,
                                svc.getTicketPrinterGroupIDs(dto.getPrinter()));
                    }
                    dto.setPrinterGroupIDs(lookupGroupIDs.get(lookupKey));

                    jobTicketMap.put(uuid, dto);
                    jobTicketNumbers.put(dto.getTicketNumber(), dto);

                    if (svc.isReopenedTicket(dto)) {
                        reopenedTicketCache.put(dto.getTicketNumber(), dto);
                    }

                    incrementStats(queueInfo, dto);

                } catch (JsonMappingException e) {
                    /*
                     * There has been a change in layout of the JSON file...
                     */
                    uuidToDelete.add(uuid);
                }
                return CONTINUE;
            }
        };

        Files.walkFileTree(ConfigManager.getJobTicketsHome(), visitor);

        /*
         * Clean up misfits?
         */
        if (!uuidToDelete.isEmpty()) {
            final Iterator<UUID> iter = uuidToDelete.iterator();
            while (iter.hasNext()) {
                final UUID uuid = iter.next();
                getJobTicketFile(uuid, FILENAME_EXT_PDF).delete();
                getJobTicketFile(uuid, FILENAME_EXT_JSON).delete();
                if (LOGGER.isWarnEnabled()) {
                    LOGGER.warn(
                            String.format("Jobticket JSON error: deleted [%s]",
                                    uuid.toString()));
                }
            }
        }
    }

    @Override
    public void start() {

        this.jobTicketCache = new ConcurrentHashMap<>();
        this.jobTicketsByNumber = new ConcurrentHashMap<>();
        this.reopenedTicketCache = new ConcurrentHashMap<>();
        this.jobTicketQueueInfo = new JobTicketQueueInfo();

        try {
            initTicketCache(this, this.jobTicketCache, this.jobTicketsByNumber,
                    this.reopenedTicketCache, this.jobTicketQueueInfo);
        } catch (IOException e) {
            throw new SpException(e.getMessage(), e);
        }

        final ConfigManager cm = ConfigManager.instance();

        JobTicketLabelCache
                .initTicketDomains(cm.getConfigValue(Key.JOBTICKET_DOMAINS));
        JobTicketLabelCache
                .initTicketUses(cm.getConfigValue(Key.JOBTICKET_USES));
        JobTicketLabelCache
                .initTicketTags(cm.getConfigValue(Key.JOBTICKET_TAGS));
    }

    @Override
    public void shutdown() {
        // noop
    }

    /**
     * Determines the ticket delivery date.
     *
     * @param submitDate
     *            The submit date
     * @param deliveryDateCandidate
     *            The delivery date candidate. Can be {@code null}.
     * @return The determined delivery date.
     */
    private Date getTicketDeliveryDate(final Date submitDate,
            final Date deliveryDateCandidate) {
        if (deliveryDateCandidate == null) {
            return submitDate;
        } else {
            return deliveryDateCandidate;
        }
    }

    /**
     * Gets set with Printer Group IDs a ticket printer is member of.
     *
     * @param printerName
     *            Printer name.
     * @return Printer Group IDs.
     */
    private Set<Long> getTicketPrinterGroupIDs(final String printerName) {
        final Set<Long> set = new HashSet<>();
        for (final PrinterGroup group : printerGroupMemberDAO()
                .getGroups(printerName)) {
            set.add(group.getId());
        }
        return set;
    }

    /**
     * Calculates the next Job Ticket delivery date.
     *
     * @param offsetDate
     *            Date offset.
     * @return Next Job Ticket delivery date.
     */
    @Override
    public Date getDeliveryDateNext(final Date offsetDate) {

        final ConfigManager cm = ConfigManager.instance();

        Date deliveryDateWlk = offsetDate;

        if (cm.isConfigValue(Key.JOBTICKET_DELIVERY_DATETIME_ENABLE)) {

            deliveryDateWlk = DateUtils.addDays(deliveryDateWlk,
                    cm.getConfigInt(Key.JOBTICKET_DELIVERY_DAYS, 0));

            deliveryDateWlk = this.getDeliveryDaysOfWeekCron()
                    .getNextValidTimeAfter(deliveryDateWlk);

            deliveryDateWlk = DateUtils.addMinutes(
                    DateUtils.truncate(deliveryDateWlk, Calendar.DAY_OF_MONTH),
                    cm.getConfigInt(Key.JOBTICKET_DELIVERY_DAY_MINUTES, 0));
        }
        return deliveryDateWlk;
    }

    /**
     * Creates a job ticket label from constituents.
     *
     * @param domain
     *            The domain (to be pre-pended to the generated ticket number).
     *            Can be {@code null} or empty.
     * @param use
     *            The use (to be pre-pended to the generated ticket number). Can
     *            be {@code null} or empty.
     * @param tag
     *            The tag (to be pre-pended to the generated ticket number). Can
     *            be {@code null} or empty.
     * @return The aggregated label (can be empty)
     */
    @Override
    public String createTicketLabel(final JobTicketLabelDto dto) {

        final StringBuilder label = new StringBuilder();

        if (dto != null) {
            if (StringUtils.isNotBlank(dto.getDomain())) {
                label.append(dto.getDomain());
            }
            if (StringUtils.isNotBlank(dto.getUse())) {
                if (label.length() > 0) {
                    label.append(TICKET_NUMBER_PREFIX_LABEL_SEPARATOR);
                }
                label.append(dto.getUse());
            }
            if (StringUtils.isNotBlank(dto.getTag())) {
                if (label.length() > 0) {
                    label.append(TICKET_NUMBER_PREFIX_LABEL_SEPARATOR);
                }
                label.append(dto.getTag());
            }
        }
        return label.toString();
    }

    @Override
    public OutboxJobDto createCopyJob(final User user,
            final ProxyPrintInboxReq request, final Date deliveryDate,
            final JobTicketLabelDto label) {

        final UUID uuid = UUID.randomUUID();
        final Date submitDate = ServiceContext.getTransactionDate();

        final OutboxJobDto dto;

        try {
            dto = this.addJobticketToCache(user, null, uuid, request,
                    submitDate,
                    this.getTicketDeliveryDate(submitDate, deliveryDate),
                    this.createTicketLabel(label), 1, 1);

            dto.setPrinterGroupIDs(
                    this.getTicketPrinterGroupIDs(request.getPrinterName()));

            final String msgKey = "msg-user-print-jobticket-copy";

            request.setStatus(Status.WAITING_FOR_RELEASE);
            request.setUserMsgKey(msgKey);
            request.setUserMsg(localize(msgKey, dto.getTicketNumber()));

        } catch (IOException e) {
            throw new SpException(e.getMessage(), e);
        }
        return dto;
    }

    @Override
    public OutboxJobDto reopenTicketForExtraCopies(final DocLog docLog)
            throws IOException, DocStoreException {

        final PrintOut printOut = docLog.getDocOut().getPrintOut();
        final PrintModeEnum printMode =
                PrintModeEnum.valueOf(printOut.getPrintMode());
        final boolean isCopyTicket = printMode == PrintModeEnum.TICKET_C;

        final String ticketNumberOrg = docLog.getExternalId();

        OutboxJobDto dto;
        DocStoreTypeEnum docStoreType = null;

        docStoreType = DocStoreTypeEnum.ARCHIVE;
        try {
            dto = docStoreService().retrieveJob(docStoreType, docLog);
        } catch (DocStoreException e) {
            docStoreType = DocStoreTypeEnum.JOURNAL;
            dto = docStoreService().retrieveJob(docStoreType, docLog);
        }

        /*
         * Reject old store format.
         */
        if (dto.getUserId() == null || //
                StringUtils.isBlank(dto.getPrinter()) || //
                StringUtils.isBlank(dto.getPrinterRedirect()) || //
                (!isCopyTicket && dto.getUuidPageCount() == null) || //
                StringUtils.isBlank(dto.getTicketNumber())) {
            throw new DocStoreException(
                    "Legacy print store format not supported.");
        }

        /*
         * INVARIANT: ticket printer printer must be present.
         */
        if (proxyPrintService().getCachedPrinter(dto.getPrinter()) == null) {
            throw new DocStoreException(String.format(
                    "Ticket Printer [%s] not present.", dto.getPrinter()));
        }

        //
        dto.setTicketReopen(Boolean.TRUE);
        dto.setSubmitTime(ServiceContext.getTransactionDate().getTime());
        dto.setExpiryTime(
                this.getDeliveryDateNext(ServiceContext.getTransactionDate())
                        .getTime());

        dto.setTicketNumber(
                ticketNumberOrg.concat(TICKET_NUMBER_SUFFIX_REOPEN));
        dto.setPrinterRedirect(null);

        dto.setArchive(Boolean.FALSE);
        dto.setCopies(0);
        dto.setSheets(0);
        dto.setCostResult(new ProxyPrintCostDto());

        if (dto.getAccountTransactions() != null) {
            dto.getAccountTransactions().setWeightTotal(0);
            final OutboxAccountTrxInfo trx =
                    dto.getAccountTransactions().getTransactions().get(0);
            trx.setWeight(0);
            trx.setWeightUnit(1);
        }

        final UUID uuid = UUID.randomUUID();
        final PdfCreateInfo createInfo;
        if (isCopyTicket) {
            createInfo = null;
        } else {
            final File pdfFileTicket = getJobTicketFile(uuid, FILENAME_EXT_PDF);
            dto.setFile(pdfFileTicket.getName());
            final File pdfFileOriginal =
                    docStoreService().retrievePdf(docStoreType, docLog);
            FileUtils.copyFile(pdfFileOriginal, pdfFileTicket);
            createInfo = new PdfCreateInfo(pdfFileTicket);
        }

        this.addJobticketToCache(uuid, createInfo, dto);

        return dto;
    }

    @Override
    public List<OutboxJobDto> proxyPrintInbox(final User lockedUser,
            final ProxyPrintInboxReq request, final Date deliveryDate,
            final JobTicketLabelDto label)
            throws EcoPrintPdfTaskPendingException {

        final ProxyPrintInbox executor = new ProxyPrintInbox(this, deliveryDate,
                this.createTicketLabel(label));

        executor.print(lockedUser, request);

        final List<OutboxJobDto> tickets = executor.getTicketsCreated();

        if (tickets.isEmpty()) {
            throw new IllegalStateException("no job tickets created");
        }

        final String msgKey;
        final String msgTxt;

        if (tickets.size() == 1) {
            msgKey = "msg-user-print-jobticket-print";
            msgTxt = localize(msgKey, tickets.get(0).getTicketNumber());
        } else {
            final StringBuilder msg = new StringBuilder();
            for (final OutboxJobDto dto : tickets) {
                if (msg.length() > 0) {
                    msg.append(", ");
                }
                // Replace soft-hyphen by hard-hyphen, so ticket is displayed as
                // one.
                msg.append(dto.getTicketNumber().replace('-', '\u2011'));
            }
            msgKey = "msg-user-print-jobticket-print-multiple";
            msgTxt = localize(msgKey, msg.toString());
        }

        request.setUserMsgKey(msgKey);
        request.setUserMsg(msgTxt);

        return tickets;
    }

    @Override
    public JobTicketQueueInfo getTicketQueueInfo() {
        synchronized (this.jobTicketQueueInfo) {
            return this.jobTicketQueueInfo.getCopy();
        }
    }

    @Override
    public List<OutboxJobDto> getTickets(final JobTicketFilter filter) {
        return filterTickets(filter.getUserId(), filter.getSearchTicketId(),
                filter.getPrinterGroupID());
    }

    @Override
    public OutboxJobDto getTicket(final String fileName) {
        final UUID uuid = uuidFromFileName(fileName);
        return this.jobTicketCache.get(uuid);
    }

    @Override
    public OutboxJobDto getTicket(final Long userId, final String fileName) {
        final OutboxJobDto dto = this.getTicket(fileName);
        if (dto != null && dto.getUserId().equals(userId)) {
            return dto;
        }
        return null;
    }

    @Override
    public int cancelTickets(final Long userId) {
        int nRemoved = 0;
        for (final OutboxJobDto dto : filterTickets(userId, null, null)) {
            if (StringUtils.isBlank(dto.getPrinterRedirect())) {
                if (cancelTicket(dto.getFile()) != null) {
                    nRemoved++;
                }
            }
        }
        return nRemoved;
    }

    @Override
    public List<String> getTicketNumbers(final JobTicketFilter filter,
            final int maxItems) {

        final Long userId = filter.getUserId();
        final String searchTicketId = filter.getSearchTicketId();

        final List<String> tickets = new ArrayList<>();

        final String searchSeq =
                StringUtils.defaultString(searchTicketId).toLowerCase();

        int nItems = 0;

        for (final OutboxJobDto dto : this.jobTicketCache.values()) {

            if (userId != null && !dto.getUserId().equals(userId)) {
                continue;
            }

            if (!searchSeq.isEmpty() && !StringUtils
                    .contains(dto.getTicketNumber().toLowerCase(), searchSeq)) {
                continue;
            }

            tickets.add(dto.getTicketNumber());

            nItems++;

            if (nItems == maxItems) {
                break;
            }
        }
        return tickets;
    }

    /**
     * Filters the pending Job Tickets.
     *
     * @param userId
     *            The {@link User} database key as filter, when {@code null}
     *            tickets from all users are selected.
     * @param searchTicketId
     *            Part of a ticket id as case-insensitive search argument.
     * @param printerGroupID
     *            See {@link PrinterGroup#getId()}.
     * @return The Job Tickets.
     */
    private List<OutboxJobDto> filterTickets(final Long userId,
            final String searchTicketId, final Long printerGroupID) {

        final List<OutboxJobDto> tickets = new ArrayList<>();

        final String searchSeq =
                StringUtils.defaultString(searchTicketId).toLowerCase();

        for (final Entry<UUID, OutboxJobDto> entry : this.jobTicketCache
                .entrySet()) {

            // Filter #1
            if (userId != null
                    && !entry.getValue().getUserId().equals(userId)) {
                continue;
            }

            // Filter #2
            if (!searchSeq.isEmpty() && !StringUtils.contains(
                    entry.getValue().getTicketNumber().toLowerCase(),
                    searchSeq)) {
                continue;
            }

            // Filter #3
            if (printerGroupID != null && printerGroupID.longValue() > 0
                    && !entry.getValue().getPrinterGroupIDs()
                            .contains(printerGroupID)) {
                continue;
            }

            /*
             * Create a new localized copy.
             */
            try {
                final OutboxJobDto dto = JsonHelper.read(OutboxJobDto.class,
                        getJobTicketFile(entry.getKey(), FILENAME_EXT_JSON));

                tickets.add(dto);

            } catch (IOException e) {

                if (LOGGER.isWarnEnabled()) {
                    LOGGER.warn(String.format("Job Ticket [%s] cannot be read.",
                            entry.getKey().toString()));
                }
            }
        }
        return tickets;
    }

    /**
     * Gets the UUID from one of the a job ticket files.
     *
     * @param fileName
     *            One of the files from the Job Ticket file pair.
     * @return The UUID.
     */
    private static UUID uuidFromFileName(final String fileName) {
        return UUID.fromString(FilenameUtils.getBaseName(fileName));
    }

    /**
     * Removes a Job Ticket.
     *
     * @param uuid
     *            The {@link UUID}.
     * @return The removed {@link OutboxJobDto}.
     */
    private OutboxJobDto removeTicket(final UUID uuid) {

        getJobTicketFile(uuid, FILENAME_EXT_JSON).delete();
        getJobTicketFile(uuid, FILENAME_EXT_PDF).delete();

        final OutboxJobDto dto = this.jobTicketCache.remove(uuid);

        this.jobTicketsByNumber.remove(dto.getTicketNumber());

        if (this.isReopenedTicketNumber(dto.getTicketNumber())) {
            this.reopenedTicketCache.remove(dto.getTicketNumber());
        }

        this.decrementStats(dto);

        return dto;
    }

    /**
     * Removes a Job Ticket and notifies the {@link OutboxService} that job was
     * completed or canceled.
     *
     * @param uuid
     *            The {@link UUID}.
     * @param isCompleted
     *            {@code true} when ticket is completed, {@code false} when
     *            ticket is canceled.
     * @return The {@link OutboxJobDto}.
     */
    private OutboxJobDto removeTicket(final UUID uuid,
            final boolean isCompleted) {

        final OutboxJobDto job = this.removeTicket(uuid);

        if (job != null) {
            if (isCompleted) {
                outboxService().onOutboxJobCompleted(job);
            } else {
                outboxService().onOutboxJobCanceled(job);
            }
        }
        return job;
    }

    @Override
    public OutboxJobDto cancelTicket(final Long userId, final String fileName) {

        final UUID uuid = uuidFromFileName(fileName);
        final OutboxJobDto dto = this.jobTicketCache.get(uuid);

        if (dto == null) {
            return dto;
        }

        if (!dto.getUserId().equals(userId)) {
            throw new IllegalArgumentException(
                    String.format("Job ticket [%s] is not owned by user [%s]",
                            uuid.toString(), userId.toString()));
        }
        return this.removeTicket(uuid, false);
    }

    @Override
    public OutboxJobDto cancelTicket(final String fileName) {
        final UUID uuid = uuidFromFileName(fileName);
        return this.removeTicket(uuid, false);
    }

    @Override
    public PrintOut getTicketPrintOut(final String fileName)
            throws FileNotFoundException {

        final UUID uuid = uuidFromFileName(fileName);
        final OutboxJobDto dto = this.jobTicketCache.get(uuid);

        if (dto == null) {
            throw new FileNotFoundException(
                    String.format("Ticket %s not found.", fileName));
        }

        final Long printOutId = dto.getPrintOutId();

        if (printOutId == null) {
            return null;
        }

        final PrintOut printOut = printOutDAO().findById(printOutId);

        if (printOut == null) {
            throw new IllegalStateException(String.format(
                    "Ticket %s: PrintOut not found.", dto.getTicketNumber()));
        }

        return printOut;
    }

    @Override
    public Boolean cancelTicketPrint(final String fileName) {

        final PrintOut printOut;

        try {
            printOut = this.getTicketPrintOut(fileName);
        } catch (FileNotFoundException e1) {
            return null;
        }

        if (printOut == null) {
            throw new IllegalStateException(String
                    .format("Ticket %s: PrintOut ID not found.", fileName));
        }

        try {
            return Boolean
                    .valueOf(proxyPrintService().cancelPrintJob(printOut));
        } catch (IppConnectException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public OutboxJobDto closeTicketPrint(final String fileName) {

        final PrintOut printOut;

        try {
            printOut = this.getTicketPrintOut(fileName);
        } catch (FileNotFoundException e) {
            return null;
        }

        final IppJobStateEnum jobState = printOutDAO().getIppJobState(printOut);
        final boolean jobCompleted =
                jobState == IppJobStateEnum.IPP_JOB_COMPLETED;

        if (!jobCompleted) {
            docLogService().updateExternalStatus(
                    printOut.getDocOut().getDocLog(),
                    ExternalSupplierStatusEnum.CANCELLED);
        }

        final UUID uuid = uuidFromFileName(fileName);
        final OutboxJobDto dto = this.removeTicket(uuid, jobCompleted);

        if (dto == null) {
            return null;
        }

        //
        dto.setIppJobState(jobState);
        return dto;
    }

    @Override
    public void updatePrinterGroupIDs() {

        final Map<String, Set<Long>> lookupGroupIDs = new HashMap<>();

        for (final OutboxJobDto dto : this.jobTicketCache.values()) {

            final String lookupKey = dto.getPrinter();

            if (!lookupGroupIDs.containsKey(lookupKey)) {
                lookupGroupIDs.put(lookupKey,
                        this.getTicketPrinterGroupIDs(dto.getPrinter()));
            }

            dto.setPrinterGroupIDs(lookupGroupIDs.get(lookupKey));
        }
    }

    @Override
    public void updatePrinterGroupIDs(final Printer printer) {

        final Set<Long> printerGroupIDs = new HashSet<>();

        for (final PrinterGroupMember member : printer
                .getPrinterGroupMembers()) {
            printerGroupIDs.add(member.getGroup().getId());
        }

        final String printerName = printer.getPrinterName();

        for (final OutboxJobDto dto : this.jobTicketCache.values()) {
            if (printerName.equalsIgnoreCase(dto.getPrinter())) {
                dto.setPrinterGroupIDs(printerGroupIDs);
            }
        }
    }

    @Override
    public boolean updateTicket(final JobTicketWrapperDto wrapper)
            throws IOException {

        final boolean result = this.updateTicket(wrapper.getTicket());
        this.updateStats(wrapper);
        return result;
    }

    @Override
    public boolean updateTicket(final OutboxJobDto dto) throws IOException {

        final UUID uuid = uuidFromFileName(dto.getFile());

        if (!this.jobTicketCache.containsKey(uuid)) {
            return false;
        }

        this.jobTicketCache.put(uuid, dto);
        this.jobTicketsByNumber.put(dto.getTicketNumber(), dto);

        if (this.isReopenedTicketNumber(dto.getTicketNumber())) {
            this.reopenedTicketCache.put(dto.getTicketNumber(), dto);
        }

        final File jsonFileTicket = getJobTicketFile(uuid, FILENAME_EXT_JSON);

        try (Writer writer = new FileWriter(jsonFileTicket);) {
            JsonHelper.write(dto, writer);
        }
        return true;
    }

    @Override
    public String notifyTicketCompletedByEmail(final OutboxJobBaseDto dto,
            final String operator, final User user, final Locale locale) {

        return notifyTicketEmail(
                new JobTicketCompleted(
                        ConfigManager.getServerCustomEmailTemplateHome(),
                        TemplateDtoCreator.templateJobTicketDto(dto, operator),
                        TemplateDtoCreator.templateUserDto(user)),
                user, locale);
    }

    @Override
    public String notifyTicketCanceledByEmail(final OutboxJobBaseDto dto,
            final String operator, final User user, final String reason,
            final Locale locale) {

        final TemplateJobTicketDto templateJobTicketDto =
                TemplateDtoCreator.templateJobTicketDto(dto, operator);

        templateJobTicketDto.setReturnMessage(reason);

        return notifyTicketEmail(new JobTicketCanceled(
                ConfigManager.getServerCustomEmailTemplateHome(),
                templateJobTicketDto, TemplateDtoCreator.templateUserDto(user)),
                user, locale);
    }

    /**
     * Notifies Job Ticket owner (by email).
     *
     * @param tpl
     *            The email template.
     * @param user
     *            The Job Ticket owner.
     * @param locale
     *            The locale for the email text.
     * @return The email address, or {@code null} when not send.
     *
     */
    private String notifyTicketEmail(final JobTicketEmailTemplate tpl,
            final User user, final Locale locale) {

        final String emailAddr = userService().getPrimaryEmailAddress(user);

        if (emailAddr == null) {
            LOGGER.warn(String.format(
                    "No primary email address found for user [%s]",
                    user.getUserId()));
            return null;
        }

        final boolean asHtml = ConfigManager.instance()
                .isConfigValue(Key.JOBTICKET_NOTIFY_EMAIL_CONTENT_TYPE_HTML);

        final EmailMsgParms emailParms =
                EmailMsgParms.create(emailAddr, tpl, locale, asHtml);

        try {
            emailService().sendEmail(emailParms);
        } catch (InterruptedException | CircuitBreakerException
                | MessagingException | IOException | PGPBaseException e) {
            throw new SpException(e.getMessage());
        }

        return emailAddr;
    }

    /**
     *
     * @param dto
     *            The job.
     * @param printer
     *            The redirect printer.
     * @return {@code true} if client-side grayscale conversion has to be
     *         applied on PDF before sending it to printer.
     */
    private static boolean isClientSideGrayscaleConversion(
            final OutboxJobDto dto, final Printer printer) {
        return dto.isMonochromeJob()
                && proxyPrintService().isColorPrinter(printer.getPrinterName())
                && printerService().isClientSideMonochrome(printer);
    }

    /**
     *
     * @param dto
     *            The job.
     * @param printer
     *            The redirect printer.
     * @return {@code true} if client-side booklet page ordering has to be
     *         applied on PDF before sending it to printer.
     */
    private static boolean isClientSideBooklet(final OutboxJobDto dto,
            final Printer printer) {
        return dto.isBookletJob()
                && printerService().isClientSideBooklet(printer);
    }

    /**
     * Executes a Job Ticket request.
     * <p>
     * </p>
     *
     * @param parms
     *            The parameters.
     * @param settleOnly
     *            {@code true} when ticket must be settled only, {@code false}
     *            when ticket job must be proxy printed.
     * @return The printed ticket or {@code null} when ticket was not found.
     * @throws IOException
     *             When IO error.
     * @throws IppConnectException
     *             When connection to CUPS fails.
     * @throws IppRuleValidationException
     *             When IPP constraint violations.
     * @throws IllegalStateException
     *             For a print job (no settlement) with ippMediaSource is
     *             {@code null}: when "media" option is not specified in Job
     *             Ticket, or printer has no "auto" choice for "media-source".
     */
    private OutboxJobDto execTicket(final JobTicketExecParms parms,
            final boolean settleOnly) throws IOException, IppConnectException,
            IppRuleValidationException {

        final UUID uuid = uuidFromFileName(parms.getFileName());
        final OutboxJobDto dto = this.jobTicketCache.get(uuid);

        if (dto == null) {
            return dto;
        }
        /*
         * INVARIANT: Job Ticket can only have one (1) associated PrintOut.
         */
        if (dto.getPrintOutId() != null) {
            throw new IllegalStateException(
                    String.format("Ticket %s already has PrintOut history.",
                            dto.getTicketNumber()));
        }

        final ConfigManager cm = ConfigManager.instance();

        /*
         * Set redirect printer.
         */
        final Printer printer = parms.getPrinter();

        dto.setPrinterRedirect(printer.getPrinterName());

        final ThirdPartyEnum extPrinterManager;

        if (paperCutService().isExtPaperCutPrint(dto.getPrinterRedirect())) {

            if (paperCutService()
                    .getPrintIntegration() == PaperCutIntegrationEnum.NONE) {
                extPrinterManager = null;
            } else {
                extPrinterManager = ThirdPartyEnum.PAPERCUT;
            }

        } else {
            extPrinterManager = null;
        }

        final User lockedUser = userService().lockUser(dto.getUserId());

        final OutboxJobDto dtoReturn;

        if (settleOnly) {

            proxyPrintService().settleJobTicket(parms.getOperator(), lockedUser,
                    dto, getJobTicketFile(uuid, FILENAME_EXT_PDF),
                    extPrinterManager);

            dtoReturn = this.removeTicket(uuid, true);

            if (cm.isConfigValue(Key.JOBTICKET_NOTIFY_EMAIL_COMPLETED_ENABLE)) {
                notifyTicketCompletedByEmail(dto, parms.getOperator(),
                        lockedUser, ConfigManager.getDefaultLocale());
            }

        } else {

            if (parms.getIppMediaSource() == null) {
                throw new IllegalStateException(String.format(
                        "Job Ticket %s to Printer %s: %s missing.",
                        dto.getTicketNumber(), printer.getPrinterName(),
                        IppDictJobTemplateAttr.ATTR_MEDIA_SOURCE));
            }

            setRedirectPrinterOptions(dto, parms);

            final List<File> files2Delete = new ArrayList<>();
            final File pdfFileToPrint = getPdfFileToPrint(dto, printer,
                    getJobTicketFile(uuid, FILENAME_EXT_PDF), files2Delete);

            try {
                final PrintOut printOut = proxyPrintService()
                        .proxyPrintJobTicket(parms.getOperator(), lockedUser,
                                dto, pdfFileToPrint, extPrinterManager)
                        .getDocOut().getPrintOut();

                dto.setPrintOutId(printOut.getId());

            } finally {
                for (final File file : files2Delete) {
                    file.delete();
                }
            }

            this.updateTicket(dto);
            dtoReturn = dto;
        }

        return dtoReturn;
    }

    /**
     * Gets the Job Ticket PDF file to be printed. Note: optional PDF
     * pre-processing is performed.
     *
     * @param dto
     *            The job.
     * @param printer
     *            The printer.
     * @param orgJobTicketPdf
     *            The original PDF.
     * @param files2Delete
     *            List where files to be deleted are added to.
     * @return The PDF file to print.
     * @throws IOException
     *             When IO errors. Note: temporary files are deleted.
     */
    private static File getPdfFileToPrint(final OutboxJobDto dto,
            final Printer printer, final File orgJobTicketPdf,
            final List<File> files2Delete) throws IOException {

        File pdfFileToPrint = orgJobTicketPdf;
        boolean exception = true;

        try {
            if (isClientSideGrayscaleConversion(dto, printer)) {
                pdfFileToPrint = new PdfToGrayscale(
                        new File(ConfigManager.getAppTmpDir()))
                                .convert(pdfFileToPrint);
                files2Delete.add(pdfFileToPrint);
            }

            if (isClientSideBooklet(dto, printer)) {
                pdfFileToPrint =
                        new PdfToBooklet(new File(ConfigManager.getAppTmpDir()))
                                .convert(pdfFileToPrint);
                files2Delete.add(pdfFileToPrint);
            }

            exception = false;

        } finally {

            if (exception) {
                for (final File file : files2Delete) {
                    file.delete();
                }
                files2Delete.clear();
            }
        }

        return pdfFileToPrint;
    }

    /**
     * Charges the printed copies to the various {@link AccountTrx}, and updates
     * the {@link DocLog} cost totals
     * <p>
     * IMPORTANT: <i>Call this method when retrying with a direct print, after a
     * failed (cancelled) ThirdParty Print operation.</i>
     * </p>
     *
     * @param trxDocLog
     *            The {@link DocLog} containing the transactions.
     * @param printedCopies
     *            The number of printed copies.
     */
    private void retryTicketPrintCharge(final DocLog trxDocLog,
            final int printedCopies) {

        final PrintSupplierData printSupplierData =
                PrintSupplierData.createFromData(trxDocLog.getExternalData());

        final BigDecimal costTotal = printSupplierData.getCostTotal();

        /*
         * Update DoLog with costs.
         */
        trxDocLog.setCost(costTotal);
        trxDocLog.setCostOriginal(costTotal);

        docLogDAO().update(trxDocLog);

        /*
         * Number of decimals for decimal scaling.
         */
        final int scale = ConfigManager.getFinancialDecimalsInDatabase();

        final BigDecimal weightTotalCost = costTotal;
        final int weightTotal = printedCopies;

        for (final AccountTrx trx : trxDocLog.getTransactions()) {

            final BigDecimal weightedCost =
                    accountingService().calcWeightedAmount(weightTotalCost,
                            weightTotal, trx.getTransactionWeight().intValue(),
                            trx.getTransactionWeightUnit().intValue(), scale);

            accountingService().chargeAccountTrxAmount(trx, weightedCost, null);
        }
    }

    /**
     * Sets the redirect printers options of the {@link OutboxJobDto}.
     *
     * @param dto
     *            The {@link OutboxJobDto}.
     * @param parms
     *            The parameters.
     *
     * @return JsonProxyPrinter The proxy printer.
     *
     * @throws IppRuleValidationException
     *             When IPP constraint violations.
     */
    private static JsonProxyPrinter setRedirectPrinterOptions(
            final OutboxJobDto dto, final JobTicketExecParms parms)
            throws IppRuleValidationException {

        // Set printer name.
        dto.setPrinterRedirect(parms.getPrinter().getPrinterName());

        // Set media-source.
        dto.getOptionValues().put(IppDictJobTemplateAttr.ATTR_MEDIA_SOURCE,
                parms.getIppMediaSource());

        // Set media-source of job sheet.
        dto.setMediaSourceJobSheet(parms.getIppMediaSourceJobSheet());

        //
        String ippKeywordWlk;

        /*
         * Set or remove output-bin.
         */
        ippKeywordWlk = IppDictJobTemplateAttr.ATTR_OUTPUT_BIN;
        if (parms.getIppOutputBin() == null) {
            dto.getOptionValues().remove(ippKeywordWlk);
        } else {
            dto.getOptionValues().put(ippKeywordWlk, parms.getIppOutputBin());
        }

        /*
         * Set or remove jog-offset.
         */
        ippKeywordWlk =
                IppDictJobTemplateAttr.ORG_SAVAPAGE_ATTR_FINISHINGS_JOG_OFFSET;

        if (parms.getIppOutputBin() == null
                || parms.getIppJogOffset() == null) {
            dto.getOptionValues().remove(ippKeywordWlk);
        } else {
            dto.getOptionValues().put(ippKeywordWlk, parms.getIppJogOffset());
        }

        final JsonProxyPrinter jsonPrinter = proxyPrintService()
                .getCachedPrinter(parms.getPrinter().getPrinterName());

        /*
         * Validate IPP constraint rules.
         */
        final Locale localeWrk;

        if (parms.getLocale() == null) {
            localeWrk = Locale.getDefault();
        } else {
            localeWrk = parms.getLocale();
        }

        final String msg = proxyPrintService().validateContraintsMsg(
                jsonPrinter, dto.getOptionValues(), localeWrk);

        if (msg != null) {
            throw new IppRuleValidationException(msg);
        }

        return jsonPrinter;
    }

    @Override
    public OutboxJobDto retryTicketPrint(final JobTicketExecParms parms)
            throws IOException, IppConnectException,
            IppRuleValidationException {

        final OutboxJobDto dto = this.getTicket(parms.getFileName());

        // May be some other operator already closed the ticket.
        if (dto == null) {
            return null;
        }

        final User user = userDAO().findById(dto.getUserId());

        /*
         * Set dto before creating the print request.
         */
        final JsonProxyPrinter jsonPrinter =
                setRedirectPrinterOptions(dto, parms);

        // Create print request
        final AbstractProxyPrintReq request = proxyPrintService()
                .createProxyPrintDocReq(user, dto, PrintModeEnum.TICKET);

        final UUID ticketUuid = uuidFromFileName(parms.getFileName());

        //
        final PrintOut printOut = printOutDAO().findById(dto.getPrintOutId());

        final List<File> files2Delete = new ArrayList<>();
        final File pdfFileToPrint = getPdfFileToPrint(dto,
                printOut.getPrinter(),
                getJobTicketFile(ticketUuid, FILENAME_EXT_PDF), files2Delete);

        try {

            final PdfCreateInfo createInfo = new PdfCreateInfo(pdfFileToPrint);
            createInfo.setBlankFillerPages(dto.getFillerPages());

            // Re-use job name.
            final String printJobTitle =
                    printOut.getDocOut().getDocLog().getTitle();

            request.setJobName(printJobTitle);

            /*
             * Update DocLog External Supplier status?
             */
            final DocLog docLog = printOut.getDocOut().getDocLog();

            // current
            final ExternalSupplierEnum extSupplierCurrent =
                    DaoEnumHelper.getExtSupplier(docLog);

            final ExternalSupplierStatusEnum extSupplierStatusCurrent =
                    DaoEnumHelper.getExtSupplierStatus(docLog);

            // retry
            final ThirdPartyEnum extPrintManagerRetry = proxyPrintService()
                    .getExtPrinterManager(parms.getPrinter().getPrinterName());

            final ExternalSupplierEnum extSupplierRetry;
            final ExternalSupplierStatusEnum extSupplierStatusRetry;

            final String documentTitleRetry;

            if (extPrintManagerRetry == null) {

                documentTitleRetry = null;

                if (extSupplierCurrent == ExternalSupplierEnum.SAVAPAGE) {
                    extSupplierRetry = null;
                    extSupplierStatusRetry = null;

                    retryTicketPrintCharge(docLog, request.getNumberOfCopies());

                } else {
                    extSupplierRetry = extSupplierCurrent;
                    extSupplierStatusRetry = ExternalSupplierStatusEnum.PENDING;
                }

            } else if (extPrintManagerRetry == ThirdPartyEnum.PAPERCUT) {

                final ExternalSupplierInfo supplierInfo;

                if (extSupplierCurrent == null
                        || extSupplierCurrent == ExternalSupplierEnum.SAVAPAGE) {

                    supplierInfo = paperCutService()
                            .createExternalSupplierInfo(request);
                    extSupplierRetry = ExternalSupplierEnum.SAVAPAGE;

                } else {
                    throw new IllegalStateException(
                            String.format("%s [%s] is not handled.",
                                    extSupplierCurrent.getClass()
                                            .getSimpleName(),
                                    extSupplierCurrent.toString()));
                }

                /*
                 * Prepare for PaperCut in retry mode.
                 */
                paperCutService().prepareForExtPaperCutRetry(request,
                        supplierInfo, PrintModeEnum.PUSH);

                documentTitleRetry = request.getJobName();
                extSupplierStatusRetry =
                        PaperCutHelper.getInitialPendingJobStatus();

            } else {
                throw new IllegalStateException(
                        String.format("%s [%s] is not handled.",
                                extPrintManagerRetry.getClass().getSimpleName(),
                                extPrintManagerRetry.toString()));
            }

            /*
             * Update any changes.
             */
            if (documentTitleRetry != null
                    || extSupplierCurrent != extSupplierRetry
                    || extSupplierStatusCurrent != extSupplierStatusRetry) {

                docLogDAO().updateExtSupplier(docLog.getId(), extSupplierRetry,
                        extSupplierStatusRetry, documentTitleRetry);
            }

            /*
             * CUPS Print.
             */
            final JsonProxyPrintJob printJob =
                    proxyPrintService().proxyPrintJobTicketResend(request, dto,
                            jsonPrinter, user.getUserId(), createInfo);
            /*
             * Update PrintOut with new Printer and CUPS job data.
             */
            printOutDAO().updateCupsJobPrinter(dto.getPrintOutId(),
                    parms.getPrinter(), printJob, request.getOptionValues());

            ServiceContext.getDaoContext().commit();

            // Notify the CUPS job status monitor of this new PrintOut.
            ProxyPrintJobStatusMonitor.notifyPrintOut(
                    parms.getPrinter().getPrinterName(), printJob);

            // Update the ticket.
            this.updateTicket(dto);

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(String.format(
                        "retryTicketPrint: COMMIT PrintOut ID [%d] "
                                + "printer [%s] " + "job [%d] status [%s]",
                        dto.getPrintOutId().longValue(),
                        parms.getPrinter().getPrinterName(),
                        printJob.getJobId().intValue(),
                        IppJobStateEnum.asEnum(printJob.getJobState())));
            }
        } finally {
            for (final File file : files2Delete) {
                file.delete();
            }
        }
        return dto;
    }

    @Override
    public OutboxJobDto printTicket(final JobTicketExecParms parms)
            throws IOException, IppConnectException,
            IppRuleValidationException {

        return execTicket(parms, false);
    }

    @Override
    public OutboxJobDto settleTicket(final String operator,
            final Printer printer, final String fileName) throws IOException {

        final JobTicketExecParms parms = new JobTicketExecParms();

        parms.setOperator(operator);
        parms.setPrinter(printer);
        parms.setFileName(fileName);

        try {
            return execTicket(parms, true);
        } catch (IppConnectException | IppRuleValidationException e) {
            // This is not supposed to happen, because no proxy print is done,
            // and rule validation is irrelevant.
            throw new IllegalStateException(e.getMessage());
        }
    }

    /**
     * Checks if capability of printer matches the IPP option value requested.
     *
     * @param optionsRequested
     *            The requested {@link IppOptionMap}.
     * @param ippOptionKey
     *            The IPP option key.
     * @param printerOptionLookup
     *            The lookup of printer IPP options.
     * @return {@code true} if job does not request the IPP option, or when
     *         printer has capability to fulfill job IPP option value.
     */
    private static boolean isPrinterCapabilityMatch(
            final IppOptionMap optionsRequested, final String ippOptionKey,
            final Map<String, JsonProxyPrinterOpt> printerOptionLookup) {

        final String jobOptionValue =
                optionsRequested.getOptionValue(ippOptionKey);

        if (jobOptionValue == null) {
            // Option not requested: match.
            return true;
        }

        final JsonProxyPrinterOpt printerOptionValue =
                printerOptionLookup.get(ippOptionKey);

        if (printerOptionValue == null) {
            // Printer does not have option: no match.
            return false;
        }

        for (final JsonProxyPrinterOptChoice choice : printerOptionValue
                .getChoices()) {
            if (choice.getChoice().equals(jobOptionValue)) {
                // Printer has option value: match.
                return true;
            }
        }
        // Printer does not have option value: no match.
        return false;
    }

    @Override
    public List<RedirectPrinterDto> getRedirectPrinters(final OutboxJobDto job,
            final IppOptionMap ippOptionFilter, final Locale locale) {

        final Printer jobTicketPrinter =
                printerDAO().findByName(job.getPrinter());

        final List<RedirectPrinterDto> printerList = new ArrayList<>();

        final String groupName = printerService().getAttributeValue(
                jobTicketPrinter, PrinterAttrEnum.JOBTICKET_PRINTER_GROUP);

        if (StringUtils.isBlank(groupName)) {
            return printerList;
        }

        final PrinterGroup printerGroup = ServiceContext.getDaoContext()
                .getPrinterGroupDao().findByName(groupName);

        if (printerGroup == null) {
            return printerList;
        }

        final IppOptionMap optionMap = job.createIppOptionMap();

        final boolean colorJob = optionMap.isColorJob();
        final boolean duplexJob = optionMap.isDuplexJob();

        int iPreferred = -1;
        int iPrinter = 0;

        // Check compatibility.
        final String requestedMediaForJob =
                job.getOptionValues().get(IppDictJobTemplateAttr.ATTR_MEDIA);

        final String requestedMediaTypeForJob = job.getOptionValues()
                .get(IppDictJobTemplateAttr.ATTR_MEDIA_TYPE);

        for (final PrinterGroupMember member : printerGroup.getMembers()) {

            final Printer printer = member.getPrinter();

            if (printer.getDisabled().booleanValue()
                    || printer.getDeleted().booleanValue()) {
                continue;
            }

            final JsonProxyPrinter cupsPrinter = proxyPrintService()
                    .getCachedPrinter(printer.getPrinterName());

            if (cupsPrinter == null) {
                throw new IllegalStateException(
                        String.format("Printer [%s] not found in cache.",
                                printer.getPrinterName()));
            }

            // Duplex printer?
            if (duplexJob && !cupsPrinter.getDuplexDevice()) {
                continue;
            }

            // Color printer?
            final boolean colorPrinter = cupsPrinter.getColorDevice();

            if (colorJob && !colorPrinter) {
                continue;
            }

            final Map<String, JsonProxyPrinterOpt> printerOptionlookup =
                    cupsPrinter.getOptionsLookup();

            // Finishings
            final String[][] finishingOptionsToCheck =
                    IppDictJobTemplateAttr.ORG_SAVAPAGE_ATTR_FINISHINGS_V_NONE;

            final IppOptionMap optionsRequested = job.createIppOptionMap();

            boolean isMatch = true;

            for (final String[] ippOptionCheck : finishingOptionsToCheck) {

                if (!isPrinterCapabilityMatch(optionsRequested,
                        ippOptionCheck[0], printerOptionlookup)) {
                    isMatch = false;
                    break;
                }
            }

            if (!isMatch) {
                continue;
            }

            // If media-type requested, find a match.
            final JsonProxyPrinterOptChoice mediaTypeOptChoice;

            if (requestedMediaTypeForJob == null) {
                mediaTypeOptChoice = null;
            } else {
                final JsonProxyPrinterOpt mediaType =
                        this.localizePrinterOpt(printerOptionlookup,
                                IppDictJobTemplateAttr.ATTR_MEDIA_TYPE, locale);
                if (mediaType == null) {
                    continue;
                }
                mediaTypeOptChoice =
                        mediaType.getChoice(requestedMediaTypeForJob);
                if (mediaTypeOptChoice == null) {
                    continue;
                }
            }

            // Extra filter
            if (!ippOptionFilter.areOptionValuesValid(printerOptionlookup)) {
                continue;
            }

            // Find the media-source
            final JsonProxyPrinterOpt mediaSource = printerOptionlookup
                    .get(IppDictJobTemplateAttr.ATTR_MEDIA_SOURCE);

            if (mediaSource == null) {
                LOGGER.warn(String.format("[%s] not found for printer [%s]",
                        IppDictJobTemplateAttr.ATTR_MEDIA_SOURCE,
                        printer.getPrinterName()));
                continue;
            }

            // Collate
            if (job.getCopies() > 1 && job.getPages() > 1) {

                final JsonProxyPrinterOpt collateOpt = printerOptionlookup
                        .get(IppDictJobTemplateAttr.ATTR_SHEET_COLLATE);

                if (collateOpt != null) {
                    final String collateChoice;
                    if (job.isCollate()) {
                        collateChoice = IppKeyword.SHEET_COLLATE_COLLATED;
                    } else {
                        collateChoice = IppKeyword.SHEET_COLLATE_UNCOLLATED;
                    }
                    if (collateOpt.getChoice(collateChoice) == null) {
                        continue;
                    }
                }
            }
            //
            final RedirectPrinterDto redirectPrinter = new RedirectPrinterDto();

            redirectPrinter.setId(printer.getId());
            redirectPrinter.setName(printer.getDisplayName());
            redirectPrinter.setDeviceUri(cupsPrinter.getDeviceUri().toString());
            redirectPrinter.setMediaSourceOpt(mediaSource);

            if (mediaTypeOptChoice != null) {
                redirectPrinter.setMediaTypeOptChoice(mediaTypeOptChoice);
            }

            final PrinterAttrLookup printerAttrLookup =
                    new PrinterAttrLookup(printer);

            redirectPrinter.setMediaSourceMediaMap(printerService()
                    .getMediaSourceMediaMap(printerAttrLookup, mediaSource));

            // Main job.
            redirectPrinter.setMediaSourceOptChoice(
                    printerService().findMediaSourceForMedia(printerAttrLookup,
                            mediaSource, requestedMediaForJob, null));

            // Job Sheet.
            final TicketJobSheetDto jobSheetDto =
                    this.getTicketJobSheet(job.createIppOptionMap());

            if (jobSheetDto.isEnabled()) {
                redirectPrinter.setMediaSourceJobSheetOptChoice(printerService()
                        .findMediaSourceForMedia(printerAttrLookup, mediaSource,
                                jobSheetDto.getMediaOption(), printerService()
                                        .getJobSheetsMediaSources(printer)));
            }

            // Find the output-bin
            final JsonProxyPrinterOpt outputBin =
                    this.localizePrinterOpt(printerOptionlookup,
                            IppDictJobTemplateAttr.ATTR_OUTPUT_BIN, locale);

            if (outputBin != null) {

                redirectPrinter.setOutputBinOpt(outputBin);
                redirectPrinter.setOutputBinOptChoice(
                        this.getPrinterOptChoiceDefault(outputBin));

                final JsonProxyPrinterOpt jogOffset = this.localizePrinterOpt(
                        printerOptionlookup,
                        IppDictJobTemplateAttr.ORG_SAVAPAGE_ATTR_FINISHINGS_JOG_OFFSET,
                        locale);

                if (jogOffset != null) {
                    redirectPrinter.setJogOffsetOpt(jogOffset);
                    redirectPrinter.setJogOffsetOptChoice(
                            this.getPrinterOptChoiceDefault(jogOffset));
                }
            }

            printerList.add(redirectPrinter);

            if (iPreferred < 0) {
                if (colorJob) {
                    if (colorPrinter) {
                        iPreferred = iPrinter;
                    }
                } else if (!colorPrinter) {
                    iPreferred = iPrinter;
                }
            }
            iPrinter++;
        }

        if (!printerList.isEmpty() && iPreferred >= 0) {
            printerList.get(iPreferred).setPreferred(true);
        }

        return printerList;
    }

    /**
     * Localizes a printer option, by returning a copy.
     *
     * @param printerOptionlookup
     * @param ippKeyword
     *            The IPP option keyword.
     * @param locale
     *            The locale.
     * @return The printer options, or {@code null} when not found.
     */
    private JsonProxyPrinterOpt localizePrinterOpt(
            final Map<String, JsonProxyPrinterOpt> printerOptionlookup,
            final String ippKeyword, final Locale locale) {

        final JsonProxyPrinterOpt optWlk = printerOptionlookup.get(ippKeyword);

        if (optWlk == null) {
            return null;
        }

        final JsonProxyPrinterOpt optLocalized = optWlk.copy();
        proxyPrintService().localizePrinterOpt(locale, optLocalized);

        return optLocalized;
    }

    /**
     *
     * @param printerOpt
     *            The printer option.
     * @return {@code null} when not found.
     */
    private JsonProxyPrinterOptChoice
            getPrinterOptChoiceDefault(final JsonProxyPrinterOpt printerOpt) {

        for (final JsonProxyPrinterOptChoice choice : printerOpt.getChoices()) {
            if (choice.getChoice().equals(printerOpt.getDefchoiceIpp())) {
                return choice;
            }
        }
        return null;
    }

    @Override
    public RedirectPrinterDto getRedirectPrinter(final String fileName,
            final IppOptionMap optionFilter, final Locale locale) {

        final OutboxJobDto job = this.getTicket(fileName);

        if (job == null) {
            return null;
        }

        final List<RedirectPrinterDto> printers =
                this.getRedirectPrinters(job, optionFilter, locale);

        if (printers == null || printers.isEmpty()) {
            return null;
        }

        final int index;

        if (printers.size() == 1) {
            index = 0;
        } else {
            index = new Random().nextInt(printers.size());
        }
        return printers.get(index);
    }

    @Override
    public String createTicketNumber() {
        return chunkFormatted(
                shuffle(Long.toHexString(DateUtil.uniqueCurrentTime())),
                TICKET_NUMBER_CHUNK_WIDTH, TICKET_NUMBER_CHUNK_SEPARATOR)
                        .toUpperCase();
    }

    /**
     * Shuffles characters in a string.
     *
     * @param input
     *            The string to shuffle.
     * @return The shuffled {@link StringBuilder} result.
     */
    private static StringBuilder shuffle(final String input) {

        final List<Character> characters = new ArrayList<Character>();

        for (final char c : input.toCharArray()) {
            characters.add(c);
        }

        final StringBuilder shuffled = new StringBuilder(input.length());

        while (characters.size() != 0) {
            final int randPicker = (int) (Math.random() * characters.size());
            shuffled.append(characters.remove(randPicker));
        }
        return shuffled;
    }

    /**
     * Formats a string by inserting a separator between substring chunks.
     *
     * @param str
     *            The {@link StringBuilder} to format.
     * @param chunkWidth
     *            The character width of the substring chunks.
     * @param chunkSeparator
     *            The separator between chunks.
     * @return The formatted string.
     */
    private static String chunkFormatted(final StringBuilder str,
            final int chunkWidth, final String chunkSeparator) {

        int idx = str.length() - chunkWidth;

        while (idx > 0) {
            str.insert(idx, chunkSeparator);
            idx = idx - chunkWidth;
        }
        return str.toString();
    }

    @Override
    public File createTicketJobSheet(final String user,
            final OutboxJobDto jobDto, final TicketJobSheetDto jobSheetDto) {

        return new TicketJobSheetPdfCreator(user, jobDto, jobSheetDto).create();
    }

    @Override
    public int getJobTicketQueueSize() {
        if (this.jobTicketCache == null) {
            return 0;
        }
        return this.jobTicketCache.size();
    }

    @Override
    public TicketJobSheetDto getTicketJobSheet(final IppOptionMap options) {
        final TicketJobSheetDto dto = new TicketJobSheetDto();

        final String value = options.getOptionValue(
                IppDictJobTemplateAttr.ORG_SAVAPAGE_ATTR_JOB_SHEETS);

        final TicketJobSheetDto.Sheet sheet;

        if (value == null) {
            sheet = TicketJobSheetDto.Sheet.NONE;
        } else {
            switch (value) {
            case IppKeyword.ORG_SAVAPAGE_ATTR_JOB_SHEETS_NONE:
                sheet = TicketJobSheetDto.Sheet.NONE;
                break;

            case IppKeyword.ORG_SAVAPAGE_ATTR_JOB_SHEETS_START:
                sheet = TicketJobSheetDto.Sheet.START;
                break;

            case IppKeyword.ORG_SAVAPAGE_ATTR_JOB_SHEETS_END:
                sheet = TicketJobSheetDto.Sheet.END;
                break;

            default:
                sheet = TicketJobSheetDto.Sheet.NONE;
                final String msg = String.format(
                        "%s: unknown value [%s], using [%s]",
                        IppDictJobTemplateAttr.ORG_SAVAPAGE_ATTR_JOB_SHEETS,
                        value, IppKeyword.ORG_SAVAPAGE_ATTR_JOB_SHEETS_NONE);
                LOGGER.warn(msg);
            }
        }

        dto.setSheet(sheet);

        dto.setMediaOption(options.getOptionValue(
                IppDictJobTemplateAttr.ORG_SAVAPAGE_ATTR_JOB_SHEETS_MEDIA));

        return dto;
    }

    @Override
    public Collection<JobTicketDomainDto> getTicketDomainsByName() {
        return JobTicketLabelCache.getTicketDomainsByName();
    }

    @Override
    public Collection<JobTicketUseDto> getTicketUsesByName() {
        return JobTicketLabelCache.getTicketUsesByName();
    }

    @Override
    public Collection<JobTicketTagDto> getTicketTagsByName() {
        return JobTicketLabelCache.getTicketTagsByName();
    }

    @Override
    public String getTicketNumberLabel(final String ticketNumber) {
        if (ticketNumber.indexOf(TICKET_NUMBER_PREFIX_LABEL_SEPARATOR) < 0) {
            return null;
        }
        return StringUtils.substringBeforeLast(ticketNumber,
                TICKET_NUMBER_PREFIX_LABEL_SEPARATOR);
    }

    @Override
    public SortedSet<Integer> getDeliveryDaysOfWeek() {
        return DateUtil.getWeekDayOrdinals(this.getDeliveryDaysOfWeekCron());
    }

    /**
     * @return CRON expression of valid Job Ticket delivery days.
     */
    private CronExpression getDeliveryDaysOfWeekCron() {
        try {
            return new CronExpression(String.format("0 0 0 ? * %s",
                    ConfigManager.instance().getConfigValue(
                            Key.JOBTICKET_DELIVERY_DAYS_OF_WEEK)));
        } catch (ParseException e) {
            throw new IllegalStateException(e.getMessage());
        }
    }

    @Override
    public boolean isTicketNumberPresent(final String number) {
        return this.jobTicketsByNumber.containsKey(number);
    }

    @Override
    public boolean isReopenedTicket(final OutboxJobDto job) {
        return BooleanUtils.isTrue(job.getTicketReopen());
    }

    @Override
    public boolean isReopenedTicketNumber(final String ticketNumber) {
        return ticketNumber != null
                && ticketNumber.endsWith(TICKET_NUMBER_SUFFIX_REOPEN);
    }

    @Override
    public boolean isTicketReopened(final String ticketNumber) {
        return this.reopenedTicketCache
                .containsKey(ticketNumber.concat(TICKET_NUMBER_SUFFIX_REOPEN));
    }

}
