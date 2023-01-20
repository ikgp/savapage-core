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
package org.savapage.core.print.server;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.savapage.core.PostScriptDrmException;
import org.savapage.core.SpException;
import org.savapage.core.cometd.AdminPublisher;
import org.savapage.core.cometd.PubLevelEnum;
import org.savapage.core.cometd.PubTopicEnum;
import org.savapage.core.config.ConfigManager;
import org.savapage.core.config.IConfigProp;
import org.savapage.core.config.IConfigProp.Key;
import org.savapage.core.config.OnOffEnum;
import org.savapage.core.dao.PrinterDao;
import org.savapage.core.dao.UserDao;
import org.savapage.core.dao.enums.ACLOidEnum;
import org.savapage.core.dao.enums.DocLogProtocolEnum;
import org.savapage.core.dao.enums.IppRoutingEnum;
import org.savapage.core.dao.enums.ReservedIppQueueEnum;
import org.savapage.core.dao.helpers.IppQueueHelper;
import org.savapage.core.doc.DocContent;
import org.savapage.core.doc.DocContentToPdfException;
import org.savapage.core.doc.DocContentTypeEnum;
import org.savapage.core.doc.DocInputStream;
import org.savapage.core.doc.IDocFileConverter;
import org.savapage.core.doc.IPostScriptConverter;
import org.savapage.core.doc.IStreamConverter;
import org.savapage.core.doc.PdfRepair;
import org.savapage.core.doc.PdfToDecrypted;
import org.savapage.core.doc.PdfToPrePress;
import org.savapage.core.doc.PsToImagePdf;
import org.savapage.core.doc.store.DocStoreBranchEnum;
import org.savapage.core.doc.store.DocStoreException;
import org.savapage.core.doc.store.DocStoreTypeEnum;
import org.savapage.core.fonts.InternalFontFamilyEnum;
import org.savapage.core.i18n.PhraseEnum;
import org.savapage.core.ipp.routing.IppRoutingListener;
import org.savapage.core.jpa.Device;
import org.savapage.core.jpa.DocLog;
import org.savapage.core.jpa.IppQueue;
import org.savapage.core.jpa.PrintIn;
import org.savapage.core.jpa.Printer;
import org.savapage.core.jpa.User;
import org.savapage.core.pdf.IPdfPageProps;
import org.savapage.core.pdf.PdfAbstractException;
import org.savapage.core.pdf.PdfDocumentFonts;
import org.savapage.core.pdf.PdfPasswordException;
import org.savapage.core.pdf.PdfSecurityException;
import org.savapage.core.pdf.PdfUnsupportedException;
import org.savapage.core.pdf.PdfValidityException;
import org.savapage.core.pdf.SpPdfPageProps;
import org.savapage.core.print.proxy.ProxyPrintException;
import org.savapage.core.services.AccessControlService;
import org.savapage.core.services.DeviceService;
import org.savapage.core.services.DocLogService;
import org.savapage.core.services.DocStoreService;
import org.savapage.core.services.InboxService;
import org.savapage.core.services.ProxyPrintService;
import org.savapage.core.services.QueueService;
import org.savapage.core.services.ServiceContext;
import org.savapage.core.services.UserService;
import org.savapage.core.services.helpers.DocContentPrintInInfo;
import org.savapage.core.services.helpers.ExternalSupplierInfo;
import org.savapage.core.services.helpers.PdfRepairEnum;
import org.savapage.core.system.PdfFontsErrorValidator;
import org.savapage.core.users.conf.UserAliasList;
import org.savapage.core.util.FileSystemHelper;
import org.savapage.core.util.Messages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.itextpdf.text.ExceptionConverter;

/**
 * Processes a document print request. The document can be PDF, PostScripts,
 * {@link CupsCommandFile#FIRST_LINE_SIGNATURE}, or any other supported format.
 *
 * @author Rijk Ravestein
 *
 */
public final class DocContentPrintProcessor {

    /** */
    private static final Logger LOGGER =
            LoggerFactory.getLogger(DocContentPrintProcessor.class);

    /** */
    private static final AccessControlService ACCESS_CONTROL_SERVICE =
            ServiceContext.getServiceFactory().getAccessControlService();
    /** */
    private static final DeviceService DEVICE_SERVICE =
            ServiceContext.getServiceFactory().getDeviceService();
    /** */
    private static final DocLogService DOC_LOG_SERVICE =
            ServiceContext.getServiceFactory().getDocLogService();
    /** */
    private static final DocStoreService DOC_STORE_SERVICE =
            ServiceContext.getServiceFactory().getDocStoreService();

    /** */
    private static final InboxService INBOX_SERVICE =
            ServiceContext.getServiceFactory().getInboxService();
    /** */
    private static final ProxyPrintService PROXYPRINT_SERVICE =
            ServiceContext.getServiceFactory().getProxyPrintService();
    /** */
    private static final QueueService QUEUE_SERVICE =
            ServiceContext.getServiceFactory().getQueueService();
    /** */
    private static final UserService USER_SERVICE =
            ServiceContext.getServiceFactory().getUserService();

    /** */
    private static final int BUFFER_SIZE = 4096;

    /** */
    private java.util.UUID uuidJob = java.util.UUID.randomUUID();

    /**
     * The number of content bytes read from the input stream.
     */
    private long inputByteCount = 0;

    /** */
    private byte[] readAheadInputBytes = null;

    /** */
    private SpPdfPageProps pageProps = null;

    /** */
    private boolean drmViolationDetected = false;

    /** */
    private boolean drmRestricted = false;

    /**
     * {@code null} if no PDF document.
     */
    private PdfRepairEnum pdfRepair;

    /**
     * If {@code true} the provided {@link DocContentTypeEnum#PDF} is clean.
     */
    private boolean pdfProvidedIsClean = false;

    /** */
    private boolean pdfToCairo = false;

    /** */
    private User userDb = null;

    /** */
    private String uidTrusted = null;

    /** */
    private String mimetype;

    /** */
    private String signatureString = null;

    /** */
    private final IppQueue queue;

    /** */
    private final ReservedIppQueueEnum reservedQueue;

    /**
     * The authenticated WebApp user: {@code null} if not present.
     */
    private final String authWebAppUser;

    /**
     * The exception which occurred during processing the request.
     */
    private Exception deferredException = null;

    /** */
    private String assignedUserId = null;

    /** */
    private String jobName;

    /** */
    private final String originatorIp;

    /** */
    private IppRoutingListener ippRoutinglistener;

    /** */
    private String originatorEmail;

    /**
     * {@link DocLog} to attach {@link PrintIn} to. Can be {@code null}.
     */
    private DocLog printInParent;

    /**
     * Creates a print server request.
     *
     * @param queue
     *            The queue to print to.
     * @param originatorIp
     *            The IP address of the requesting client.
     * @param jobName
     *            The name of the print job.
     * @param authWebAppUser
     *            The authenticated WebApp user: {@code null} if not present.
     */
    public DocContentPrintProcessor(final IppQueue queue,
            final String originatorIp, final String jobName,
            final String authWebAppUser) {

        this.jobName = jobName;
        this.queue = queue;
        this.originatorIp = originatorIp;
        this.authWebAppUser = authWebAppUser;

        if (this.queue == null) {
            this.reservedQueue = null;
        } else {
            this.reservedQueue = QUEUE_SERVICE
                    .getReservedQueue(this.getQueue().getUrlPath());
        }
    }

    /**
     * @return Listener.
     */
    public IppRoutingListener getIppRoutinglistener() {
        return ippRoutinglistener;
    }

    /**
     * @param listener
     *            Listener.
     */
    public void setIppRoutinglistener(final IppRoutingListener listener) {
        this.ippRoutinglistener = listener;
    }

    /**
     * Gets the IP address of the requesting client.
     *
     * @return {@code null} when unknown or irrelevant.
     */
    public String getOriginatorIp() {
        return originatorIp;
    }

    /**
     *
     * @return
     */
    public String getOriginatorEmail() {
        return originatorEmail;
    }

    /**
     *
     * @param bytes
     *            The bytes read.
     */
    public final void setReadAheadInputBytes(final byte[] bytes) {
        readAheadInputBytes = bytes;
    }

    /**
     * Is the queue trusted?
     *
     * @return
     */
    public boolean isTrustedQueue() {
        return this.queue != null && this.queue.getTrusted();
    }

    /**
     * Is this an authorized print job? This takes the user and the queue into
     * account, but not the IP address of the requesting user.
     *
     * @return
     */
    public boolean isAuthorized() {

        final boolean authorized =
                isTrustedUser() && (isTrustedQueue() || isAuthWebAppUser());

        if (!authorized && LOGGER.isWarnEnabled()) {

            final StringBuilder msg = new StringBuilder();

            msg.append("Authorized [").append(authorized).append("] :");

            msg.append(" Assigned User [").append(this.assignedUserId)
                    .append("]");

            msg.append(" Trusted User [").append(this.uidTrusted).append("]");

            //
            msg.append(" Trusted Queue [").append(this.isTrustedQueue())
                    .append("]");
            if (this.queue == null) {
                msg.append(" Reason [queue is null]");
            }

            msg.append(" Authenticatied Web App User [")
                    .append(this.authWebAppUser).append("]");

            LOGGER.warn(msg.toString());
        }
        return authorized;
    }

    /**
     * Get the uid of the Person who is currently authenticated in User WebApp
     * at same IP-address as the job was issued from.
     *
     * @return {@code null} if no user is authenticated.
     */
    public String getAuthWebAppUser() {
        return this.authWebAppUser;
    }

    /**
     * Is the authenticated WebApp User present?
     *
     * @return {@code true} if present.
     */
    public boolean isAuthWebAppUser() {
        return StringUtils.isNotBlank(this.authWebAppUser);
    }

    /**
     * Processes the assigned user, i.e. checks whether he is trusted to print a
     * job. Trust can either be direct, by alias, or by authenticated WebApp
     * User. The user (alias) must be a Person.
     * <p>
     * <b>Note</b>: On a trusted Queue (and lazy print enabled) a user is lazy
     * inserted. <i>This method has its own database transaction scope.</i>
     * </p>
     *
     * @param assignedUser
     *            Assigned user id. {@code null} if not available.
     * @param requestingUser
     *            Requesting user id (for logging purposes only).
     * @return {@code true} if we have a trusted assigned uid.
     */
    public boolean processAssignedUser(final String assignedUser,
            final String requestingUser) {

        final UserDao userDao = ServiceContext.getDaoContext().getUserDao();

        this.assignedUserId = assignedUser;

        String uid = null;

        if (assignedUser == null) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(
                        String.format("Requesting user [%s] is not assigned.",
                                requestingUser));
            }
            this.userDb = null;
            this.uidTrusted = null;

        } else {
            // Get the alias (if present).
            uid = UserAliasList.instance().getUserName(this.assignedUserId);

            if (!uid.equals(this.assignedUserId)) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug(String.format(
                            "Replaced assigned user [%s] alias by user [%s].",
                            this.assignedUserId, uid));
                }
            }

            this.userDb = userDao.findActiveUserByUserId(uid);

            /*
             * On a trusted queue (and lazy print enabled) we can lazy insert a
             * user...
             */
            final ConfigManager cm = ConfigManager.instance();

            if (this.userDb == null && this.isTrustedQueue()
                    && cm.isUserInsertLazyPrint()) {

                final String group =
                        cm.getConfigValue(Key.USER_SOURCE_GROUP).trim();

                ServiceContext.getDaoContext().beginTransaction();

                this.userDb = USER_SERVICE
                        .lazyInsertExternalUser(cm.getUserSource(), uid, group);

                if (this.userDb == null) {
                    ServiceContext.getDaoContext().rollback();
                } else {
                    ServiceContext.getDaoContext().commit();
                }
            }
        }

        // Do we have a (lazy inserted) database user?
        if (this.userDb == null) {
            /*
             * The user is not found in the database (no lazy insert). Try the
             * authenticated WebApp user (if present)...
             */
            this.uidTrusted = this.getAuthWebAppUser();

            if (this.uidTrusted != null) {
                this.userDb = userDao.findActiveUserByUserId(this.uidTrusted);
            }

        } else {
            this.uidTrusted = uid;
        }

        /*
         * Check authorization.
         */
        boolean isAuthorized = false;
        final String reason;

        if (this.userDb == null) {
            /*
             * No (trusted WepApp) database user.
             */
            reason = "is untrusted";

        } else {

            if (this.userDb.getPerson()) {

                final Date dateNow = new Date();

                if (USER_SERVICE.isUserPrintInDisabled(this.userDb, dateNow)) {
                    reason = "is disabled for printing";
                } else {
                    isAuthorized = true;
                    reason = null;
                }

            } else {
                reason = "is not a Person";
            }
        }

        if (isAuthorized) {

            if (!this.uidTrusted.equals(uid)) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Requesting user [" + uid + "] is unknown:"
                            + " using WebApp user [" + uidTrusted + "]");
                }
            }

            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("job-name: [" + getJobName() + "]");
            }

        } else {

            if (reason != null) {

                if (this.uidTrusted != null && !this.uidTrusted.equals(uid)) {

                    if (LOGGER.isWarnEnabled()) {
                        LOGGER.warn("Requesting user [" + uid
                                + "] is unknown -> WebApp user ["
                                + this.uidTrusted + "] " + reason
                                + ": print denied");
                    }

                } else {
                    if (LOGGER.isWarnEnabled()) {
                        LOGGER.warn(
                                "Requesting user [{}] {}"
                                        + " : access denied to queue [{}]",
                                requestingUser, reason,
                                IppQueueHelper.uiPath(this.queue));
                    }
                }
            }
            this.uidTrusted = null;
        }

        return this.uidTrusted != null;
    }

    /**
     * Writes the content to an output stream.
     * <p>
     * Note: {@link #inputByteCount} is incremented while reading.
     * </p>
     *
     * @param istr
     *            The content input.
     * @param ostr
     *            The content ouput.
     * @throws IOException
     *             When reading or writing goes wrong.
     */
    private void saveBinary(final InputStream istr, final OutputStream ostr)
            throws IOException {

        final byte[] buffer = new byte[BUFFER_SIZE];

        int noOfBytesWlk = 0;

        /*
         * Read bytes from source file and write to destination file...
         */
        while ((noOfBytesWlk = istr.read(buffer)) != -1) {
            this.inputByteCount += noOfBytesWlk;
            ostr.write(buffer, 0, noOfBytesWlk);
        }
    }

    /**
     * Streams all PostScript input to the PostScript output stream. An
     * exception is throw when PostScript has copyright restrictions.
     *
     * @param istr
     *            The PostScript input stream.
     * @param ostr
     *            The PostScript ouput stream.
     * @throws IOException
     *             When a read/write error occurs.
     * @throws PostScriptDrmException
     *             When PostScript could not be re-distilled due to copyright
     *             restrictions. The obvious reason is when the PostScript file
     *             was created as a result of printing an encrypted PDF file. In
     *             this case the {@code ps2pdf} program fails and reports that
     *             <i>Redistilling encrypted PDF is not permitted</i>.
     */
    private void savePostScript(final InputStream istr, final OutputStream ostr)
            throws IOException, PostScriptDrmException {

        final boolean respectDRM = !ConfigManager.instance()
                .isConfigValue(IConfigProp.Key.PRINT_IN_PDF_ENCRYPTED_ALLOW);

        BufferedReader reader = new BufferedReader(new InputStreamReader(istr));
        BufferedWriter writer =
                new BufferedWriter(new OutputStreamWriter(ostr));

        switch (PostScriptFilter.process(reader, writer, respectDRM)) {
        case DRM_NEGLECTED:
            setDrmRestricted(true);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("DRM protected PostScript from user [" + uidTrusted
                        + "] accepted");
            }
            break;
        case DRM_NO:
            break;
        case DRM_YES:
            throw new PostScriptDrmException(
                    "PostScript not accepted due to copyright restrictions");
        default:
            break;
        }
    }

    /**
     * @param signature
     *            Signature bytes.
     * @param reference
     *            Reference bytes.
     * @return {@code true} if signature matches reference.
     */
    private boolean isSignatureMatch(final byte[] signature,
            final byte[] reference) {
        final int max;
        if (signature.length < reference.length) {
            max = signature.length;
        } else {
            max = reference.length;
        }
        for (int i = 0; i < max; i++) {
            if (signature[i] != reference[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks or assigns the content type of the job depending on the delivery
     * protocol.
     * <p>
     * Optionally a read-ahead of the content input stream (to read the
     * signature) is performed.
     * </p>
     *
     * @param delivery
     *            The delivery protocol.
     * @param typeProvided
     *            The content type as claimed by the provider.
     * @param content
     *            The content input stream.
     * @return The assigned content type.
     * @throws IOException
     *             If IO error.
     */
    private DocContentTypeEnum checkJobContent(
            final DocLogProtocolEnum delivery,
            final DocContentTypeEnum typeProvided, final InputStream content)
            throws IOException {

        DocContentTypeEnum contentType = DocContentTypeEnum.UNKNOWN;

        this.signatureString = null;

        if (delivery.equals(DocLogProtocolEnum.IPP)) {
            /*
             * What kind of file are we getting in?
             */
            byte[] signature = new byte[4];
            content.read(signature);

            this.setReadAheadInputBytes(signature);

            this.signatureString = new String(signature);

            if (this.signatureString.startsWith(DocContent.HEADER_PDF)) {
                contentType = DocContentTypeEnum.PDF;
            } else if (this.signatureString.startsWith(DocContent.HEADER_PS)) {
                contentType = DocContentTypeEnum.PS;
            } else if (DocContent.HEADER_PJL.startsWith(this.signatureString)) {
                // Note: HEADER_PJL string length is GT signatureString.
                contentType = DocContentTypeEnum.PS;
            } else if (this.signatureString
                    .startsWith(DocContent.HEADER_UNIRAST)) {
                contentType = DocContentTypeEnum.URF;
            } else if (this.signatureString
                    .startsWith(DocContent.HEADER_PWGRAST)) {
                contentType = DocContentTypeEnum.PWG;
            } else if (this.isSignatureMatch(signature,
                    DocContent.HEADER_JPEG)) {
                contentType = DocContentTypeEnum.JPEG;
            } else if (this.signatureString
                    .startsWith(DocContent.HEADER_PDF_BANNER)) {
                contentType = DocContentTypeEnum.CUPS_PDF_BANNER;
            } else if (CupsCommandFile.isSignatureStart(this.signatureString)) {
                contentType = DocContentTypeEnum.CUPS_COMMAND;
            }

        } else if (delivery.equals(DocLogProtocolEnum.IMAP)) {
            /*
             * Do not check: accept the provided type.
             */
            contentType = typeProvided;

        } else {
            /*
             * Do not check: accept the provided type.
             */
            contentType = typeProvided;
        }

        return contentType;
    }

    /**
     * Save UnsupportedPrintJobContent for debugging purposes?
     * <p>
     * TODO: make this a {@link IConfigProp.Key}.
     * </p>
     */
    private final static boolean SAVE_UNSUPPORTED_CONTENT = false;

    /**
     * @return Hex representation of captured signature.
     */
    private String hexSignature() {
        final StringBuilder strHex = new StringBuilder();
        for (final byte ch : this.readAheadInputBytes) {
            strHex.append(String.format("%02X ", ch));
        }
        return strHex.toString().trim();
    }

    /**
     * Evaluates assigned the DocContentType and throws an exception when
     * content is NOT supported.
     * <p>
     * </p>
     *
     * @param delivery
     *            The delivery protocol.
     * @param assignedContentType
     * @param content
     * @throws IOException
     * @throws UnsupportedPrintJobContent
     */
    private void evaluateJobContent(DocLogProtocolEnum delivery,
            final DocContentTypeEnum assignedContentType,
            final InputStream content)
            throws IOException, UnsupportedPrintJobContent {

        UnsupportedPrintJobContent formatException = null;
        FileOutputStream fostr = null;

        try {
            if (assignedContentType == DocContentTypeEnum.UNKNOWN) {

                formatException = new UnsupportedPrintJobContent(
                        "header [" + this.hexSignature() + "] unknown");

                if (SAVE_UNSUPPORTED_CONTENT) {
                    fostr = new FileOutputStream(
                            ConfigManager.getAppTmpDir() + "/" + delivery + "_"
                                    + System.currentTimeMillis() + ".unknown");
                    fostr.write(this.readAheadInputBytes);
                    saveBinary(content, fostr);
                }

            } else if (!DocContent.isSupported(assignedContentType)) {

                formatException =
                        new UnsupportedPrintJobContent("Content type ["
                                + assignedContentType + "] NOT supported.");

            }
        } finally {
            if (fostr != null) {
                fostr.close();
            }
        }

        if (formatException != null) {
            throw formatException;
        }

    }

    /**
     * @param cm
     *            {@link ConfigManager}.
     * @param tempDirApp
     *            Directory for temporary files.
     * @param isDriverPrint
     *            {@code true} if Driver Print.
     * @return {@link IPostScriptConverter}.
     */
    private IPostScriptConverter createPostScriptToImageConverter(
            final ConfigManager cm, final String tempDirApp,
            final boolean isDriverPrint) {

        final int dpi;

        if (isDriverPrint) {
            dpi = cm.getConfigInt(Key.PRINT_IN_PS_DRIVER_IMAGES_DPI);
        } else {
            dpi = cm.getConfigInt(Key.PRINT_IN_PS_DRIVERLESS_IMAGES_DPI);
        }

        return new PsToImagePdf(new File(tempDirApp), dpi, this.jobName,
                StringUtils.defaultString(this.getUserDb().getFullName(),
                        this.assignedUserId));
    }

    /**
     * Processes content to be printed as offered on the input stream, writes a
     * {@link DocLog}, and places the resulting PDF in the user's inbox.
     * <p>
     * When this is a {@link DocLogProtocolEnum#RAW} print it is assumed that
     * the input stream header is already validated.
     * </p>
     *
     * @param istrContent
     *            The input stream containing the content to be printed.
     * @param supplierInfo
     *            {@link ExternalSupplierInfo} (can be {@code null}).
     * @param protocol
     *            The originating printing protocol.
     * @param originatorEmailAddr
     *            MUST be present for {@link DocLogProtocolEnum#IMAP}. For all
     *            other protocols {@code null}.
     * @param contentTypeProvided
     *            The content type as claimed by the provider. This parameter is
     *            {@code null} and ignored when protocol is
     *            {@link DocLogProtocolEnum#IPP}.
     * @param preferredOutputFont
     *            The preferred font for the PDF output. This parameter is
     *            {@code null} when (user) preference is unknown or irrelevant.
     * @throws IOException
     *             If IO errors.
     */
    public void process(final InputStream istrContent,
            final ExternalSupplierInfo supplierInfo,
            final DocLogProtocolEnum protocol, final String originatorEmailAddr,
            final DocContentTypeEnum contentTypeProvided,
            final InternalFontFamilyEnum preferredOutputFont)
            throws IOException {

        if (!isTrustedUser()) {
            return;
        }

        this.originatorEmail = originatorEmailAddr;

        final DocContentTypeEnum inputType =
                checkJobContent(protocol, contentTypeProvided, istrContent);

        // Skip CUPS_COMMAND for now.
        if (inputType == DocContentTypeEnum.CUPS_COMMAND) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(CupsCommandFile.FIRST_LINE_SIGNATURE + " ignored");
            }
            return;
        }

        //
        final String homeDir = ConfigManager.getUserHomeDir(this.uidTrusted);
        final String tempDirApp = ConfigManager.getAppTmpDir();

        //
        USER_SERVICE.lazyUserHomeDir(userDb);

        FileOutputStream fostrContent = null;

        this.inputByteCount = 0;

        final List<File> filesCreated = new ArrayList<>();
        final List<File> files2Delete = new ArrayList<>();

        final ConfigManager cm = ConfigManager.instance();

        try {
            /*
             * Keep the job content evaluation WITHIN the try block, so the
             * exception is handled appropriately.
             */
            evaluateJobContent(protocol, inputType, istrContent);

            setMimetype(DocContent.getMimeType(inputType));

            /*
             * The basename of the resulting file WITHOUT extension.
             */
            final String jobFileBase =
                    String.format("%s%c", this.uuidJob.toString(), '.');

            /*
             * The basename of the resulting PDF file.
             */
            final String jobFileBasePdf = String.format("%s%s", jobFileBase,
                    DocContent.FILENAME_EXT_PDF);

            /*
             * File to receive the input stream content.
             */
            final File contentFile = new File(String.format("%s%c%s%s",
                    tempDirApp, File.separatorChar, jobFileBase,
                    DocContent.getFileExtension(inputType)));

            /*
             * Create the file.
             */
            fostrContent = new FileOutputStream(contentFile);

            final OnOffEnum detainPostScript;

            if (inputType == DocContentTypeEnum.PS
                    && protocol.isDriverPrint()) {
                detainPostScript = cm.getConfigEnum(OnOffEnum.class,
                        Key.PRINT_IN_PS_DRIVER_DETAIN);
            } else {
                detainPostScript = OnOffEnum.OFF;
            }

            /*
             * Administer the just created file as created or 2delete.
             */
            if (inputType == DocContentTypeEnum.PDF) {
                filesCreated.add(contentFile);
            } else {
                // Wait for PDF conversion outcome:
                // do not delete PostScript file yet.
                if (detainPostScript == OnOffEnum.OFF) {
                    files2Delete.add(contentFile);
                }
            }

            /*
             * Write the saved pre-processed input bytes to the output stream.
             */
            if (this.readAheadInputBytes != null) {
                this.inputByteCount += this.readAheadInputBytes.length;
                fostrContent.write(this.readAheadInputBytes);
            }

            /*
             * Be optimistic about PostScript and PDF content.
             */
            setDrmViolationDetected(false);
            setDrmRestricted(false);

            this.pdfToCairo = false;
            this.pdfRepair = null;

            /*
             * Document content converters are needed for non-PDF content.
             */
            IStreamConverter streamConverter = null;
            IDocFileConverter fileConverter = null;
            IPostScriptConverter postScriptConverter = null;

            /*
             * Directly write (rest of) offered content to PostScript or PDF, or
             * convert to PDF.
             */
            if (inputType == DocContentTypeEnum.PDF) {

                this.pdfRepair = PdfRepairEnum.NONE;
                saveBinary(istrContent, fostrContent);

            } else if (inputType == DocContentTypeEnum.PS) {
                /*
                 * An exception is throw upon a DRM violation.
                 */
                savePostScript(istrContent, fostrContent);

                if (protocol.isDriverPrint()) {
                    if (OnOffEnum.ON == cm.getConfigEnum(OnOffEnum.class,
                            Key.PRINT_IN_PS_DRIVER_IMAGES_TRIGGER)) {
                        postScriptConverter =
                                this.createPostScriptToImageConverter(cm,
                                        tempDirApp, true);
                    }
                } else {
                    if (OnOffEnum.ON == cm.getConfigEnum(OnOffEnum.class,
                            Key.PRINT_IN_PS_DRIVERLESS_IMAGES_TRIGGER)) {
                        postScriptConverter =
                                this.createPostScriptToImageConverter(cm,
                                        tempDirApp, false);
                    }
                }

                if (postScriptConverter == null) {
                    /*
                     * Always use a file converter,
                     */
                    fileConverter =
                            DocContent.createPdfFileConverter(inputType);
                }

            } else if (inputType == DocContentTypeEnum.CUPS_PDF_BANNER) {

                streamConverter = DocContent.createPdfStreamConverter(inputType,
                        preferredOutputFont);

            } else if (inputType == DocContentTypeEnum.HTML) {

                // If available, file converter is preferred.
                fileConverter = DocContent.createPdfFileConverter(inputType);

                if (fileConverter == null) {
                    streamConverter = DocContent.createPdfStreamConverter(
                            inputType, preferredOutputFont);
                } else {
                    saveBinary(istrContent, fostrContent);
                }

            } else {

                if (!protocol.isDriverPrint()) {
                    streamConverter = DocContent.createPdfStreamConverter(
                            inputType, preferredOutputFont);
                }

                if (streamConverter == null) {

                    fileConverter =
                            DocContent.createPdfFileConverter(inputType);

                    if (fileConverter != null) {
                        saveBinary(istrContent, fostrContent);
                    }
                }
            }

            /*
             * Convert to PDF with a stream converter?
             */
            if (streamConverter != null) {
                /*
                 * INVARIANT: no read-ahead on the input content stream.
                 */
                final DocInputStream istrDoc = new DocInputStream(istrContent);
                this.inputByteCount = streamConverter.convert(inputType,
                        istrDoc, fostrContent);
            }

            /*
             * Path of the PDF file BEFORE it is moved to its final destination.
             * As a DEFAULT we use the path of the streamed content.
             */
            String tempPathPdf = contentFile.getAbsolutePath();

            /*
             * We're done with capturing the content input stream, so close the
             * file output stream.
             */
            fostrContent.close();
            fostrContent = null;

            /*
             * Convert to PDF with a FILE converter?
             */
            if (fileConverter != null) {
                this.inputByteCount = contentFile.length();

                final File pdfOutputFile =
                        fileConverter.convert(inputType, contentFile);

                /*
                 * Retry with PostScript converter?
                 */
                if (inputType == DocContentTypeEnum.PS
                        && fileConverter.hasStdErrMsg()) {

                    if (protocol.isDriverPrint()) {
                        if (OnOffEnum.AUTO == cm.getConfigEnum(OnOffEnum.class,
                                Key.PRINT_IN_PS_DRIVER_IMAGES_TRIGGER)) {
                            postScriptConverter =
                                    this.createPostScriptToImageConverter(cm,
                                            tempDirApp, true);
                        }
                    } else {
                        if (OnOffEnum.AUTO == cm.getConfigEnum(OnOffEnum.class,
                                Key.PRINT_IN_PS_DRIVERLESS_IMAGES_TRIGGER)) {
                            postScriptConverter =
                                    this.createPostScriptToImageConverter(cm,
                                            tempDirApp, false);
                        }
                    }

                    if (postScriptConverter == null) {
                        filesCreated.add(pdfOutputFile);
                    } else {
                        pdfOutputFile.delete();
                    }
                }
                tempPathPdf = pdfOutputFile.getAbsolutePath();
            }

            /*
             * Convert to PDF with PostScript converter?
             */
            if (postScriptConverter != null) {
                final File pdfOutputFile =
                        postScriptConverter.convert(contentFile);
                filesCreated.add(pdfOutputFile);
                tempPathPdf = pdfOutputFile.getAbsolutePath();
            }

            /*
             * Calculate number of pages, etc. and repair along the way.
             */
            final SpPdfPageProps pdfPageProps =
                    this.createPdfPageProps(tempPathPdf);

            this.setPageProps(pdfPageProps);

            //
            if (inputType == DocContentTypeEnum.PDF) {
                final File fileWrk = new File(tempPathPdf);
                if (cm.isConfigValue(Key.PRINT_IN_PDF_FONTS_VERIFY)) {
                    this.verifyPdfFonts(fileWrk);
                }
                if (!this.pdfToCairo
                        && cm.isConfigValue(Key.PRINT_IN_PDF_FONTS_EMBED)) {
                    this.embedPdfFonts(fileWrk);
                }
                if (!this.pdfToCairo && !this.pdfProvidedIsClean
                        && cm.isConfigValue(Key.PRINT_IN_PDF_CLEAN)) {
                    this.cleanPdf(fileWrk);
                }
                if (cm.isConfigValue(Key.PRINT_IN_PDF_PREPRESS)) {
                    this.cleanPdfPrepress(fileWrk);
                }
            } else if (fileConverter != null && (fileConverter.hasStdErrMsg()
                    || detainPostScript == OnOffEnum.ON)) {

                final StringBuilder msg = new StringBuilder();
                msg.append("User \"").append(this.uidTrusted).append("\" ")
                        .append(fileConverter.getClass().getSimpleName());

                if (fileConverter.hasStdErrMsg()) {
                    msg.append(" errors.");
                } else {
                    msg.append(".");
                }

                final PubLevelEnum pubLevel;

                if (postScriptConverter == null) {
                    if (detainPostScript == OnOffEnum.OFF) {
                        pubLevel = PubLevelEnum.ERROR;
                        msg.append(" Rendering invalid.");
                    } else {
                        pubLevel = PubLevelEnum.WARN;
                    }
                } else {
                    pubLevel = PubLevelEnum.WARN;
                    msg.append(" Pages rendered as images.");
                }

                if (detainPostScript == OnOffEnum.ON
                        || (detainPostScript == OnOffEnum.AUTO
                                && fileConverter.hasStdErrMsg())) {
                    msg.append(" (PostScript file is detained).");
                }
                AdminPublisher.instance().publish(PubTopicEnum.USER, pubLevel,
                        msg.toString());
            }

            // Check again...
            if (detainPostScript == OnOffEnum.AUTO && (fileConverter == null
                    || !fileConverter.hasStdErrMsg())) {
                // No stderr: delete PostScript file after all.
                files2Delete.add(contentFile);
            }

            /*
             * STEP 1: Log in Database: BEFORE the file MOVE.
             */
            final DocContentPrintInInfo printInInfo =
                    this.logPrintIn(protocol, supplierInfo);

            /*
             * STEP 2: PDF to Doc Store?
             */
            final File pdfTempFile = new File(tempPathPdf);

            final boolean isDocJournal =
                    this.processDocJournal(printInInfo, pdfTempFile);

            /*
             * STEP 3: IPP Routing?
             */
            final boolean isIppRouting =
                    this.processIppRouting(printInInfo, pdfTempFile);

            /*
             * STEP 4: Move to user safepages home?
             */
            final boolean isMailPrintTicket =
                    isDocJournal && this.isMailPrintTicket();

            if (isIppRouting || isMailPrintTicket) {
                files2Delete.add(pdfTempFile);
            } else {
                // See Mantis #1167 : "touch" PDF file.
                pdfTempFile.setLastModified(System.currentTimeMillis());

                final Path pathTarget = FileSystems.getDefault()
                        .getPath(homeDir, jobFileBasePdf);

                FileSystemHelper.doAtomicFileMove(//
                        FileSystems.getDefault().getPath(tempPathPdf),
                        pathTarget);
                /*
                 * Start task to create the shadow EcoPrint PDF file?
                 */
                if (ConfigManager.isEcoPrintEnabled() && this.getPageProps()
                        .getNumberOfPages() <= cm.getConfigInt(
                                Key.ECO_PRINT_AUTO_THRESHOLD_SHADOW_PAGE_COUNT)) {
                    INBOX_SERVICE.startEcoPrintPdfTask(homeDir,
                            pathTarget.toFile(), this.uuidJob);
                }
            }

        } catch (Exception e) {

            if (e instanceof PostScriptDrmException) {

                setDrmRestricted(true);
                setDrmViolationDetected(true);

                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("DRM protected PostScript from user ["
                            + uidTrusted + "] REJECTED");
                }

                /*
                 * We also need to log the rejected print-in, since we want to
                 * notify the result in the User WebApp.
                 */
                this.logPrintIn(protocol, supplierInfo);

            } else {

                if (e instanceof PdfValidityException) {
                    if (this.pdfRepair == null
                            || !this.pdfRepair.isRepairFail()) {
                        this.pdfRepair = PdfRepairEnum.DOC_FAIL;
                    }
                    this.logPrintIn(protocol, supplierInfo);
                }
                /*
                 * Save the exception, so it can be thrown at the end of the
                 * parent operation.
                 */
                setDeferredException(e);
            }

            /*
             * Clean up any created files.
             */
            for (final File file : filesCreated) {
                if (file.exists()) {
                    file.delete();
                }
            }

        } finally {

            if (fostrContent != null) {
                fostrContent.close();
            }

            for (final File file : files2Delete) {
                if (file.exists()) {
                    file.delete();
                }
            }
        }
    }

    /**
     * Validates and optionally repairs PDF file for font errors.
     *
     * @param pdf
     *            PDF file.
     * @throws PdfValidityException
     *             When font error(s) in PDF document.
     * @throws IOException
     *             When file IO error.
     */
    private void verifyPdfFonts(final File pdf)
            throws PdfValidityException, IOException {

        final PdfFontsErrorValidator validator =
                new PdfFontsErrorValidator(pdf);

        if (!validator.execute()) {

            if (!this.pdfToCairo) {

                final PdfRepair converter = new PdfRepair();

                FileSystemHelper.replaceWithNewVersion(pdf,
                        converter.convert(pdf));
                // Try again.
                if (validator.execute()) {
                    this.pdfRepair = PdfRepairEnum.FONT;
                    this.pdfToCairo = true;
                    return;
                }
                this.pdfRepair = PdfRepairEnum.FONT_FAIL;
            }
            throw new PdfValidityException("Font errors.",
                    PhraseEnum.PDF_INVALID.uiText(ServiceContext.getLocale()),
                    PhraseEnum.PDF_INVALID);
        }
    }

    /**
     * Embeds non-standard fonts in PDF file.
     *
     * @param pdf
     *            PDF file.
     * @throws PdfValidityException
     *             When embed font error(s).
     */
    private void embedPdfFonts(final File pdf) throws PdfValidityException {

        final PdfDocumentFonts fonts;

        try {
            fonts = PdfDocumentFonts.create(pdf);
        } catch (IOException e) {
            throw new IllegalStateException(e.getMessage());
        }

        if (fonts.isAllEmbeddedOrStandard()) {
            return;
        }

        final PdfRepair converter = new PdfRepair();

        try {
            FileSystemHelper.replaceWithNewVersion(pdf, converter.convert(pdf));
            this.pdfToCairo = true;
            if (converter.hasStdout()) {
                this.pdfRepair = PdfRepairEnum.DOC;
            }
        } catch (IOException e) {
            this.pdfRepair = PdfRepairEnum.DOC_FAIL;
            throw new PdfValidityException("Embed Font errors.",
                    PhraseEnum.PDF_INVALID.uiText(ServiceContext.getLocale()),
                    PhraseEnum.PDF_INVALID);
        }
    }

    /**
     * Cleans a PDF file.
     *
     * @param pdf
     *            PDF file.
     * @throws PdfValidityException
     *             When error(s).
     */
    private void cleanPdf(final File pdf) throws PdfValidityException {

        final PdfRepair converter = new PdfRepair();

        try {
            FileSystemHelper.replaceWithNewVersion(pdf, converter.convert(pdf));
            this.pdfToCairo = true;
            if (converter.hasStdout()) {
                this.pdfRepair = PdfRepairEnum.DOC;
            }
        } catch (IOException e) {
            this.pdfRepair = PdfRepairEnum.DOC_FAIL;
            throw new PdfValidityException("PDF cleaning errors.",
                    PhraseEnum.PDF_INVALID.uiText(ServiceContext.getLocale()),
                    PhraseEnum.PDF_INVALID);
        }
    }

    /**
     * Cleans a PDF file by executing Ghostscript prepress.
     *
     * @param pdf
     *            PDF file.
     * @throws PdfValidityException
     *             When error(s).
     */
    private void cleanPdfPrepress(final File pdf) throws PdfValidityException {

        final PdfToPrePress converter = new PdfToPrePress();

        try {
            FileSystemHelper.replaceWithNewVersion(pdf, converter.convert(pdf));
            if (converter.hasStdout()) {
                this.pdfRepair = PdfRepairEnum.DOC;
            }
        } catch (IOException e) {
            this.pdfRepair = PdfRepairEnum.DOC_FAIL;
            throw new PdfValidityException("PDF prepress errors.",
                    PhraseEnum.PDF_INVALID.uiText(ServiceContext.getLocale()),
                    PhraseEnum.PDF_INVALID);
        }
    }

    /**
     * Creates PDF page properties, and optionally repairs or decrypts PDF.
     *
     * @param tempPathPdf
     *            The PDF file path.
     * @return {@link SpPdfPageProps}.
     * @throws PdfValidityException
     *             When invalid PDF document.
     * @throws PdfSecurityException
     *             When encrypted PDF document.
     * @throws IOException
     *             When file IO error.
     * @throws PdfPasswordException
     *             When password protected PDF document.
     * @throws PdfUnsupportedException
     *             When unsupported PDF document.
     */
    private SpPdfPageProps createPdfPageProps(final String tempPathPdf)
            throws PdfValidityException, PdfSecurityException, IOException,
            PdfPasswordException, PdfUnsupportedException {

        SpPdfPageProps pdfPageProps;

        try {
            pdfPageProps = SpPdfPageProps.create(tempPathPdf);
        } catch (PdfValidityException e) {

            if (ConfigManager.instance().isConfigValue(
                    IConfigProp.Key.PRINT_IN_PDF_INVALID_REPAIR)) {

                this.pdfRepair = PdfRepairEnum.DOC_FAIL;

                final File pdfFile = new File(tempPathPdf);

                // Convert ...
                try {
                    final PdfRepair converter = new PdfRepair();
                    FileSystemHelper.replaceWithNewVersion(pdfFile,
                            converter.convert(pdfFile));
                } catch (IOException ignore) {
                    throw new PdfValidityException(e.getMessage(),
                            PhraseEnum.PDF_REPAIR_FAILED
                                    .uiText(ServiceContext.getLocale()),
                            PhraseEnum.PDF_REPAIR_FAILED);
                }
                // and try again.
                pdfPageProps = SpPdfPageProps.create(tempPathPdf);

                this.pdfRepair = PdfRepairEnum.DOC;
                this.pdfToCairo = true;
            } else {
                throw e;
            }

        } catch (PdfSecurityException e) {

            if (e.isPrintingAllowed()
                    && ConfigManager.instance().isConfigValue(
                            IConfigProp.Key.PRINT_IN_PDF_ENCRYPTED_ALLOW)
                    && PdfToDecrypted.isAvailable()) {

                final File pdfFile = new File(tempPathPdf);

                // Convert ...
                FileSystemHelper.replaceWithNewVersion(pdfFile,
                        new PdfToDecrypted().convert(pdfFile));

                // and try again.
                pdfPageProps = SpPdfPageProps.create(tempPathPdf);

                this.setDrmRestricted(true);

            } else {
                throw e;
            }
        }

        return pdfPageProps;
    }

    /**
     * Logs the PrintIn job.
     * <p>
     * <b>IMPORTANT</b>: This method has it <u>own transaction scope</u>.
     * </p>
     *
     * @param protocol
     *            The {@link DocLogProtocolEnum}.
     * @param supplierInfo
     *            {@link {@link ExternalSupplierInfo}} (can be {@code null}).
     * @return {@link DocContentPrintInInfo}.
     */
    private DocContentPrintInInfo logPrintIn(final DocLogProtocolEnum protocol,
            final ExternalSupplierInfo supplierInfo) {

        final DocContentPrintInInfo printInInfo = new DocContentPrintInInfo();

        printInInfo.setPrintInDate(ServiceContext.getTransactionDate());

        printInInfo.setDrmRestricted(this.isDrmRestricted());
        printInInfo.setPdfRepair(this.pdfRepair);
        printInInfo.setJobBytes(this.getJobBytes());
        printInInfo.setJobName(this.getJobName());
        printInInfo.setMimetype(this.getMimetype());
        printInInfo.setOriginatorEmail(this.getOriginatorEmail());
        printInInfo.setOriginatorIp(this.getOriginatorIp());
        printInInfo.setPageProps(this.getPageProps());
        printInInfo.setUuidJob(this.getUuidJob());
        printInInfo.setSupplierInfo(supplierInfo);

        if (this.printInParent == null) {
            DOC_LOG_SERVICE.logPrintIn(this.getUserDb(), this.getQueue(),
                    protocol, printInInfo);
        } else {
            DOC_LOG_SERVICE.attachPrintIn(this.printInParent, this.getUserDb(),
                    this.getQueue(), protocol, printInInfo);
        }

        return printInInfo;
    }

    /**
     * Stores PDF file into the Journal branch of the Document Store, if this
     * branch is enabled and user has permission to journal their SavePages.
     *
     * @param printInInfo
     *            {@link PrintIn} information.
     * @param pdfFile
     *            The PDF document to route.
     * @return {@code true} if PDF was stored in Document Store, {@code false}
     *         if store/branch is disabled..
     * @throws DocStoreException
     */
    private boolean processDocJournal(final DocContentPrintInInfo printInInfo,
            final File pdfFile) throws DocStoreException {

        final DocStoreTypeEnum store = DocStoreTypeEnum.JOURNAL;

        if (DOC_STORE_SERVICE.isEnabled(store, DocStoreBranchEnum.IN_PRINT)
                && !QUEUE_SERVICE.isDocStoreJournalDisabled(this.getQueue())
                && this.userDb != null && ACCESS_CONTROL_SERVICE
                        .hasAccess(userDb, ACLOidEnum.U_QUEUE_JOURNAL)) {
            DOC_STORE_SERVICE.store(store, printInInfo, pdfFile);
            return true;
        }
        return false;
    }

    /**
     * Processes IPP Routing, if applicable.
     *
     * @param printInInfo
     *            {@link PrintIn} information.
     * @param pdfFile
     *            The PDF document to route.
     * @return {@code true} if IPP routing was applied, {@code false} if not.
     */
    private boolean processIppRouting(final DocContentPrintInInfo printInInfo,
            final File pdfFile) {

        if (!ConfigManager.instance().isConfigValue(Key.IPP_ROUTING_ENABLE)) {
            return false;
        }

        if (StringUtils.isBlank(this.originatorIp)
                || QUEUE_SERVICE.isReservedQueue(this.queue.getUrlPath())) {
            return false;
        }

        /*
         * Use a new queue instance to prevent
         * org.hibernate.LazyInitializationException.
         */
        final IppQueue queueWrk = ServiceContext.getDaoContext()
                .getIppQueueDao().findById(this.queue.getId());

        final IppRoutingEnum routing = QUEUE_SERVICE.getIppRouting(queueWrk);

        if (routing == null || routing == IppRoutingEnum.NONE) {
            return false;
        }

        if (routing == IppRoutingEnum.TERMINAL) {
            final Device terminal =
                    DEVICE_SERVICE.getHostTerminal(this.originatorIp);

            final String warnMsg;

            if (terminal == null) {
                warnMsg = ": terminal not found.";
            } else if (BooleanUtils.isTrue(terminal.getDisabled())) {
                warnMsg = ": terminal disabled.";
            } else {
                final Printer printer = terminal.getPrinter();
                if (printer == null) {
                    warnMsg = ": no printer on terminal.";
                } else {
                    warnMsg = null;
                }
            }

            if (warnMsg != null) {
                final String msg =
                        String.format("IPP Routing of Queue /%s from %s %s",
                                queueWrk.getUrlPath(), this.originatorIp, warnMsg);
                AdminPublisher.instance().publish(PubTopicEnum.PROXY_PRINT,
                        PubLevelEnum.WARN, msg);
                LOGGER.warn(msg);
                return false;
            }

            try {
                PROXYPRINT_SERVICE.proxyPrintIppRouting(this.userDb, queueWrk,
                        terminal.getPrinter(), printInInfo, pdfFile,
                        this.ippRoutinglistener);
            } catch (ProxyPrintException e) {
                throw new SpException(e.getMessage());
            }
        } else if (routing == IppRoutingEnum.PRINTER) {
            final String printerName =  QUEUE_SERVICE.getIppRoutingTarget(queueWrk);
            if (StringUtils.isBlank(printerName)) {
                throw new SpException(
                        "IPP Routing of Queue /" + queueWrk.getUrlPath()
                                + " from " + this.originatorIp
                                + ": no printer name defined.");
            }
            final PrinterDao printerDao = ServiceContext.getDaoContext().getPrinterDao();
            final Printer printer = printerDao.findByName(printerName);
            try {
                PROXYPRINT_SERVICE.proxyPrintIppRouting(this.userDb, queueWrk,
                    printer, printInInfo, pdfFile,
                        this.ippRoutinglistener);
            } catch (ProxyPrintException e) {
                throw new SpException(e.getMessage());
            }
        } else {
            throw new SpException(String.format(
                    "IPP Routing [%s] is not supported", routing.toString()));
        }

        return true;
    }

    /**
     * @param docLog
     *            {@link DocLog} to attach {@link PrintIn} to. Can be
     *            {@code null}.
     */
    public void setPrintInParent(final DocLog docLog) {
        this.printInParent = docLog;
    }

    /**
     * @return
     */
    public java.util.UUID getUuidJob() {
        return uuidJob;
    }

    public void setUuidJob(java.util.UUID uuidJob) {
        this.uuidJob = uuidJob;
    }

    public long getJobBytes() {
        return inputByteCount;
    }

    public IPdfPageProps getPageProps() {
        return pageProps;
    }

    public int getNumberOfPages() {
        return pageProps.getNumberOfPages();
    }

    private void setPageProps(final SpPdfPageProps pageProps) {
        this.pageProps = pageProps;
    }

    public String getMimetype() {
        return mimetype;
    }

    private void setMimetype(String mimetype) {
        this.mimetype = mimetype;
    }

    /**
     * The user object from the database representing the user who printed this
     * job.
     *
     * @return {@code null} when unknown.
     */
    public User getUserDb() {
        return userDb;
    }

    public boolean isDrmViolationDetected() {
        return drmViolationDetected;
    }

    public void setDrmViolationDetected(boolean drmViolationDetected) {
        this.drmViolationDetected = drmViolationDetected;
    }

    public boolean isDrmRestricted() {
        return drmRestricted;
    }

    public void setDrmRestricted(boolean restricted) {
        drmRestricted = restricted;
    }

    public boolean isPdfRepaired() {
        return this.pdfRepair != null && this.pdfRepair.isRepaired();
    }

    /**
     * Checks if we have a trusted userid who is allowed to print.
     *
     * @return {@code true} if we have a trusted user.
     */
    public boolean isTrustedUser() {
        return this.uidTrusted != null;
    }

    /**
     * @return {@code true} if content is a processed as MailPrint Ticket.
     */
    private boolean isMailPrintTicket() {
        return this.reservedQueue != null
                && this.reservedQueue == ReservedIppQueueEnum.MAILPRINT
                && ConfigManager.isMailPrintTicketingEnabled(this.getUserDb());
    }

    public Exception getDeferredException() {
        return deferredException;
    }

    public void setDeferredException(Exception deferredException) {
        this.deferredException = deferredException;
    }

    public boolean hasDeferredException() {
        return deferredException != null;
    }

    /**
     *
     * @return
     */
    public String getJobName() {
        return jobName;
    }

    /**
     *
     */
    public void setJobName(final String name) {
        this.jobName = name;
    }

    /**
     *
     * @return
     */
    public IppQueue getQueue() {
        return queue;
    }

    /**
     * Return a localized message string. IMPORTANT: The locale from the
     * application is used.
     *
     * @param key
     *            The key of the message.
     * @param args
     *            The placeholder arguments for the message template.
     *
     * @return The message text.
     */
    private String localize(final String key, final String... args) {
        return Messages.getMessage(getClass(), key, args);
    }

    /**
     * Evaluates (and adapts) the error state of the print job processed by this
     * request.
     * <p>
     * When a deferred exception is present or the user is not authorized to
     * print this job, messages are logged and send to the
     * {@link AdminPublisher}.
     * </p>
     * <p>
     * NOTE: Some "legal" deferred exceptions like
     * {@link PostScriptDrmException}, {@link PdfSecurityException} and
     * {@link UnsupportedPrintJobContent} are nullified after message handling,
     * i.e. {@link #getDeferredException()} will return {@code null} after this
     * method is performed.
     * </p>
     *
     * @param isAuthorized
     *            {@code true} when requesting user is authorized.
     * @param requestingUserId
     *            Requesting user id (for logging purposes only).
     */
    public void evaluateErrorState(final boolean isAuthorized,
            final String requestingUserId) {

        final Exception exception = getDeferredException();

        if (exception == null && isAuthorized) {
            return;
        }

        final String urlQueue = IppQueueHelper.uiPath(this.queue);
        final String userid;

        if (getUserDb() == null) {
            userid = StringUtils.defaultString(getAuthWebAppUser());
        } else {
            userid = getUserDb().getUserId();
        }

        final String pubMessage;
        PubLevelEnum pubLevel = PubLevelEnum.WARN;

        if (exception == null) {

            pubMessage = localize("pub-user-print-in-not-authorized");

        } else {

            if (exception instanceof PostScriptDrmException) {

                pubMessage = exception.getMessage();

                if (LOGGER.isWarnEnabled()) {
                    LOGGER.warn(String.format(
                            "User [%s] Distilling PostScript to PDF : %s",
                            userid, exception.getMessage()));
                }
                setDeferredException(null);

            } else if ((exception instanceof PdfAbstractException)) {

                pubMessage = ((PdfAbstractException) exception).getLogMessage();

                if (LOGGER.isWarnEnabled()) {
                    LOGGER.warn(
                            String.format("User [%s]: %s", userid, pubMessage));
                }
                setDeferredException(null);

            } else if (exception instanceof UnsupportedPrintJobContent) {

                pubMessage = exception.getMessage();

                if (LOGGER.isWarnEnabled()) {
                    if (LOGGER.isWarnEnabled()) {
                        LOGGER.warn(String.format(
                                "Unsupported Print Content from user [%s]: %s",
                                userid, exception.getMessage()));
                    }
                }
                setDeferredException(null);

            } else {
                pubMessage = exception.getMessage();
                if ((exception instanceof //
                org.xhtmlrenderer.util.XRRuntimeException)
                        || (exception instanceof ExceptionConverter)
                        || (exception instanceof DocContentToPdfException)) {
                    LOGGER.warn("[{}] PDF error: {}", this.getJobName(),
                            pubMessage);
                    pubLevel = PubLevelEnum.WARN;
                } else {
                    LOGGER.error(pubMessage, exception);
                    pubLevel = PubLevelEnum.ERROR;
                }
            }
        }

        AdminPublisher.instance().publish(PubTopicEnum.USER, pubLevel,
                localize("pub-user-print-in-denied", requestingUserId, urlQueue,
                        originatorIp, pubMessage));
    }

    /**
     * @param isClean
     *            If {@code true} the provided {@link DocContentTypeEnum#PDF} is
     *            clean.
     */
    public void setPdfProvidedIsClean(final boolean isClean) {
        this.pdfProvidedIsClean = isClean;
    }

}
