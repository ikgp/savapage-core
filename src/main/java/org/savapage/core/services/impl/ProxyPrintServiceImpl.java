/*
 * This file is part of the SavaPage project <http://savapage.org>.
 * Copyright (c) 2011-2015 Datraverse B.V.
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
package org.savapage.core.services.impl;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.UnknownHostException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import javax.print.attribute.standard.MediaSizeName;

import org.apache.commons.lang3.StringUtils;
import org.savapage.core.SpException;
import org.savapage.core.community.CommunityDictEnum;
import org.savapage.core.config.ConfigManager;
import org.savapage.core.config.IConfigProp.Key;
import org.savapage.core.dao.PrinterDao;
import org.savapage.core.dao.helpers.DocLogProtocolEnum;
import org.savapage.core.dao.helpers.PrintModeEnum;
import org.savapage.core.dao.helpers.PrinterAttrEnum;
import org.savapage.core.dao.helpers.ProxyPrinterName;
import org.savapage.core.dto.IppMediaCostDto;
import org.savapage.core.dto.IppMediaSourceCostDto;
import org.savapage.core.dto.MediaCostDto;
import org.savapage.core.dto.MediaPageCostDto;
import org.savapage.core.dto.ProxyPrinterCostDto;
import org.savapage.core.dto.ProxyPrinterDto;
import org.savapage.core.dto.ProxyPrinterMediaSourcesDto;
import org.savapage.core.ipp.IppMediaSizeEnum;
import org.savapage.core.ipp.IppSyntaxException;
import org.savapage.core.ipp.attribute.AbstractIppDict;
import org.savapage.core.ipp.attribute.IppAttr;
import org.savapage.core.ipp.attribute.IppAttrCollection;
import org.savapage.core.ipp.attribute.IppAttrGroup;
import org.savapage.core.ipp.attribute.IppAttrValue;
import org.savapage.core.ipp.attribute.IppDictJobDescAttr;
import org.savapage.core.ipp.attribute.IppDictJobTemplateAttr;
import org.savapage.core.ipp.attribute.IppDictJobTemplateAttr.ApplEnum;
import org.savapage.core.ipp.attribute.IppDictOperationAttr;
import org.savapage.core.ipp.attribute.IppDictPrinterDescAttr;
import org.savapage.core.ipp.attribute.IppDictSubscriptionAttr;
import org.savapage.core.ipp.attribute.syntax.IppBoolean;
import org.savapage.core.ipp.attribute.syntax.IppInteger;
import org.savapage.core.ipp.attribute.syntax.IppKeyword;
import org.savapage.core.ipp.attribute.syntax.IppMimeMediaType;
import org.savapage.core.ipp.client.IppClient;
import org.savapage.core.ipp.client.IppConnectException;
import org.savapage.core.ipp.encoding.IppDelimiterTag;
import org.savapage.core.ipp.operation.IppGetPrinterAttrOperation;
import org.savapage.core.ipp.operation.IppOperationId;
import org.savapage.core.ipp.operation.IppStatusCode;
import org.savapage.core.job.SpJobScheduler;
import org.savapage.core.jpa.DocLog;
import org.savapage.core.jpa.Printer;
import org.savapage.core.jpa.PrinterAttr;
import org.savapage.core.jpa.PrinterGroup;
import org.savapage.core.jpa.PrinterGroupMember;
import org.savapage.core.json.JsonAbstractBase;
import org.savapage.core.json.JsonPrinterDetail;
import org.savapage.core.json.rpc.AbstractJsonRpcMethodResponse;
import org.savapage.core.json.rpc.JsonRpcError.Code;
import org.savapage.core.json.rpc.JsonRpcMethodError;
import org.savapage.core.json.rpc.JsonRpcMethodResult;
import org.savapage.core.print.proxy.JsonProxyPrintJob;
import org.savapage.core.print.proxy.JsonProxyPrinter;
import org.savapage.core.print.proxy.JsonProxyPrinterOpt;
import org.savapage.core.print.proxy.JsonProxyPrinterOptChoice;
import org.savapage.core.print.proxy.JsonProxyPrinterOptGroup;
import org.savapage.core.print.proxy.ProxyPrintInboxReq;
import org.savapage.core.services.ServiceContext;
import org.savapage.core.util.BigDecimalUtil;
import org.savapage.core.util.DateUtil;
import org.savapage.core.util.MediaUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Rijk Ravestein
 *
 */
public final class ProxyPrintServiceImpl extends AbstractProxyPrintService {

    /**
     * .
     */
    private static final Logger LOGGER = LoggerFactory
            .getLogger(ProxyPrintServiceImpl.class);

    private static final String OPTGROUP_PAGE_SETUP = "pagesetup";
    private static final String OPTGROUP_JOB = "job";
    private static final String OPTGROUP_ADVANCED = "advanced";
    private static final String LOCALIZE_IPP_ATTR_PREFIX = "ipp-attr-";

    private final static String NOTIFY_PULL_METHOD = "ippget";

    /**
     * A unique ID to distinguish our subscription from other system
     * subscriptions.
     */
    private final static String NOTIFY_USER_DATA = "savapage:"
            + ConfigManager.getServerPort();

    private final IppClient ippClient = IppClient.instance();

    /**
     *
     * @throws MalformedURLException
     */
    public ProxyPrintServiceImpl() {
        super();
    }

    @Override
    public void init() {
        super.init();
        ippClient.init();
    }

    @Override
    public void exit() throws IppConnectException, IppSyntaxException {
        super.exit();
        ippClient.shutdown();
    }

    @Override
    protected ArrayList<JsonProxyPrinterOptGroup> createCommonCupsOptions() {
        return null;
    }

    @Override
    protected List<JsonProxyPrinter> retrieveCupsPrinters()
            throws IppConnectException, URISyntaxException,
            MalformedURLException {

        final List<JsonProxyPrinter> printers = new ArrayList<>();

        final boolean remoteCupsEnabled =
                ConfigManager.instance().isConfigValue(
                        Key.CUPS_IPP_REMOTE_ENABLED);

        final JsonProxyPrinter defaultPrinter = getCupsDefaultPrinter();

        /*
         * Get the list of CUPS printers.
         */
        final List<IppAttrGroup> response =
                ippClient.send(getUrlDefaultServer(),
                        IppOperationId.CUPS_GET_PRINTERS, reqCupsGetPrinters());

        /*
         * Traverse the response groups.
         */
        for (IppAttrGroup group : response) {

            /*
             * Handle PRINTER_ATTR groups only.
             */
            if (group.getDelimiterTag() != IppDelimiterTag.PRINTER_ATTR) {
                continue;
            }

            /*
             * Skip any SavaPage printer.
             */
            final String makeModel =
                    group.getAttrSingleValue(
                            IppDictPrinterDescAttr.ATTR_PRINTER_MAKE_MODEL, "");

            if (makeModel.toLowerCase().startsWith("savapage")) {
                continue;
            }

            /*
             * Get the printer URI.
             */
            final String printerUriSupp =
                    group.getAttrSingleValue(IppDictPrinterDescAttr.ATTR_PRINTER_URI_SUPPORTED);

            final URI uriPrinter = new URI(printerUriSupp);

            /*
             * Skip remote printer when remoteCups is disabled.
             */
            if (!remoteCupsEnabled && !isLocalPrinter(uriPrinter)) {
                continue;
            }

            /*
             * Create JsonProxyPrinter object from PRINTER_ATTR group.
             */
            final JsonProxyPrinter proxyPrinterFromGroup =
                    createUserPrinter(defaultPrinter, group);

            if (proxyPrinterFromGroup == null) {
                continue;
            }
            // proxyPrinterToAdd.setPrinterUri(uriPrinter);

            /*
             * Retrieve printer details.
             */
            try {
                final JsonProxyPrinter proxyPrinterDetails =
                        retrieveCupsPrinterDetails(
                                proxyPrinterFromGroup.getName(),
                                proxyPrinterFromGroup.getPrinterUri());

                if (proxyPrinterDetails != null) {
                    printers.add(proxyPrinterDetails);
                }

            } catch (IppConnectException e) {
                // noop
            }

        }
        return printers;
    }

    /**
     * Gets the CUPS server {@link URL} of the printer {@link URI}. Example:
     * http://192.168.1.35:631
     *
     * @param uriPrinter
     *            The {@link URI} of the printer.
     * @return The CUPS server {@link URL}.
     * @throws MalformedURLException
     *             When the input is malformed.
     */
    private URL getCupsServerUrl(final URI uriPrinter)
            throws MalformedURLException {
        return new URL("http", uriPrinter.getHost(), uriPrinter.getPort(), "");
    }

    @Override
    public List<IppAttrGroup> getIppPrinterAttr(final String printerName,
            final URI printerUri) throws IppConnectException {

        final boolean isLocalCups = isLocalPrinter(printerUri);

        try {
            /*
             * If this is a REMOTE printer (e.g. printerUri:
             * ipp://192.168.1.36:631/printers/HL-2030-series) ...
             *
             * ... then we MUST use that URI to get the details (groups).
             */
            final URL urlCupsServer;

            if (isLocalCups) {
                urlCupsServer = getUrlDefaultServer();
            } else {
                urlCupsServer = getCupsServerUrl(printerUri);
            }
            return ippClient.send(urlCupsServer, isLocalCups,
                    IppOperationId.GET_PRINTER_ATTR,
                    reqGetPrinterAttr(printerUri.toString()));

        } catch (MalformedURLException e) {
            throw new SpException(e);
        }
    }

    @Override
    protected JsonProxyPrinter retrieveCupsPrinterDetails(
            final String printerName, final URI printerUri)
            throws IppConnectException {

        JsonProxyPrinter printer = null;

        final List<IppAttrGroup> response =
                getIppPrinterAttr(printerName, printerUri);

        if (response.size() > 1) {
            printer = createUserPrinter(null, response.get(1));
        }

        return printer;
    }

    /**
     * @deprecated Corrects printer attributes inconsistenties, removes unwanted
     *             choices, etc.
     *
     * @param group
     *            The {@link IppDelimiterTag#PRINTER_ATTR} group.
     */
    @Deprecated
    private void alterPrinterAttr(IppAttrGroup group) {

        boolean isColorDevice =
                group.getAttrSingleValue(
                        IppDictPrinterDescAttr.ATTR_COLOR_SUPPORTED,
                        IppBoolean.FALSE).equals(IppBoolean.TRUE);

        /*
         * ftp://ftp.pwg.org/pub/pwg/candidates/cs-ippjobprinterext3v10-20120727-
         * 5100.13.pdf
         *
         * A color printer MUST support the 'color' and 'monochrome' options.
         */
        if (isColorDevice) {

            IppAttrValue attrValue =
                    group.getAttrValue(IppDictJobTemplateAttr.ATTR_PRINT_COLOR_MODE_SUPP);

            if (attrValue == null) {

                attrValue =
                        new IppAttrValue(
                                IppDictJobTemplateAttr.ATTR_PRINT_COLOR_MODE_SUPP,
                                IppKeyword.instance());
            } else {
                /*
                 * We remove the 'auto' value, since we want to force the user
                 * to make an explicit choice.
                 */
                attrValue.removeValue(IppKeyword.PRINT_COLOR_MODE_AUTO);
            }

            if (attrValue.getValues().isEmpty()) {
                /*
                 * First one is picked up as the default.
                 */
                attrValue.addValue(IppKeyword.PRINT_COLOR_MODE_MONOCHROME);
                attrValue.addValue(IppKeyword.PRINT_COLOR_MODE_COLOR);
                group.addAttribute(attrValue);
            }
        }
    }

    /**
     * Creates a {@link JsonProxyPrinter}, with a subset of IPP option
     * (attributes).
     * <p>
     * NOTE: Irrelevant (raw) IPP options from the {@link IppAttrGroup} input
     * parameter are not copied to the output {@link JsonProxyPrinter}.
     * </p>
     *
     * @param defaultPrinter
     *            {@code null} when default printer is unknown.
     * @param group
     *            The (raw) IPP printer options.
     * @return the {@link JsonProxyPrinter} or {@code null} when printer
     *         definition is not valid somehow.
     */
    private JsonProxyPrinter createUserPrinter(
            final JsonProxyPrinter defaultPrinter, final IppAttrGroup group) {

        final JsonProxyPrinter printer = new JsonProxyPrinter();

        /*
         * Mantis #403: Cache CUPS printer-name internally as upper-case.
         */
        printer.setName(ProxyPrinterName.getDaoName(group
                .getAttrSingleValue(IppDictPrinterDescAttr.ATTR_PRINTER_NAME)));

        printer.setManufacturer(group
                .getAttrSingleValue(IppDictPrinterDescAttr.ATTR_PRINTER_MORE_INFO_MANUFACTURER));
        printer.setModelName(group
                .getAttrSingleValue(IppDictPrinterDescAttr.ATTR_PRINTER_MAKE_MODEL));

        // Device URI
        final URI deviceUri;

        final String deviceUriValue;

        try {
            deviceUriValue =
                    group.getAttrSingleValue(IppDictPrinterDescAttr.ATTR_DEVICE_URI);

            if (deviceUriValue == null) {
                deviceUri = null;
            } else {
                deviceUri = new URI(deviceUriValue);
            }
        } catch (URISyntaxException e) {
            throw new SpException(e.getMessage());
        }

        printer.setDeviceUri(deviceUri);

        // Printer URI
        final URI printerUri;

        try {
            final String uriValue =
                    group.getAttrSingleValue(IppDictPrinterDescAttr.ATTR_PRINTER_URI_SUPPORTED);
            if (uriValue == null) {
                // Mantis #585
                LOGGER.warn(String.format(
                        "Skipping printer [%s] model [%s] device URI [%s]"
                                + ": no printer URI", printer.getName(),
                        StringUtils.defaultString(printer.getModelName(), ""),
                        StringUtils.defaultString(deviceUriValue, "")));
                return null;
            } else {
                printerUri = new URI(uriValue);
            }
        } catch (URISyntaxException e) {
            throw new SpException(e.getMessage());
        }

        printer.setPrinterUri(printerUri);

        //
        printer.setInfo(group
                .getAttrSingleValue(IppDictPrinterDescAttr.ATTR_PRINTER_INFO));

        printer.setAcceptingJobs(group.getAttrSingleValue(
                IppDictPrinterDescAttr.ATTR_PRINTER_IS_ACCEPTING_JOBS,
                IppBoolean.TRUE).equals(IppBoolean.TRUE));

        printer.setLocation(group
                .getAttrSingleValue(IppDictPrinterDescAttr.ATTR_PRINTER_LOCATION));
        printer.setState(group
                .getAttrSingleValue(IppDictPrinterDescAttr.ATTR_PRINTER_STATE));
        printer.setStateChangeTime(group
                .getAttrSingleValue(IppDictPrinterDescAttr.ATTR_PRINTER_STATE_CHANGE_TIME));
        printer.setStateReasons(group
                .getAttrSingleValue(IppDictPrinterDescAttr.ATTR_PRINTER_STATE_REASONS));

        printer.setColorDevice(group.getAttrSingleValue(
                IppDictPrinterDescAttr.ATTR_COLOR_SUPPORTED, IppBoolean.FALSE)
                .equals(IppBoolean.TRUE));

        printer.setDuplexDevice(Boolean.FALSE);

        // ----------------
        ArrayList<JsonProxyPrinterOptGroup> printerOptGroups =
                new ArrayList<>();
        printer.setGroups(printerOptGroups);

        // ---------------------
        // Options
        // ---------------------
        String[] attrKeywords = null;
        ArrayList<JsonProxyPrinterOpt> printerOptions = null;

        // ---------------------
        // Options: Page Setup
        // ---------------------
        printerOptions = new ArrayList<>();

        /*
         * Note that the option order in the array is the top-down order as they
         * appear in the Web App.
         */
        attrKeywords = new String[] {
        /* */
        IppDictJobTemplateAttr.ATTR_MEDIA_SOURCE,
        /* */
        IppDictJobTemplateAttr.ATTR_MEDIA,
        /* */
        IppDictJobTemplateAttr.ATTR_SIDES,
        /* */
        IppDictJobTemplateAttr.ATTR_PRINT_COLOR_MODE,
        /* */
        IppDictJobTemplateAttr.ATTR_PRINTER_RESOLUTION,
        /* */
        IppDictJobTemplateAttr.ATTR_NUMBER_UP,

        /*
         * Mantis #408: disable ...
         *
         * IppDictJobTemplateAttr.ATTR_PRINT_QUALITY
         *
         * IppDictJobTemplateAttr.ATTR_ORIENTATION_REQUESTED
         */
        };

        for (final String keyword : attrKeywords) {
            addOption(printer, group, printerOptions, keyword);
        }

        if (!printerOptions.isEmpty()) {

            final JsonProxyPrinterOptGroup optGroup =
                    new JsonProxyPrinterOptGroup();

            optGroup.setOptions(printerOptions);
            optGroup.setName(OPTGROUP_PAGE_SETUP);
            optGroup.setText(OPTGROUP_PAGE_SETUP);

            printerOptGroups.add(optGroup);
        }

        // ---------------------
        // Options: Job
        // ---------------------

        // Add Cover Page - Before: After:
        //
        printerOptions = new ArrayList<>();

        attrKeywords = new String[] {
        /*
         * No entries intended.
         */
        };

        for (final String keyword : attrKeywords) {
            addOption(printer, group, printerOptions, keyword);
        }

        if (!printerOptions.isEmpty()) {

            final JsonProxyPrinterOptGroup optGroup =
                    new JsonProxyPrinterOptGroup();

            optGroup.setOptions(printerOptions);
            optGroup.setName(OPTGROUP_JOB);
            optGroup.setText(OPTGROUP_JOB);

            printerOptGroups.add(optGroup);
        }

        // ---------------------
        // Options: Advanced
        // ---------------------
        printerOptions = new ArrayList<>();

        attrKeywords = new String[] {
        /* */
        IppDictJobTemplateAttr.ATTR_FINISHINGS,
        /*
         * Mantis #408: disable ...
         *
         * IppDictJobTemplateAttr.ATTR_OUTPUT_BIN
         */
        };

        for (final String keyword : attrKeywords) {
            addOption(printer, group, printerOptions, keyword);
        }

        if (!printerOptions.isEmpty()) {

            final JsonProxyPrinterOptGroup optGroup =
                    new JsonProxyPrinterOptGroup();

            optGroup.setOptions(printerOptions);
            optGroup.setName(OPTGROUP_ADVANCED);
            optGroup.setText(OPTGROUP_ADVANCED);

            printerOptGroups.add(optGroup);
        }

        // -----------------------------------------
        printer.setDfault(defaultPrinter != null
                && defaultPrinter.hasSameName(printer));

        /*
         * TODO: The PPD values are used to see if a printer "changed", but this
         * becomes obsolete as soon as we utilize event subscription fully.
         *
         * For now make sure PPD values are NOT NULL !!
         *
         * "pcfilename":"HL1250.PPD", "FileVersion":"1.1"
         */
        printer.setPpd("");
        printer.setPpdVersion("");

        return printer;
    }

    @Override
    public void localizePrinterOptChoices(final Locale locale,
            final String attrKeyword,
            final List<JsonProxyPrinterOptChoice> choices) {

        final boolean isMedia =
                attrKeyword.equals(IppDictJobTemplateAttr.ATTR_MEDIA);

        for (final JsonProxyPrinterOptChoice optChoice : choices) {

            final String choice = optChoice.getChoice();

            String choiceTextDefault = choice;

            if (isMedia) {

                final IppMediaSizeEnum ippMediaSize =
                        IppMediaSizeEnum.find(choice);

                if (ippMediaSize == null) {
                    continue;
                }

                final MediaSizeName mediaSizeName =
                        ippMediaSize.getMediaSizeName();

                choiceTextDefault =
                        localizeWithDefault(locale, LOCALIZE_IPP_ATTR_PREFIX
                                + attrKeyword + "-" + mediaSizeName.toString(),
                                mediaSizeName.toString());
            }

            optChoice.setText(localizeWithDefault(locale,
                    LOCALIZE_IPP_ATTR_PREFIX + attrKeyword + "-" + choice,
                    choiceTextDefault));
        }
    }

    @Override
    public void localizePrinterOption(final Locale locale,
            final JsonProxyPrinterOpt option) {

        final String attrKeyword = option.getKeyword();
        option.setText(localize(locale, LOCALIZE_IPP_ATTR_PREFIX + attrKeyword));
        localizePrinterOptChoices(locale, attrKeyword, option.getChoices());
    }

    @Override
    public void localize(final Locale locale,
            final JsonPrinterDetail printerDetail) {

        for (final JsonProxyPrinterOptGroup optGroup : printerDetail
                .getGroups()) {

            if (optGroup.getName().equals(OPTGROUP_ADVANCED)) {
                optGroup.setText(localize(locale, "ipp-cat-advanced"));
            } else if (optGroup.getName().equals(OPTGROUP_JOB)) {
                optGroup.setText(localize(locale, "ipp-cat-job"));
            } else if (optGroup.getName().equals(OPTGROUP_PAGE_SETUP)) {
                optGroup.setText(localize(locale, "ipp-cat-page-setup"));
            }

            for (final JsonProxyPrinterOpt option : optGroup.getOptions()) {
                localizePrinterOption(locale, option);
            }
        }
    }

    @Override
    public String localizeMnemonic(final MediaSizeName mediaSizeName) {
        return localizeWithDefault(ServiceContext.getLocale(),
                LOCALIZE_IPP_ATTR_PREFIX + IppDictJobTemplateAttr.ATTR_MEDIA
                        + "-" + mediaSizeName.toString(),
                mediaSizeName.toString());
    }

    /**
     * Adds an option to the printerOptions parameter.
     * <p>
     * See <a href="https://secure.datraverse.nl/mantis/view.php?id=185">Mantis
     * #185</a>.
     * </p>
     *
     * @param printer
     *            The {@link JsonProxyPrinter}.
     * @param group
     *            The {@link IppAttrGroup}.
     * @param printerOptions
     *            The list of {@link JsonProxyPrinterOpt} to add the option to.
     * @param attrKeyword
     *            The IPP attribute keyword.
     */
    private void addOption(final JsonProxyPrinter printer,
            final IppAttrGroup group,
            final ArrayList<JsonProxyPrinterOpt> printerOptions,
            final String attrKeyword) {

        /*
         * INVARIANT: More than one (1) choice.
         */
        final IppAttrValue attrChoice =
                group.getAttrValue(IppDictJobTemplateAttr.attrName(attrKeyword,
                        ApplEnum.SUPPORTED));

        if (attrChoice == null || attrChoice.getValues().size() < 2) {
            return;
        }

        /*
         * If no default found, use the first from the list of choices.
         */
        String defChoice =
                group.getAttrSingleValue(IppDictJobTemplateAttr.attrName(
                        attrKeyword, ApplEnum.DEFAULT));

        if (defChoice == null) {
            defChoice = attrChoice.getValues().get(0);
        }

        final String txtKeyword = attrKeyword;

        final JsonProxyPrinterOpt option = new JsonProxyPrinterOpt();
        option.setUi(UI_PICKONE);

        option.setKeyword(attrKeyword);
        option.setText(txtKeyword);

        String defChoiceFound = attrChoice.getValues().get(0);

        final boolean isMedia =
                attrKeyword.equals(IppDictJobTemplateAttr.ATTR_MEDIA);

        final boolean isMediaSource =
                attrKeyword.equals(IppDictJobTemplateAttr.ATTR_MEDIA_SOURCE);

        final boolean isNup =
                attrKeyword.equals(IppDictJobTemplateAttr.ATTR_NUMBER_UP);

        final boolean isSides =
                attrKeyword.equals(IppDictJobTemplateAttr.ATTR_SIDES);

        boolean isDuplexPrinter = false;
        boolean hasManualMediaSource = false;
        boolean hasAutoMediaSource = false;

        for (final String choice : attrChoice.getValues()) {

            if (isMediaSource
                    && choice.equalsIgnoreCase(IppKeyword.MEDIA_SOURCE_AUTO)) {
                hasAutoMediaSource = true;
                continue;
            }

            /*
             * Mantis #185: Limit IPP n-up to max 9 (1 character).
             */
            if (isNup && choice.length() > 1) {
                continue;
            }

            if (isMedia && IppMediaSizeEnum.find(choice) == null) {
                continue;
            }

            if (isSides && !choice.equalsIgnoreCase(IppKeyword.SIDES_ONE_SIDED)) {
                isDuplexPrinter = true;
            } else if (isMediaSource
                    && choice.equalsIgnoreCase(IppKeyword.MEDIA_SOURCE_MANUAL)) {
                hasManualMediaSource = true;
            }

            if (choice.equals(defChoice)) {
                defChoiceFound = defChoice;
            }

            option.addChoice(choice, choice);
        }

        option.setDefchoice(defChoiceFound);
        option.setDefchoiceIpp(defChoiceFound);

        if (option.getChoices().size() > 1) {
            printerOptions.add(option);
        }

        if (isSides) {
            printer.setDuplexDevice(Boolean.valueOf(isDuplexPrinter));
        } else if (isMediaSource) {
            printer.setAutoMediaSource(Boolean.valueOf(hasAutoMediaSource));
            printer.setManualMediaSource(Boolean.valueOf(hasManualMediaSource));
        }

    }

    @Override
    protected List<JsonProxyPrintJob> retrievePrintJobs(
            final String printerName, final List<Integer> jobIds)
            throws IppConnectException {

        final List<JsonProxyPrintJob> jobs = new ArrayList<>();

        final JsonProxyPrinter proxyPrinter =
                this.getCachedPrinter(printerName);

        if (proxyPrinter != null) {

            final String printerUri = proxyPrinter.getPrinterUri().toString();

            final URL urlCupsServer;

            try {
                urlCupsServer =
                        this.getCupsServerUrl(proxyPrinter.getPrinterUri());
            } catch (MalformedURLException e) {
                throw new IppConnectException(e);
            }

            for (final Integer jobId : jobIds) {
                final JsonProxyPrintJob job =
                        retrievePrintJobUri(urlCupsServer, printerUri, null,
                                jobId);
                if (job != null) {
                    jobs.add(job);
                }
            }

        } else {
            /*
             * Remote printer might not be present when remote CUPS is down, or
             * when connection is refused.
             */
            if (LOGGER.isWarnEnabled()) {
                LOGGER.warn("Proxy printer [" + printerName
                        + "] not found in cache: possibly due "
                        + "to remote CUPS connection problem.");
            }
        }

        return jobs;
    }

    /**
     *
     * @param jobUri
     * @return
     */
    private static String jobIdFromJobUri(final String jobUri) {
        return jobUri.substring(jobUri.lastIndexOf('/') + 1);
    }

    @Override
    protected void printPdf(final PrintModeEnum printMode,
            final JsonProxyPrinter printer, final String user,
            final File filePdf, final String jobName, final int copies,
            final Boolean fitToPage, final Map<String, String> optionValues,
            final DocLog docLog) throws IppConnectException {

        final String uriPrinter = printer.getPrinterUri().toString();

        final URL urlCupsServer;

        try {
            urlCupsServer = getCupsServerUrl(printer.getPrinterUri());
        } catch (MalformedURLException e) {
            throw new SpException(e.getMessage());
        }

        /*
         * Construct.
         */
        final String jobNameWork;
        if (StringUtils.isBlank(jobName)) {
            jobNameWork =
                    String.format("%s-%s",
                            CommunityDictEnum.SAVAPAGE.getWord(), DateUtil
                                    .formattedDateTime(ServiceContext
                                            .getTransactionDate()));
        } else {
            jobNameWork = jobName;
        }

        // Print
        final List<IppAttrGroup> response =
                ippClient.send(
                        urlCupsServer,
                        IppOperationId.PRINT_JOB,
                        reqPrintJob(filePdf, uriPrinter, user, jobNameWork,
                                jobNameWork, copies, fitToPage, optionValues),
                        filePdf);

        final IppAttrGroup group = response.get(1);

        // The job-uri can be null.
        final String jobUri =
                group.getAttrSingleValue(IppDictOperationAttr.ATTR_JOB_URI);

        // The job-id can be null.
        String jobId =
                group.getAttrSingleValue(IppDictOperationAttr.ATTR_JOB_ID);

        if (jobId == null) {

            /*
             * Create the job-id from job-uri. Example:
             * ipp://192.168.1.200:631/jobs/65
             */
            if (jobUri == null) {
                throw new SpException("job id could not be determined.");
            }

            jobId = jobIdFromJobUri(jobUri);
        }

        final JsonProxyPrintJob printJob =
                retrievePrintJobUri(urlCupsServer, null, jobUri,
                        Integer.valueOf(jobId, 10));

        /*
         * Note: if the "media-source" is "manual", the printJob is returned
         * with status "processing".
         */
        printJob.setUser(user); // needed??

        // ------------------
        final boolean duplex = ProxyPrintInboxReq.isDuplex(optionValues);
        final boolean grayscale = ProxyPrintInboxReq.isGrayscale(optionValues);
        final int nUp = ProxyPrintInboxReq.getNup(optionValues);

        final boolean oddOrEvenSheets =
                ProxyPrintInboxReq.isOddOrEvenSheets(optionValues);
        final boolean coverPageBefore =
                ProxyPrintInboxReq.isCoverPageBefore(optionValues);
        final boolean coverPageAfter =
                ProxyPrintInboxReq.isCoverPageAfter(optionValues);

        final String cupsPageSet = "all";
        final String cupsJobSheets = "";

        // ------------------
        final MediaSizeName mediaSizeName =
                IppMediaSizeEnum.findMediaSizeName(optionValues
                        .get(IppDictJobTemplateAttr.ATTR_MEDIA));

        collectPrintOutData(docLog, DocLogProtocolEnum.IPP, printMode, printer,
                printJob, jobName, copies, duplex, grayscale, nUp, cupsPageSet,
                oddOrEvenSheets, cupsJobSheets, coverPageBefore,
                coverPageAfter, mediaSizeName);
    }

    /**
     * Gets the default {@link JsonProxyPrinter}.
     *
     * @return The default {@link JsonProxyPrinter} or {@code null} when not
     *         found.
     * @throws IppConnectException
     */
    private JsonProxyPrinter getCupsDefaultPrinter() throws IppConnectException {

        final List<IppAttrGroup> request = new ArrayList<>();
        request.add(createOperationGroup());

        final List<IppAttrGroup> response = new ArrayList<>();

        final IppStatusCode statusCode =
                ippClient.send(getUrlDefaultServer(),
                        IppOperationId.CUPS_GET_DEFAULT, request, response);

        if (statusCode == IppStatusCode.CLI_NOTFND) {
            return null;
        }

        final JsonProxyPrinter printer =
                createUserPrinter(null, response.get(1));

        if (printer != null) {
            printer.setDfault(true);
        }

        return printer;
    }

    /**
     * Retrieves the print job data using the URI of the printer or the job.
     *
     * @param urlCupsServer
     *            The {@link URL} of the CUPS server.
     * @param uriPrinter
     *            If {@code null} uriJob is used.
     * @param uriJob
     *            If {@code null} uriPrinter and jobId is used.
     * @param jobId
     * @return {@code null} when print job is not found.
     * @throws IppConnectException
     */
    private JsonProxyPrintJob retrievePrintJobUri(final URL urlCupsServer,
            final String uriPrinter, String uriJob, Integer jobId)
            throws IppConnectException {

        List<IppAttrGroup> response = new ArrayList<>();

        IppStatusCode statusCode = null;

        if (uriJob == null) {
            statusCode =
                    ippClient.send(urlCupsServer, IppOperationId.GET_JOB_ATTR,
                            reqGetJobAttr(uriPrinter, jobId), response);
        } else {
            statusCode =
                    ippClient.send(urlCupsServer, IppOperationId.GET_JOB_ATTR,
                            reqGetJobAttr(uriJob), response);
        }

        final JsonProxyPrintJob job;

        if (statusCode == IppStatusCode.OK && response.size() > 1) {

            job = new JsonProxyPrintJob();

            job.setJobId(jobId);

            IppAttrGroup group = response.get(1);

            job.setDest(group
                    .getAttrSingleValue(IppDictJobDescAttr.ATTR_JOB_PRINTER_URI));
            job.setTitle(group
                    .getAttrSingleValue(IppDictJobDescAttr.ATTR_JOB_NAME));
            job.setJobState(Integer.parseInt(
                    group.getAttrSingleValue(IppDictJobDescAttr.ATTR_JOB_STATE),
                    10));

            job.setCreationTime(Integer.valueOf(
                    group.getAttrSingleValue(IppDictJobDescAttr.ATTR_TIME_AT_CREATION),
                    10));

            String value =
                    group.getAttrSingleValue(
                            IppDictJobDescAttr.ATTR_TIME_AT_COMPLETED, "");

            if (StringUtils.isNotBlank(value)) {
                job.setCompletedTime(Integer.parseInt(value, 10));
            }

        } else {
            job = null;
        }
        // ---------------
        return job;
    }

    @Override
    protected void stopSubscription(final String requestingUser,
            final String recipientUri) throws IppConnectException,
            IppSyntaxException {

        /*
         * Step 1: Get the existing printer subscriptions for requestingUser.
         */
        final List<IppAttrGroup> response = new ArrayList<>();

        final IppStatusCode statusCode =
                ippClient.send(
                        getUrlDefaultServer(),
                        IppOperationId.GET_SUBSCRIPTIONS,
                        reqGetPrinterSubscriptions(SUBSCRIPTION_PRINTER_URI,
                                requestingUser), response);
        /*
         * NOTE: it is possible that there are NO subscriptions for the user,
         * this will given an IPP_NOT_FOUND.
         */
        if (statusCode != IppStatusCode.OK
                && statusCode != IppStatusCode.CLI_NOTFND) {
            throw new IppSyntaxException(statusCode.toString());
        }

        /*
         * Step 2: Cancel only our OWN printer subscription.
         */
        for (final IppAttrGroup group : response) {

            if (group.getDelimiterTag() != IppDelimiterTag.SUBSCRIPTION_ATTR) {
                continue;
            }

            /*
             * There might be other subscription that are NOT ours (like native
             * CUPS descriptions).
             */
            final String recipientUriFound =
                    group.getAttrSingleValue(IppDictSubscriptionAttr.ATTR_NOTIFY_RECIPIENT_URI);

            if (recipientUriFound == null
                    || !recipientUri.equals(recipientUriFound)) {
                continue;
            }

            final String subscriptionId =
                    group.getAttrSingleValue(IppDictSubscriptionAttr.ATTR_NOTIFY_SUBSCRIPTION_ID);

            ippClient
                    .send(getUrlDefaultServer(),
                            IppOperationId.CANCEL_SUBSCRIPTION,
                            reqCancelPrinterSubscription(requestingUser,
                                    subscriptionId));
        }

    }

    @Override
    protected void startSubscription(final String requestingUser,
            final String leaseSeconds, final String recipientUri)
            throws IppConnectException, IppSyntaxException {

        if (ConfigManager.isCupsPushNotification()) {

            startPushSubscription(requestingUser, recipientUri, leaseSeconds);

        } else {

            final String subscriptionId =
                    startPullSubscription(requestingUser, leaseSeconds);

            final long secondsFromNow = 2L;

            SpJobScheduler.instance().scheduleOneShotIppNotifications(
                    requestingUser, subscriptionId, secondsFromNow);
        }
    }

    /**
     *
     * @param requestingUser
     *            The requesting user.
     * @param subscriptionId
     *            The subscription id.
     *
     * @return The IPP request.
     */
    private List<IppAttrGroup> reqCancelPrinterSubscription(
            final String requestingUser, final String subscriptionId) {

        List<IppAttrGroup> attrGroups = new ArrayList<>();

        IppAttrGroup group = null;
        AbstractIppDict dict = null;

        /*
         * Group 1: Operation Attributes
         */
        group = createOperationGroup();
        attrGroups.add(group);

        dict = IppDictOperationAttr.instance();

        group.add(dict.getAttr(IppDictOperationAttr.ATTR_PRINTER_URI),
                SUBSCRIPTION_PRINTER_URI);
        group.add(dict.getAttr(IppDictOperationAttr.ATTR_REQUESTING_USER_NAME),
                requestingUser);
        group.add(
                dict.getAttr(IppDictOperationAttr.ATTR_NOTIFY_SUBSCRIPTION_ID),
                subscriptionId);
        // ---------
        return attrGroups;
    }

    /**
     * Creates an IPP request to renews the printer subscription.
     *
     * @param requestingUser
     *            The requesting user.
     * @param subscriptionId
     *            The subscription id.
     * @param leaseSeconds
     *            The lease seconds.
     *
     * @return The IPP request.
     */
    private List<IppAttrGroup> reqRenewPrinterSubscription(
            final String requestingUser, final String subscriptionId,
            final String leaseSeconds) {

        List<IppAttrGroup> attrGroups = new ArrayList<>();

        IppAttrGroup group = null;
        AbstractIppDict dict = null;

        /*
         * Group 1: Operation Attributes
         */
        group = createOperationGroup();
        attrGroups.add(group);

        dict = IppDictOperationAttr.instance();

        group.add(dict.getAttr(IppDictOperationAttr.ATTR_PRINTER_URI),
                SUBSCRIPTION_PRINTER_URI);
        group.add(dict.getAttr(IppDictOperationAttr.ATTR_REQUESTING_USER_NAME),
                requestingUser);
        group.add(
                dict.getAttr(IppDictOperationAttr.ATTR_NOTIFY_SUBSCRIPTION_ID),
                subscriptionId);

        /*
         * Group 2: Subscription Attributes
         */
        group = new IppAttrGroup(IppDelimiterTag.SUBSCRIPTION_ATTR);
        attrGroups.add(group);

        dict = IppDictSubscriptionAttr.instance();

        group.add(dict
                .getAttr(IppDictSubscriptionAttr.ATTR_NOTIFY_LEASE_DURATION),
                leaseSeconds);

        // ---------
        return attrGroups;
    }

    /**
     * Creates an IPP request to create the printer subscription.
     * <p>
     * <b>Note</b>: either recipientUri or notifyPullMethod must be specified.
     * </p>
     *
     * @param requestingUser
     *            The requesting user.
     * @param recipientUri
     *            The recipient as as value of
     *            {@link IppDictSubscriptionAttr#ATTR_NOTIFY_RECIPIENT_URI}
     *            <p>
     *            {@code null} when notifyPullMethod is specified.
     *            </p>
     * @param notifyPullMethod
     *            The pull method as value of
     *            {@link IppDictSubscriptionAttr#ATTR_NOTIFY_PULL_METHOD}
     *            <p>
     *            {@code null} when recipientUri is specified.
     *            </p>
     * @param leaseSeconds
     *            The lease seconds.
     *
     * @return The IPP request.
     */
    private List<IppAttrGroup> reqCreatePrinterSubscriptions(
            String requestingUser, String recipientUri,
            String notifyPullMethod, String leaseSeconds) {

        List<IppAttrGroup> attrGroups = new ArrayList<>();

        IppAttrGroup group = null;
        AbstractIppDict dict = null;

        /*
         * Group 1: Operation Attributes
         */
        group = createOperationGroup();
        attrGroups.add(group);

        dict = IppDictOperationAttr.instance();

        group.add(dict.getAttr(IppDictOperationAttr.ATTR_PRINTER_URI),
                SUBSCRIPTION_PRINTER_URI);
        group.add(dict.getAttr(IppDictOperationAttr.ATTR_REQUESTING_USER_NAME),
                requestingUser);

        /*
         * Group 2: Subscription Attributes
         */
        group = new IppAttrGroup(IppDelimiterTag.SUBSCRIPTION_ATTR);
        attrGroups.add(group);

        dict = IppDictSubscriptionAttr.instance();

        if (recipientUri != null) {
            group.add(
                    dict.getAttr(IppDictSubscriptionAttr.ATTR_NOTIFY_RECIPIENT_URI),
                    recipientUri);
        }

        if (notifyPullMethod != null) {
            group.add(dict
                    .getAttr(IppDictSubscriptionAttr.ATTR_NOTIFY_PULL_METHOD),
                    notifyPullMethod);
        }

        group.add(dict.getAttr(IppDictSubscriptionAttr.ATTR_NOTIFY_USER_DATA),
                NOTIFY_USER_DATA);

        group.add(dict
                .getAttr(IppDictSubscriptionAttr.ATTR_NOTIFY_LEASE_DURATION),
                leaseSeconds);

        /*
         * group.add(dict.getAttr(IppDictSubscriptionAttr.ATTR_NOTIFY_TIME_INTERVAL
         * ), "5");
         */

        /*
         * Printer and Jobs events to subscribe on ...
         */
        final String[] events = {
        /* */
        "printer-config-changed",
        /* */
        "printer-media-changed",
        /* */
        "printer-queue-order-changed",
        /* */
        "printer-restarted",
        /* */
        "printer-shutdown",
        /* */
        "printer-state-changed",
        /* */
        "printer-stopped",
        /* */
        "job-state-changed",
        /* */
        "job-created",
        /* */
        "job-completed",
        /* */
        "job-stopped",

        /* */
        /* "job-progress" */

        /* CUPS event: printer or class is added */
        "printer-added",
        /* CUPS event: printer or class is deleted */
        "printer-deleted",
        /* CUPS event: printer or class is modified */
        "printer-modified",
        /* CUPS event: security condition occurs */
        "server-audit",
        /* CUPS event: server is restarted */
        "server-restarted",
        /* CUPS event: server is started */
        "server-started",
        /* CUPS event: server is stopped */
        "server-stopped"
        //
                };

        IppAttrValue attrEvents =
                new IppAttrValue(
                        dict.getAttr(IppDictSubscriptionAttr.ATTR_NOTIFY_EVENTS));

        for (String event : events) {
            attrEvents.addValue(event);
        }

        group.addAttribute(attrEvents);

        // ---------
        return attrGroups;
    }

    /**
     *
     * @param uriPrinter
     * @param requestingUser
     * @return The IPP request.
     */
    private List<IppAttrGroup> reqGetPrinterSubscriptions(
            final String uriPrinter, String requestingUser) {

        List<IppAttrGroup> attrGroups = new ArrayList<>();
        IppAttrGroup group = null;

        /*
         * Group 1: Operation Attributes
         */
        group = createOperationGroup();
        attrGroups.add(group);

        // ---------
        AbstractIppDict dict = IppDictOperationAttr.instance();
        group.add(dict.getAttr(IppDictOperationAttr.ATTR_PRINTER_URI),
                uriPrinter);
        group.add(dict.getAttr(IppDictOperationAttr.ATTR_MY_SUBSCRIPTIONS),
                IppBoolean.TRUE);
        group.add(dict.getAttr(IppDictOperationAttr.ATTR_REQUESTING_USER_NAME),
                requestingUser);

        // ---------
        return attrGroups;
    }

    private static final String SUBSCRIPTION_PRINTER_URI = "ipp://localhost/";

    /**
     *
     * @param uriJob
     * @param reqUser
     * @return
     */
    private List<IppAttrGroup> reqGetJobAttr(final String uriJob) {

        List<IppAttrGroup> attrGroups = new ArrayList<>();

        IppAttrGroup group = null;
        AbstractIppDict dict = null;

        /*
         * Group 1: Operation Attributes
         */
        group = createOperationGroup();
        attrGroups.add(group);

        dict = IppDictOperationAttr.instance();

        group.add(dict.getAttr(IppDictOperationAttr.ATTR_JOB_URI), uriJob);

        // ---------
        return attrGroups;
    }

    /**
     *
     * @param reqUser
     * @param uriPrinter
     * @param jobId
     * @return
     */
    private List<IppAttrGroup> reqGetJobAttr(final String uriPrinter,
            final Integer jobId) {

        List<IppAttrGroup> attrGroups = new ArrayList<>();

        IppAttrGroup group = null;
        AbstractIppDict dict = null;

        /*
         * Group 1: Operation Attributes
         */
        group = createOperationGroup();
        attrGroups.add(group);

        dict = IppDictOperationAttr.instance();

        // ---------
        group.add(dict.getAttr(IppDictOperationAttr.ATTR_PRINTER_URI),
                uriPrinter);
        group.add(dict.getAttr(IppDictOperationAttr.ATTR_JOB_ID),
                jobId.toString());

        // ---------
        return attrGroups;
    }

    /**
     * Creates a Print Job IPP request.
     *
     * @param uriPrinter
     * @throws IOException
     */
    private List<IppAttrGroup> reqPrintJob(final File fileToPrint,
            final String uriPrinter, final String reqUser,
            final String docName, final String jobName, int copies,
            final Boolean fitToPage, Map<String, String> optionValues) {

        final List<IppAttrGroup> attrGroups = new ArrayList<>();

        IppAttrGroup group = null;
        AbstractIppDict dict = null;

        /*
         * Group 1: Operation Attributes
         */
        group = createOperationGroup();
        attrGroups.add(group);

        dict = IppDictOperationAttr.instance();

        group.add(dict.getAttr(IppDictOperationAttr.ATTR_PRINTER_URI),
                uriPrinter);

        group.add(dict.getAttr(IppDictOperationAttr.ATTR_JOB_NAME), jobName);

        group.add(dict.getAttr(IppDictOperationAttr.ATTR_REQUESTING_USER_NAME),
                reqUser);

        group.add(dict.getAttr(IppDictOperationAttr.ATTR_JOB_K_OCTETS),
                String.valueOf(fileToPrint.length()));

        group.add(
                dict.getAttr(IppDictOperationAttr.ATTR_IPP_ATTRIBUTE_FIDELITY),
                IppBoolean.TRUE);

        group.add(dict.getAttr(IppDictOperationAttr.ATTR_DOCUMENT_NAME),
                docName);

        group.add(dict.getAttr(IppDictOperationAttr.ATTR_COMPRESSION),
                IppKeyword.COMPRESSION_NONE);

        group.add(dict.getAttr(IppDictOperationAttr.ATTR_DOCUMENT_FORMAT),
                IppMimeMediaType.PDF);

        // "document-natural-language" (naturalLanguage):

        /*
         * An "impression" is the image (possibly many print-stream pages in
         * different configurations) imposed onto a single media page.
         */

        // "job-impressions" (integer(0:MAX)):

        /*
         *
         */
        // "job-media-sheets" (integer(0:MAX)):

        /*
         * Group 2: Job Template Attributes
         *
         * The client OPTIONALLY supplies a set of Job Template attributes as
         * defined in section 4.2. If the client is not supplying any Job
         * Template attributes in the request, the client SHOULD omit Group 2
         * rather than sending an empty group. However, a Printer object MUST be
         * able to accept an empty group.
         */

        /*
         * No full bleed for now.
         */
        final boolean isFullBleed = false;

        /*
         * TODO: dependent on CUPS version. When was it implemented?
         */
        final boolean usePWG5100_13 = false;

        group = null;

        IppAttrCollection collectionMediaSize = null;
        IppAttrValue attrValueMediaSource = null;

        dict = IppDictJobTemplateAttr.instance();

        for (final Entry<String, String> entry : optionValues.entrySet()) {

            final String keyword = entry.getKey();

            final IppAttr attr = dict.getAttr(keyword);

            if (attr == null) {
                LOGGER.error("unkown attribute [" + keyword + "]");
                continue;
            }

            /*
             * Lazy initialization.
             */
            if (group == null) {
                group = new IppAttrGroup(IppDelimiterTag.JOB_ATTR);
                attrGroups.add(group);
            }

            /*
             *
             */
            if (keyword.equals(IppDictJobTemplateAttr.ATTR_MEDIA)) {

                collectionMediaSize =
                        new IppAttrCollection(
                                IppDictJobTemplateAttr.ATTR_MEDIA_SIZE);

                final MediaSizeName sizeName =
                        IppMediaSizeEnum.findMediaSizeName(entry.getValue());

                final int[] array = MediaUtils.getMediaWidthHeight(sizeName);

                // Number of hundredth in a mm.
                final int hundredthMM = 100;

                collectionMediaSize.add("x-dimension", new IppInteger(0),
                        String.valueOf(array[0] * hundredthMM));
                collectionMediaSize.add("y-dimension", new IppInteger(0),
                        String.valueOf(array[1] * hundredthMM));

            } else if (keyword.equals(IppDictJobTemplateAttr.ATTR_MEDIA_SOURCE)) {

                attrValueMediaSource = new IppAttrValue(attr);
                attrValueMediaSource.addValue(entry.getValue());

            } else {
                group.add(attr, entry.getValue());
            }
        }

        /*
         * NOTE: PWG5100.13 states that "A Client specifies that is has
         * borderless or "full-bleed" content by setting all of the margins to
         * 0."
         *
         * HOWEVER, this does NOT seem to be true. We get an
         * "Unsupported margins" error when " media-*-margin-supported" are NEQ
         * zero. Why?
         *
         * Therefore, we use CUPS specific IPP Job template attributes. See:
         * http://www.cups.org/documentation.php/spec-ipp.html
         *
         * Tested OK with: CUPS 1.5.3
         */
        if (group != null && !usePWG5100_13) {

            final IppInteger syntax = new IppInteger(0);

            for (final String keyword : new String[] {
                    IppDictJobTemplateAttr.CUPS_ATTR_PAGE_BOTTOM,
                    IppDictJobTemplateAttr.CUPS_ATTR_PAGE_LEFT,
                    IppDictJobTemplateAttr.CUPS_ATTR_PAGE_RIGHT,
                    IppDictJobTemplateAttr.CUPS_ATTR_PAGE_TOP }) {

                if (isFullBleed) {
                    group.add(keyword, syntax, "0");
                }

            }
        }

        if (group != null && fitToPage != null) {

            /*
             * Mantis #205
             */
            final String fitToPageIppBoolean;

            if (fitToPage) {
                fitToPageIppBoolean = IppBoolean.TRUE;
            } else {
                fitToPageIppBoolean = IppBoolean.FALSE;
            }

            group.add(IppDictJobTemplateAttr.CUPS_ATTR_FIT_TO_PAGE,
                    IppBoolean.instance(), fitToPageIppBoolean);
        }

        /*
         * Mantis #409.
         */
        if (collectionMediaSize != null || attrValueMediaSource != null) {

            final IppAttrCollection collection =
                    new IppAttrCollection(IppDictJobTemplateAttr.ATTR_MEDIA_COL);

            group.addCollection(collection);

            if (collectionMediaSize != null) {

                collection.addCollection(collectionMediaSize);
            }

            if (attrValueMediaSource != null) {
                collection.addAttribute(attrValueMediaSource);
            }

            if (usePWG5100_13) {
                /*
                 * PWG5100.13: A Client specifies that is has borderless or
                 * "full-bleed" content by setting all of the margins to 0."
                 *
                 * HOWEVER, this does NOT seem to be true. We get an
                 * "Unsupported margins" error when " media-*-margin-supported"
                 * are NEQ zero. Why?
                 */
                final IppInteger syntax = new IppInteger(0);

                for (final String keyword : new String[] {
                        IppDictJobTemplateAttr.ATTR_MEDIA_BOTTOM_MARGIN,
                        IppDictJobTemplateAttr.ATTR_MEDIA_LEFT_MARGIN,
                        IppDictJobTemplateAttr.ATTR_MEDIA_RIGHT_MARGIN,
                        IppDictJobTemplateAttr.ATTR_MEDIA_TOP_MARGIN }) {

                    if (isFullBleed) {
                        collection.add(keyword, syntax, "0");
                    }
                }
            }
        }

        /*
         * Mantis #259.
         */
        if (copies > 1) {
            /*
             * Lazy initialization.
             */
            if (group == null) {
                group = new IppAttrGroup(IppDelimiterTag.JOB_ATTR);
                attrGroups.add(group);
            }
            group.add(IppDictJobTemplateAttr.ATTR_COPIES,
                    IppInteger.instance(), String.valueOf(copies));
        }

        // ---------
        return attrGroups;
    }

    /**
     *
     * @param uriPrinter
     * @return
     */
    private List<IppAttrGroup> reqGetPrinterAttr(final String uriPrinter) {

        final List<IppAttrGroup> attrGroups = new ArrayList<>();

        IppAttrGroup group = null;
        IppAttrValue value = null;
        AbstractIppDict dict = null;

        /*
         * Group 1: Operation Attributes
         */
        group = createOperationGroup();
        attrGroups.add(group);

        dict = IppDictOperationAttr.instance();

        // ---------
        group.add(dict.getAttr(IppDictOperationAttr.ATTR_PRINTER_URI),
                uriPrinter);

        // ---------
        value =
                new IppAttrValue(
                        dict.getAttr(IppDictOperationAttr.ATTR_REQUESTED_ATTRIBUTES));

        /*
         * Commented code below shows how to retrieve attribute subsets.
         */
        // value.addValue(IppGetPrinterAttrOperation.ATTR_GRP_JOB_TPL);
        // value.addValue(IppGetPrinterAttrOperation.ATTR_GRP_PRINTER_DESC);
        // value.addValue(IppGetPrinterAttrOperation.ATTR_GRP_MEDIA_COL_DATABASE);

        /*
         * We want them all.
         */
        value.addValue(IppGetPrinterAttrOperation.ATTR_GRP_ALL);

        group.addAttribute(value);

        // ---------
        return attrGroups;
    }

    /**
     *
     * @return
     */
    private List<IppAttrGroup> reqCupsGetPrinters() {

        List<IppAttrGroup> attrGroups = new ArrayList<>();

        IppAttrGroup group = null;

        /*
         * Group 1: Operation Attributes
         */
        group = createOperationGroup();
        attrGroups.add(group);

        /*
         *
         */
        final IppAttrValue reqAttr =
                new IppAttrValue("requested-attributes", IppKeyword.instance());

        for (final String value : new String[] {
                IppDictPrinterDescAttr.ATTR_PRINTER_URI_SUPPORTED,
                IppDictPrinterDescAttr.ATTR_PRINTER_NAME,
                IppDictPrinterDescAttr.ATTR_PRINTER_INFO,
                IppDictPrinterDescAttr.ATTR_PRINTER_IS_ACCEPTING_JOBS,
                IppDictPrinterDescAttr.ATTR_PRINTER_LOCATION,
                IppDictPrinterDescAttr.ATTR_PRINTER_STATE,
                IppDictPrinterDescAttr.ATTR_PRINTER_STATE_CHANGE_TIME,
                IppDictPrinterDescAttr.ATTR_PRINTER_STATE_REASONS,
                IppDictPrinterDescAttr.ATTR_COLOR_SUPPORTED,
                IppDictPrinterDescAttr.ATTR_PRINTER_MORE_INFO_MANUFACTURER,
                IppDictPrinterDescAttr.ATTR_PRINTER_MAKE_MODEL }) {
            reqAttr.addValue(value);
        }

        group.addAttribute(reqAttr);

        // ---------
        return attrGroups;
    }

    /**
     * Creates the first Group with Operation Attributes.
     *
     * @return
     */
    private IppAttrGroup createOperationGroup() {
        IppAttrGroup group = null;

        /*
         * Group 1: Operation Attributes
         */
        group = new IppAttrGroup(IppDelimiterTag.OPERATION_ATTR);

        AbstractIppDict dict = IppDictOperationAttr.instance();

        // ------------------------------------------------------------------
        group.add(dict.getAttr(IppDictOperationAttr.ATTR_ATTRIBUTES_CHARSET),
                "utf-8");
        group.add(
                dict.getAttr(IppDictOperationAttr.ATTR_ATTRIBUTES_NATURAL_LANG),
                "en-us");

        return group;
    }

    @Override
    public String getCupsVersion() {

        List<IppAttrGroup> reqGroups = new ArrayList<>();

        IppAttrGroup group = null;

        /*
         * Group 1: Operation Attributes
         */
        group = createOperationGroup();
        reqGroups.add(group);

        AbstractIppDict dict = IppDictOperationAttr.instance();

        group.add(dict.getAttr(IppDictOperationAttr.ATTR_REQUESTED_ATTRIBUTES),
                IppDictPrinterDescAttr.ATTR_CUPS_VERSION);

        group.add(dict.getAttr(IppDictOperationAttr.ATTR_LIMIT), "1");

        // -----------
        List<IppAttrGroup> response = new ArrayList<>();

        String version = null;

        try {
            final IppStatusCode statusCode =
                    ippClient.send(getUrlDefaultServer(),
                            IppOperationId.CUPS_GET_PRINTERS, reqGroups,
                            response);

            if (statusCode == IppStatusCode.OK) {
                version =
                        response.get(1).getAttrSingleValue(
                                IppDictPrinterDescAttr.ATTR_CUPS_VERSION);
            }

        } catch (IppConnectException e) {
            // noop
        }

        return version;
    }

    @Override
    public String getCupsApiVersion() {
        return null;
    }

    @Override
    public IppStatusCode getNotifications(final String requestingUser,
            final String subscriptionId, final List<IppAttrGroup> response)
            throws IppConnectException {

        List<IppAttrGroup> request = new ArrayList<>();

        IppAttrGroup group = null;
        AbstractIppDict dict = null;

        /*
         * Group 1: Operation Attributes
         */
        dict = IppDictOperationAttr.instance();

        group = createOperationGroup();
        request.add(group);

        // ---
        group.add(dict.getAttr(IppDictOperationAttr.ATTR_PRINTER_URI),
                SUBSCRIPTION_PRINTER_URI);

        group.add(dict.getAttr(IppDictOperationAttr.ATTR_REQUESTING_USER_NAME),
                requestingUser);

        // ---
        group.add(
                dict.getAttr(IppDictOperationAttr.ATTR_NOTIFY_SUBSCRIPTION_IDS),
                subscriptionId);

        group.add(dict.getAttr(IppDictOperationAttr.ATTR_NOTIFY_WAIT),
                IppBoolean.FALSE);

        // ------
        return ippClient.send(getUrlDefaultServer(),
                IppOperationId.GET_NOTIFICATIONS, request, response);
    }

    /**
     * Starts (renews) an asynchronous subscription using the recipient uri.
     *
     * @param requestingUser
     *            The requesting user.
     * @param recipientUri
     *            The recipient uri.
     * @param leaseSeconds
     *            The lease seconds.
     * @throws IppConnectException
     * @throws IppSyntaxException
     */
    private void startPushSubscription(final String requestingUser,
            final String recipientUri, final String leaseSeconds)
            throws IppConnectException, IppSyntaxException {

        /*
         * Step 1: Get the existing printer subscriptions for requestingUser.
         */
        List<IppAttrGroup> response = new ArrayList<>();

        IppStatusCode statusCode =
                ippClient.send(
                        getUrlDefaultServer(),
                        IppOperationId.GET_SUBSCRIPTIONS,
                        reqGetPrinterSubscriptions(SUBSCRIPTION_PRINTER_URI,
                                requestingUser), response);

        /*
         * NOTE: When this is a first-time subscription it is possible that
         * there are NO subscriptions for the user, this will result in status
         * IppStatusCode.CLI_NOTFND or IppStatusCode.CLI_NOTPOS.
         */
        if (statusCode != IppStatusCode.OK
                && statusCode != IppStatusCode.CLI_NOTFND
                && statusCode != IppStatusCode.CLI_NOTPOS) {
            throw new IppSyntaxException(statusCode.toString());
        }

        /*
         * Step 2: Renew only our OWN printer subscription.
         */
        boolean isRenewed = false;

        for (final IppAttrGroup group : response) {

            if (group.getDelimiterTag() != IppDelimiterTag.SUBSCRIPTION_ATTR) {
                continue;
            }

            /*
             * There might be other subscription that are NOT ours (like native
             * CUPS descriptions).
             */
            final String recipientUriFound =
                    group.getAttrSingleValue(IppDictSubscriptionAttr.ATTR_NOTIFY_RECIPIENT_URI);

            if (recipientUriFound == null
                    || !recipientUri.equals(recipientUriFound)) {
                continue;
            }

            final String subscriptionId =
                    group.getAttrSingleValue(IppDictSubscriptionAttr.ATTR_NOTIFY_SUBSCRIPTION_ID);

            ippClient.send(
                    getUrlDefaultServer(),
                    IppOperationId.RENEW_SUBSCRIPTION,
                    reqRenewPrinterSubscription(requestingUser, subscriptionId,
                            leaseSeconds));

            isRenewed = true;

        }

        /*
         * ... or create when not renewed.
         */
        if (!isRenewed) {
            ippClient.send(
                    getUrlDefaultServer(),
                    IppOperationId.CREATE_PRINTER_SUBSCRIPTIONS,
                    reqCreatePrinterSubscriptions(requestingUser, recipientUri,
                            null, leaseSeconds));
        }

    }

    /**
     * Starts (renews) a PULL subscription using the {@link #NOTIFY_PULL_METHOD}
     * .
     *
     * @param requestingUser
     *            The requesting user.
     * @param leaseSeconds
     *            The lease seconds.
     * @return
     * @throws IppConnectException
     * @throws IppSyntaxException
     */
    private String startPullSubscription(final String requestingUser,
            final String leaseSeconds) throws IppConnectException,
            IppSyntaxException {

        String subscriptionId = null;

        /*
         * Step 1: Get the existing printer subscriptions for requestingUser.
         */
        List<IppAttrGroup> response = new ArrayList<>();

        final IppStatusCode statusCode =
                ippClient.send(
                        getUrlDefaultServer(),
                        IppOperationId.GET_SUBSCRIPTIONS,
                        reqGetPrinterSubscriptions(SUBSCRIPTION_PRINTER_URI,
                                requestingUser), response);
        /*
         * NOTE: it is possible that there are NO subscriptions for the user,
         * this will given an IPP_NOT_FOUND.
         */
        if (statusCode != IppStatusCode.OK
                && statusCode != IppStatusCode.CLI_NOTFND) {
            throw new IppSyntaxException(statusCode.toString());
        }

        /*
         * Step 2: Renew only our OWN printer subscription.
         */
        boolean isRenewed = false;

        for (final IppAttrGroup group : response) {

            if (group.getDelimiterTag() != IppDelimiterTag.SUBSCRIPTION_ATTR) {
                continue;
            }

            /*
             * There might be other subscription that are NOT ours (like native
             * CUPS descriptions).
             */
            final String notifyPullMethod =
                    group.getAttrSingleValue(IppDictSubscriptionAttr.ATTR_NOTIFY_PULL_METHOD);

            if (notifyPullMethod == null
                    || !notifyPullMethod.equals(NOTIFY_PULL_METHOD)) {
                continue;
            }

            final String notifyUserData =
                    group.getAttrSingleValue(IppDictSubscriptionAttr.ATTR_NOTIFY_USER_DATA);

            if (notifyUserData == null
                    || !notifyUserData.equals(NOTIFY_USER_DATA)) {
                continue;
            }

            subscriptionId =
                    group.getAttrSingleValue(IppDictSubscriptionAttr.ATTR_NOTIFY_SUBSCRIPTION_ID);

            ippClient.send(
                    getUrlDefaultServer(),
                    IppOperationId.RENEW_SUBSCRIPTION,
                    reqRenewPrinterSubscription(requestingUser, subscriptionId,
                            leaseSeconds));

            isRenewed = true;

        }

        /*
         * ... or create when not renewed.
         */
        if (!isRenewed) {
            response =
                    ippClient.send(
                            getUrlDefaultServer(),
                            IppOperationId.CREATE_PRINTER_SUBSCRIPTIONS,
                            reqCreatePrinterSubscriptions(requestingUser, null,
                                    NOTIFY_PULL_METHOD, leaseSeconds));

            for (IppAttrGroup group : response) {

                if (group.getDelimiterTag() != IppDelimiterTag.SUBSCRIPTION_ATTR) {
                    continue;
                }
                subscriptionId =
                        group.getAttrSingleValue(IppDictSubscriptionAttr.ATTR_NOTIFY_SUBSCRIPTION_ID);

            }

        }

        return subscriptionId;
    }

    @Override
    public ProxyPrinterDto getProxyPrinterDto(final Printer printer) {

        ProxyPrinterDto dto = new ProxyPrinterDto();

        dto.setId(printer.getId());
        dto.setPrinterName(printer.getPrinterName());
        dto.setDisplayName(printer.getDisplayName());
        dto.setLocation(printer.getLocation());
        dto.setDisabled(printer.getDisabled());
        dto.setDeleted(printer.getDeleted());

        dto.setPresent(this.getCachedPrinter(printer.getPrinterName()) != null);

        /*
         * Printer Groups.
         */
        String printerGroups = null;

        final List<PrinterGroupMember> members =
                printer.getPrinterGroupMembers();

        if (members != null) {
            for (PrinterGroupMember member : members) {
                if (printerGroups == null) {
                    printerGroups = "";
                } else {
                    printerGroups += ",";
                }
                printerGroups += member.getGroup().getDisplayName();
            }
        }

        dto.setPrinterGroups(printerGroups);
        return dto;
    }

    @Override
    public void setProxyPrinterProps(final Printer jpaPrinter,
            final ProxyPrinterDto dto) {

        final String requestingUser = ServiceContext.getActor();
        final Date now = ServiceContext.getTransactionDate();

        jpaPrinter.setModifiedBy(requestingUser);
        jpaPrinter.setModifiedDate(now);

        jpaPrinter.setDisplayName(dto.getDisplayName());
        jpaPrinter.setDisabled(dto.getDisabled());

        /*
         * Deleted?
         */
        final boolean isDeleted = dto.getDeleted();

        if (jpaPrinter.getDeleted() != isDeleted) {

            if (isDeleted) {
                printerService().setLogicalDeleted(jpaPrinter);
            } else {
                printerService().undoLogicalDeleted(jpaPrinter);
            }
        }

        /*
         * Location.
         */
        jpaPrinter.setLocation(dto.getLocation());

        /*
         * Printer Groups.
         *
         * (1) Put the entered PrinterGroups into a map for easy lookup.
         */
        final String printerGroups = dto.getPrinterGroups();

        final Map<String, String> printerGroupLookup = new HashMap<>();

        for (String displayName : StringUtils.split(printerGroups, ',')) {

            printerGroupLookup.put(displayName.trim().toLowerCase(),
                    displayName.trim());
        }

        /*
         * (2) Remove PrinterGroupMembers which are not selected now, and remove
         * entries from the Map if member already exists.
         */
        List<PrinterGroupMember> printerGroupMembers =
                jpaPrinter.getPrinterGroupMembers();

        if (printerGroupMembers == null) {
            printerGroupMembers = new ArrayList<>();
            jpaPrinter.setPrinterGroupMembers(printerGroupMembers);
        }

        Iterator<PrinterGroupMember> iterMembers =
                printerGroupMembers.iterator();

        while (iterMembers.hasNext()) {

            PrinterGroupMember member = iterMembers.next();

            final String groupName = member.getGroup().getGroupName();

            if (printerGroupLookup.containsKey(groupName)) {
                printerGroupLookup.remove(groupName);
            } else {
                this.printerGroupMemberDAO().delete(member);
                iterMembers.remove();
            }
        }

        /*
         * (3) Lazy add new Groups and GroupMember.
         */
        for (Entry<String, String> entry : printerGroupLookup.entrySet()) {

            PrinterGroup group =
                    this.printerGroupDAO().readOrAdd(entry.getKey(),
                            entry.getValue(), requestingUser, now);

            PrinterGroupMember member = new PrinterGroupMember();

            member.setGroup(group);
            member.setPrinter(jpaPrinter);
            member.setCreatedBy(requestingUser);
            member.setCreatedDate(now);

            printerGroupMembers.add(member);
        }

        /*
         *
         */
        jpaPrinter.setModifiedDate(now);
        jpaPrinter.setModifiedBy(requestingUser);

        printerDAO().update(jpaPrinter);

        /*
         *
         */
        updateCachedPrinter(jpaPrinter);
    }

    /**
     * Gets the media-source option choices for a printer from the printer
     * cache.
     *
     * @param printerName
     *            The unique name of the printer.
     * @return
     */
    private List<JsonProxyPrinterOptChoice> getMediaSourceChoices(
            String printerName) {

        final JsonProxyPrinter proxyPrinter = getCachedPrinter(printerName);

        if (proxyPrinter != null) {
            for (JsonProxyPrinterOptGroup group : proxyPrinter.getGroups()) {
                for (JsonProxyPrinterOpt option : group.getOptions()) {
                    if (option.getKeyword().equals(
                            IppDictJobTemplateAttr.ATTR_MEDIA_SOURCE)) {
                        return option.getChoices();
                    }
                }
            }
        }

        return new ArrayList<JsonProxyPrinterOptChoice>();
    }

    /**
     * Gets the media costs of a Printer from the database.
     *
     * @param printer
     *            The Printer.
     * @return
     */
    private Map<String, IppMediaCostDto> getCostByIppMediaName(Printer printer) {

        final Map<String, IppMediaCostDto> map = new HashMap<>();

        if (printer.getAttributes() != null) {

            Iterator<PrinterAttr> iterAttr = printer.getAttributes().iterator();

            while (iterAttr.hasNext()) {

                final PrinterAttr attr = iterAttr.next();

                final String key = attr.getName();

                if (!key.startsWith(PrinterDao.CostMediaAttr.COST_MEDIA_PFX)) {
                    continue;
                }

                final PrinterDao.CostMediaAttr costMediaAttr =
                        PrinterDao.CostMediaAttr.createFromDbKey(key);

                /*
                 * Self-correcting action...
                 */
                if (costMediaAttr == null) {

                    // (1)
                    printerAttrDAO().delete(attr);
                    // (2)
                    iterAttr.remove();

                    //
                    if (LOGGER.isWarnEnabled()) {
                        LOGGER.warn("Auto-correct: removed invalid attribute ["
                                + key + "] " + "from printer ["
                                + printer.getPrinterName() + "]");
                    }
                    continue;
                }

                final String ippMediaName = costMediaAttr.getIppMediaName();

                if (ippMediaName != null) {

                    IppMediaCostDto dto = new IppMediaCostDto();
                    dto.setMedia(ippMediaName);
                    dto.setActive(Boolean.TRUE);
                    try {
                        dto.setPageCost(JsonAbstractBase.create(
                                MediaCostDto.class, attr.getValue()));
                        map.put(ippMediaName, dto);
                    } catch (SpException e) {
                        LOGGER.error(e.getMessage());
                    }

                } else {
                    LOGGER.error("Printer [" + printer.getPrinterName()
                            + "] : no IPP media name found in key [" + key
                            + "]");
                }
            }
        }

        return map;
    }

    /**
     * Gets the media costs of a Printer from the database.
     *
     * @param printer
     *            The Printer.
     * @return
     */
    private Map<String, IppMediaSourceCostDto> getIppMediaSources(
            Printer printer) {

        final Map<String, IppMediaSourceCostDto> map = new HashMap<>();

        if (printer.getAttributes() != null) {

            Iterator<PrinterAttr> iterAttr = printer.getAttributes().iterator();

            while (iterAttr.hasNext()) {

                final PrinterAttr attr = iterAttr.next();

                final String key = attr.getName();

                if (!key.startsWith(PrinterDao.MediaSourceAttr.MEDIA_SOURCE_PFX)) {
                    continue;
                }

                final PrinterDao.MediaSourceAttr mediaSourceAttr =
                        PrinterDao.MediaSourceAttr.createFromDbKey(key);

                /*
                 * Self-correcting action...
                 */
                if (mediaSourceAttr == null) {

                    // (1)
                    printerAttrDAO().delete(attr);
                    // (2)
                    iterAttr.remove();

                    //
                    if (LOGGER.isWarnEnabled()) {
                        LOGGER.warn("Auto-correct: removed invalid attribute ["
                                + key + "] " + "from printer ["
                                + printer.getPrinterName() + "]");
                    }
                    continue;
                }

                final String ippMediaSourceName =
                        mediaSourceAttr.getIppMediaSourceName();

                if (ippMediaSourceName != null) {

                    final IppMediaSourceCostDto dto;

                    try {
                        dto =
                                JsonAbstractBase.create(
                                        IppMediaSourceCostDto.class,
                                        attr.getValue());

                        map.put(dto.getSource(), dto);

                    } catch (SpException e) {
                        LOGGER.error(e.getMessage());
                    }

                } else {
                    LOGGER.error("Printer [" + printer.getPrinterName()
                            + "] : no IPP media source found in key [" + key
                            + "]");
                }
            }
        }

        return map;
    }

    @Override
    public List<IppMediaCostDto>
            getProxyPrinterCostMedia(final Printer printer) {

        final List<IppMediaCostDto> list = new ArrayList<>();

        /*
         *
         */
        final Map<String, IppMediaCostDto> databaseMediaCost =
                getCostByIppMediaName(printer);

        /*
         * Lazy create "default" media.
         */
        IppMediaCostDto dto =
                databaseMediaCost.get(IppMediaCostDto.DEFAULT_MEDIA);

        if (dto == null) {

            dto = new IppMediaCostDto();

            dto.setMedia(IppMediaCostDto.DEFAULT_MEDIA);
            dto.setActive(Boolean.TRUE);

            MediaCostDto pageCost = new MediaCostDto();
            dto.setPageCost(pageCost);

            MediaPageCostDto cost = new MediaPageCostDto();
            pageCost.setCostOneSided(cost);
            cost.setCostGrayscale("0.0");
            cost.setCostColor("0.0");

            cost = new MediaPageCostDto();
            pageCost.setCostTwoSided(cost);
            cost.setCostGrayscale("0.0");
            cost.setCostColor("0.0");

        }

        list.add(dto);

        /*
         * The "regular" media choices from the printer.
         */
        for (JsonProxyPrinterOptChoice media : getMediaChoices(printer
                .getPrinterName())) {

            dto = databaseMediaCost.get(media.getChoice());

            if (dto == null) {

                dto = new IppMediaCostDto();

                dto.setMedia(media.getChoice());
                dto.setActive(Boolean.FALSE);

                MediaCostDto pageCost = new MediaCostDto();
                dto.setPageCost(pageCost);

                MediaPageCostDto cost = new MediaPageCostDto();
                pageCost.setCostOneSided(cost);
                cost.setCostGrayscale("0.0");
                cost.setCostColor("0.0");

                cost = new MediaPageCostDto();
                pageCost.setCostTwoSided(cost);
                cost.setCostGrayscale("0.0");
                cost.setCostColor("0.0");
            }

            list.add(dto);
        }

        return list;
    }

    @Override
    public List<IppMediaSourceCostDto> getProxyPrinterCostMediaSource(
            Printer printer) {

        final List<IppMediaSourceCostDto> list = new ArrayList<>();

        /*
         *
         */
        final Map<String, IppMediaSourceCostDto> databaseMediaSources =
                getIppMediaSources(printer);

        /*
         * The media-source choices from the printer.
         */
        for (final JsonProxyPrinterOptChoice mediaSource : getMediaSourceChoices(printer
                .getPrinterName())) {

            final String choice = mediaSource.getChoice();

            IppMediaSourceCostDto dto = databaseMediaSources.get(choice);

            if (dto == null) {

                dto = new IppMediaSourceCostDto();

                dto.setActive(Boolean.FALSE);
                dto.setDisplay(mediaSource.getText());
                dto.setSource(mediaSource.getChoice());

                if (!choice.equals(IppKeyword.MEDIA_SOURCE_AUTO)
                        && !choice.equals(IppKeyword.MEDIA_SOURCE_MANUAL)) {

                    IppMediaSourceCostDto dtoMedia =
                            new IppMediaSourceCostDto();
                    dto = dtoMedia;

                    dto.setActive(Boolean.FALSE);
                    dto.setSource(mediaSource.getChoice());

                    final IppMediaCostDto mediaCost = new IppMediaCostDto();
                    dtoMedia.setMedia(mediaCost);

                    mediaCost.setActive(Boolean.FALSE);
                    mediaCost.setMedia(""); // blank

                    final MediaCostDto pageCost = new MediaCostDto();
                    mediaCost.setPageCost(pageCost);

                    MediaPageCostDto cost;

                    cost = new MediaPageCostDto();
                    pageCost.setCostOneSided(cost);
                    cost.setCostGrayscale("0.0");
                    cost.setCostColor("0.0");

                    cost = new MediaPageCostDto();
                    pageCost.setCostTwoSided(cost);
                    cost.setCostGrayscale("0.0");
                    cost.setCostColor("0.0");
                }
            }

            list.add(dto);
        }

        return list;
    }

    /**
     *
     * @param printer
     * @param defaultCost
     * @return
     * @throws ParseException
     */
    private AbstractJsonRpcMethodResponse setPrinterSimpleCost(Printer printer,
            String defaultCost, final Locale locale) {

        try {
            printer.setDefaultCost(BigDecimalUtil.parse(defaultCost, locale,
                    false, false));

        } catch (ParseException e) {

            return JsonRpcMethodError.createBasicError(
                    Code.INVALID_PARAMS,
                    "",
                    localize(ServiceContext.getLocale(),
                            "msg-printer-cost-error", defaultCost));
        }

        printer.setChargeType(Printer.ChargeType.SIMPLE.toString());

        printer.setModifiedDate(ServiceContext.getTransactionDate());
        printer.setModifiedBy(ServiceContext.getActor());

        printerDAO().update(printer);

        updateCachedPrinter(printer);

        return JsonRpcMethodResult.createOkResult();
    }

    /**
     *
     * @param printer
     * @param dtoList
     * @param locale
     *            The Locale of the cost in the dtoList.
     * @return
     */
    private AbstractJsonRpcMethodResponse setPrinterMediaCost(Printer printer,
            List<IppMediaCostDto> dtoList, final Locale locale) {

        /*
         * Put into map for easy lookup of objects to handle. Validate along the
         * way.
         *
         * NOTE: processed entries are removed from the map later on.
         */
        final Map<String, IppMediaCostDto> mapCost = new HashMap<>();

        for (final IppMediaCostDto dto : dtoList) {

            final String mediaKey = dto.getMedia();

            if (!dto.isDefault() && IppMediaSizeEnum.find(mediaKey) == null) {

                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Media [" + mediaKey + "] is invalid.");
                }
                continue;
            }

            /*
             * Validate active entries only.
             */

            if (dto.getActive()) {

                final MediaPageCostDto dtoCostOne =
                        dto.getPageCost().getCostOneSided();

                final MediaPageCostDto dtoCostTwo =
                        dto.getPageCost().getCostTwoSided();

                for (final String cost : new String[] {
                        dtoCostOne.getCostColor(),
                        dtoCostOne.getCostGrayscale(),
                        dtoCostTwo.getCostColor(),
                        dtoCostTwo.getCostGrayscale() }) {

                    if (!BigDecimalUtil.isValid(cost, locale, false)) {
                        return JsonRpcMethodError.createBasicError(
                                Code.INVALID_PARAMS,
                                "",
                                localize(ServiceContext.getLocale(),
                                        "msg-printer-cost-error", cost));
                    }
                }
            }

            mapCost.put(mediaKey, dto);
        }

        /*
         * Lazy create of attribute list.
         */
        if (printer.getAttributes() == null) {
            printer.setAttributes(new ArrayList<PrinterAttr>());
        }

        /*
         * Look for existing PrinterAttr objects to update or delete.
         */
        final Iterator<PrinterAttr> iterAttr =
                printer.getAttributes().iterator();

        while (iterAttr.hasNext()) {

            final PrinterAttr costAttr = iterAttr.next();

            final PrinterDao.CostMediaAttr costMediaAttr =
                    PrinterDao.CostMediaAttr
                            .createFromDbKey(costAttr.getName());

            if (costMediaAttr == null) {
                continue;
            }

            final String mediaKey = costMediaAttr.getIppMediaName();
            final IppMediaCostDto costDto = mapCost.get(mediaKey);

            if (costDto != null && costDto.getActive()) {
                /*
                 * Update active entry.
                 */
                String json;

                try {
                    json = costDto.getPageCost().stringify(locale);
                } catch (ParseException | IOException e) {
                    throw new SpException(e);
                }

                costAttr.setValue(json);

            } else {
                /*
                 * Remove non-active entry.
                 */
                // (1)
                printerAttrDAO().delete(costAttr);
                // (2)
                iterAttr.remove();
            }

            /*
             * Handled, so remove from the map.
             */
            mapCost.remove(mediaKey);
        }

        /*
         * Add the active entries which are left in the map.
         */
        for (final IppMediaCostDto costDto : mapCost.values()) {

            if (costDto.getActive()) {
                /*
                 * Add active entry.
                 */
                final PrinterAttr costAttr = new PrinterAttr();

                costAttr.setPrinter(printer);
                costAttr.setName(new PrinterDao.CostMediaAttr(costDto
                        .getMedia()).getKey());

                String json;

                try {
                    json = costDto.getPageCost().stringify(locale);
                } catch (ParseException | IOException e) {
                    throw new SpException(e);
                }

                costAttr.setValue(json);

                // (1)
                printerAttrDAO().create(costAttr);
                // (2)
                printer.getAttributes().add(costAttr);

            } else {
                /*
                 * Non-active entry + no attribute found earlier on: no code
                 * intended.
                 */
            }

        }

        /*
         * Update the printer.
         */
        printer.setChargeType(Printer.ChargeType.MEDIA.toString());

        printer.setModifiedDate(ServiceContext.getTransactionDate());
        printer.setModifiedBy(ServiceContext.getActor());

        printerDAO().update(printer);

        updateCachedPrinter(printer);

        /*
         * We are ok.
         */
        return JsonRpcMethodResult.createOkResult();
    }

    @Override
    public AbstractJsonRpcMethodResponse setProxyPrinterCostMedia(
            final Printer printer, final ProxyPrinterCostDto dto) {

        final Locale locale =
                new Locale.Builder().setLanguageTag(dto.getLanguage())
                        .setRegion(dto.getCountry()).build();

        if (dto.getChargeType() == Printer.ChargeType.SIMPLE) {
            return setPrinterSimpleCost(printer, dto.getDefaultCost(), locale);
        } else {
            return setPrinterMediaCost(printer, dto.getMediaCost(), locale);
        }
    }

    @Override
    public AbstractJsonRpcMethodResponse setProxyPrinterCostMediaSources(
            final Printer printer,
            final ProxyPrinterMediaSourcesDto dtoMediaSources) {

        final Locale locale =
                new Locale.Builder()
                        .setLanguageTag(dtoMediaSources.getLanguage())
                        .setRegion(dtoMediaSources.getCountry()).build();

        /*
         * Put into map for easy lookup of objects to handle. Validate along the
         * way.
         *
         * NOTE: processed entries are removed from the map later on.
         */
        final Map<String, IppMediaSourceCostDto> mapMediaSources =
                new HashMap<>();

        for (final IppMediaSourceCostDto dto : dtoMediaSources.getSources()) {

            final String mediaSourceKey = dto.getSource();

            /*
             * Validate active entries only.
             */
            if (dto.getActive()) {

                final IppMediaCostDto dtoMediaCost = dto.getMedia();

                if (IppMediaSizeEnum.find(dtoMediaCost.getMedia()) == null) {
                    return JsonRpcMethodError.createBasicError(
                            Code.INVALID_PARAMS,
                            "",
                            localize(ServiceContext.getLocale(),
                                    "msg-printer-media-error",
                                    dtoMediaCost.getMedia(), dto.getDisplay()));
                }

                final MediaPageCostDto dtoCostOne =
                        dtoMediaCost.getPageCost().getCostOneSided();

                final MediaPageCostDto dtoCostTwo =
                        dtoMediaCost.getPageCost().getCostTwoSided();

                for (final String cost : new String[] {
                        dtoCostOne.getCostColor(),
                        dtoCostOne.getCostGrayscale(),
                        dtoCostTwo.getCostColor(),
                        dtoCostTwo.getCostGrayscale() }) {

                    if (!BigDecimalUtil.isValid(cost, locale, false)) {
                        return JsonRpcMethodError.createBasicError(
                                Code.INVALID_PARAMS,
                                "",
                                localize(ServiceContext.getLocale(),
                                        "msg-printer-cost-error", cost));
                    }
                }
            }

            mapMediaSources.put(mediaSourceKey, dto);
        }

        /*
         * Add Auto, Manual to the map
         */
        for (final IppMediaSourceCostDto miscSource : new IppMediaSourceCostDto[] {
                dtoMediaSources.getSourceAuto(),
                dtoMediaSources.getSourceManual() }) {

            if (miscSource != null) {
                mapMediaSources.put(miscSource.getSource(), miscSource);
            }
        }

        /*
         * Lazy create of attribute list.
         */
        if (printer.getAttributes() == null) {
            printer.setAttributes(new ArrayList<PrinterAttr>());
        }

        /*
        *
        */
        final Boolean isForceDefaultMonochrome =
                dtoMediaSources.getDefaultMonochrome();

        String attrPrintColorModeDefault =
                PrinterDao.IppKeywordAttr
                        .getKey(IppDictJobTemplateAttr.ATTR_PRINT_COLOR_MODE_DFLT);

        /*
         * Look for existing PrinterAttr objects to update or delete.
         */
        final Iterator<PrinterAttr> iterAttr =
                printer.getAttributes().iterator();

        Boolean clientSideMonochrome =
                dtoMediaSources.getClientSideMonochrome();

        while (iterAttr.hasNext()) {

            final PrinterAttr printerAttr = iterAttr.next();

            /*
             * Client-side grayscale conversion?
             */
            if (printerAttr.getName().equalsIgnoreCase(
                    PrinterAttrEnum.CLIENT_SIDE_MONOCHROME.getDbName())) {

                if (clientSideMonochrome != null
                        && clientSideMonochrome.booleanValue()) {

                    printerAttr.setValue(clientSideMonochrome.toString());
                    clientSideMonochrome = null;

                } else {
                    /*
                     * Remove non-active entry.
                     */
                    // (1)
                    printerAttrDAO().delete(printerAttr);
                    // (2)
                    iterAttr.remove();
                }
                continue;
            }

            /*
             * IppKeywordAttr: force monochrome default (update/delete).
             */
            if (attrPrintColorModeDefault != null
                    && attrPrintColorModeDefault.equalsIgnoreCase(printerAttr
                            .getName())) {

                if (isForceDefaultMonochrome != null
                        && isForceDefaultMonochrome) {

                    printerAttr
                            .setValue(IppKeyword.PRINT_COLOR_MODE_MONOCHROME);

                } else {
                    /*
                     * Remove non-active entry.
                     */
                    // (1)
                    printerAttrDAO().delete(printerAttr);
                    // (2)
                    iterAttr.remove();
                }

                /*
                 * This is a one-shot setting: prevent handling again by setting
                 * to null.
                 */
                attrPrintColorModeDefault = null;
                continue;
            }

            /*
             * MediaSourceAttr
             */
            final PrinterDao.MediaSourceAttr mediaSourceAttr =
                    PrinterDao.MediaSourceAttr.createFromDbKey(printerAttr
                            .getName());

            if (mediaSourceAttr == null) {
                continue;
            }

            final String mediaSourceKey =
                    mediaSourceAttr.getIppMediaSourceName();

            final IppMediaSourceCostDto mediaSourceDto =
                    mapMediaSources.get(mediaSourceKey);

            if (mediaSourceDto != null && mediaSourceDto.getActive()) {
                /*
                 * Update active entry.
                 */
                String json;

                try {
                    mediaSourceDto.toDatabaseObject(locale);
                    json = mediaSourceDto.stringify();
                } catch (IOException e) {
                    throw new SpException(e);
                }

                printerAttr.setValue(json);

            } else {
                /*
                 * Remove non-active entry.
                 */
                // (1)
                printerAttrDAO().delete(printerAttr);
                // (2)
                iterAttr.remove();
            }

            /*
             * Handled, so remove from the map.
             */
            mapMediaSources.remove(mediaSourceKey);
        }

        /*
         * Add the active entries which are left in the map.
         */
        for (final IppMediaSourceCostDto mediaSourceDto : mapMediaSources
                .values()) {

            if (mediaSourceDto.getActive()) {
                /*
                 * Add active entry.
                 */
                final PrinterAttr printerAttr = new PrinterAttr();

                printerAttr.setPrinter(printer);

                printerAttr.setName(new PrinterDao.MediaSourceAttr(
                        mediaSourceDto.getSource()).getKey());

                String json;

                try {
                    mediaSourceDto.toDatabaseObject(locale);
                    json = mediaSourceDto.stringify();
                } catch (IOException e) {
                    throw new SpException(e);
                }

                printerAttr.setValue(json);

                // (1)
                printerAttrDAO().create(printerAttr);
                // (2)
                printer.getAttributes().add(printerAttr);

            } else {
                /*
                 * Non-active entry + no attribute found earlier on: no code
                 * intended.
                 */
            }
        }

        /*
         * IppKeywordAttr: force monochrome default (add).
         */
        if (attrPrintColorModeDefault != null
                && isForceDefaultMonochrome != null && isForceDefaultMonochrome) {

            final PrinterAttr printerAttr = new PrinterAttr();

            printerAttr.setPrinter(printer);
            printerAttr
                    .setName(new PrinterDao.IppKeywordAttr(
                            IppDictJobTemplateAttr.ATTR_PRINT_COLOR_MODE_DFLT)
                            .getKey());
            printerAttr.setValue(IppKeyword.PRINT_COLOR_MODE_MONOCHROME);

            // (1)
            printerAttrDAO().create(printerAttr);
            // (2)
            printer.getAttributes().add(printerAttr);
        }

        /*
         * Client-side grayscale conversion (add).
         */
        if (clientSideMonochrome != null && clientSideMonochrome.booleanValue()) {
            final PrinterAttr printerAttr = new PrinterAttr();

            printerAttr.setPrinter(printer);
            printerAttr.setName(PrinterAttrEnum.CLIENT_SIDE_MONOCHROME
                    .getDbName());
            printerAttr.setValue(clientSideMonochrome.toString());

            // (1)
            printerAttrDAO().create(printerAttr);
            // (2)
            printer.getAttributes().add(printerAttr);
        }

        /*
         * Update the printer.
         */
        printer.setModifiedDate(ServiceContext.getTransactionDate());
        printer.setModifiedBy(ServiceContext.getActor());

        printerDAO().update(printer);

        updateCachedPrinter(printer);

        /*
         * We are OK.
         */
        return JsonRpcMethodResult.createOkResult();
    }

    /**
     * Gets the CUPS URL for a printer.
     *
     * @param path
     *            The path.
     * @return The URL.
     */
    private URL getCupsUrl(final String path) {

        final URL url;

        try {
            url =
                    new URL("https", ConfigManager.getServerHostAddress(),
                            Integer.parseInt(ConfigManager.getCupsPort()), path);

        } catch (MalformedURLException | UnknownHostException e) {
            throw new SpException(e.getMessage(), e);
        }

        return url;
    }

    @Override
    public URL getCupsPrinterUrl(final String printerName) {
        return getCupsUrl("/printers/" + printerName);
    }

    @Override
    public URL getCupsAdminUrl() {
        return getCupsUrl("/admin");
    }

}
