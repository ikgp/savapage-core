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
package org.savapage.core.print.proxy;

import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;
import org.savapage.core.ipp.attribute.IppDictJobTemplateAttr;
import org.savapage.core.ipp.attribute.syntax.IppKeyword;
import org.savapage.core.ipp.rules.IppRuleConstraint;
import org.savapage.core.ipp.rules.IppRuleCost;
import org.savapage.core.ipp.rules.IppRuleExtra;
import org.savapage.core.ipp.rules.IppRuleNumberUp;
import org.savapage.core.ipp.rules.IppRuleSubst;
import org.savapage.core.jpa.Printer;
import org.savapage.core.json.JsonAbstractBase;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Printer returned from the IPP operation.
 *
 * @author Rijk Ravestein
 *
 */
public final class JsonProxyPrinter extends JsonAbstractBase {

    /** */
    @JsonIgnore
    private URI deviceUri;

    /** */
    @JsonIgnore
    private URI printerUri;

    /**
     * If this is a CUPS printer class, the number of printer members. If
     * {@code 0} (zero) this is <i>not</i> a printer class.
     */
    @JsonIgnore
    private int cupsClassMembers;

    /**
     * Corresponding Database Printer Object.
     */
    @JsonIgnore
    private Printer dbPrinter;

    /**
     * Custom cost rules for a printed media side.
     */
    @JsonIgnore
    private List<IppRuleCost> customCostRulesMedia;

    /**
     * Custom cost rules for a printed sheet.
     */
    @JsonIgnore
    private List<IppRuleCost> customCostRulesSheet;

    /**
     * Custom rules for a handling number-up printing.
     */
    @JsonIgnore
    private List<IppRuleNumberUp> customNumberUpRules;

    /**
     * Constraint Rules.
     */
    @JsonIgnore
    private List<IppRuleConstraint> customRulesConstraint;

    /**
     * Extra Rules for adding PPD options.
     */
    @JsonIgnore
    private List<IppRuleExtra> customRulesExtra;

    /**
     * Extra Rules for substituting PPD option.
     */
    @JsonIgnore
    private List<IppRuleSubst> customRulesSubst;

    /**
     * Custom cost rules for a printed copy.
     */
    @JsonIgnore
    private List<IppRuleCost> customCostRulesCopy;

    /**
     * Custom cost rules for a printed set.
     */
    @JsonIgnore
    private List<IppRuleCost> customCostRulesSet;

    /**
     * If {@code true}, PDF landscape sheets are +90 rotated to portrait print
     * area, so user can -90 rotate the printed sheet to view in landscape. If
     * {@code false} (default), vice versa.
     * <p>
     * This corresponds to PPD attribute "LandscapeOrientation" (default
     * "Plus90");
     * </p>
     */
    @JsonIgnore
    private boolean ppdLandscapeMinus90;

    @JsonIgnore
    private boolean bookletClientSide;

    @JsonIgnore
    boolean archiveDisabled;

    @JsonIgnore
    boolean journalDisabled;

    @JsonIgnore
    boolean ppdPresent;

    /**
     *
     */
    public enum State {
        IDLE, BUSY, STOPPED
    };

    private String name;

    @JsonProperty("pcfilename")
    private String ppd;

    @JsonProperty("FileVersion")
    private String ppdVersion;

    @JsonProperty("is-accepting-jobs")
    private Boolean acceptingJobs;

    private String state;

    @JsonProperty("state-change-time")
    private String stateChangeTime;

    @JsonProperty("state-reasons")
    private String stateReasons;

    private String location;
    private String info;

    @JsonProperty("color_device")
    private Boolean colorDevice;

    @JsonProperty("duplex_device")
    private Boolean duplexDevice;

    @JsonProperty("manualMediaSource")
    private Boolean manualMediaSource;

    @JsonProperty("autoMediaSource")
    private Boolean autoMediaSource;

    /**
     * {@code true} when printer supports
     * {@link IppKeyword#SHEET_COLLATE_COLLATED}.
     */
    @JsonProperty("sheetCollated")
    private Boolean sheetCollated;

    /**
     * {@code true} when printer supports
     * {@link IppKeyword#SHEET_COLLATE_UNCOLLATED}.
     */
    @JsonProperty("sheetUncollated")
    private Boolean sheetUncollated;

    @JsonProperty("modelname")
    private String modelName;

    private String manufacturer;

    private ArrayList<JsonProxyPrinterOptGroup> groups;

    /**
     * {@code true} when info was injected from a SavaPage PPD extension.
     */
    @JsonIgnore
    private boolean injectPpdExt = false;

    /**
     * {@code true} when this is a job ticket printer.
     */
    @JsonIgnore
    private Boolean jobTicket = Boolean.FALSE;

    /**
     * {@code true} when Job Ticket Tags are enabled.
     */
    @JsonIgnore
    private Boolean jobTicketLabelsEnabled = Boolean.FALSE;

    /**
     * Gets the corresponding Database Printer Object.
     *
     * @return The db printer object.
     */
    @JsonIgnore
    public final Printer getDbPrinter() {
        return dbPrinter;
    }

    /**
     * Sets the corresponding Database Printer Object.
     *
     * @param printer
     *            The db printer object.
     */
    @JsonIgnore
    public final void setDbPrinter(final Printer printer) {
        this.dbPrinter = printer;
    }

    /**
     * @return Custom cost rules for a printed media side.
     */
    @JsonIgnore
    public List<IppRuleCost> getCustomCostRulesMedia() {
        return customCostRulesMedia;
    }

    /**
     * @param rules
     *            Custom cost rules for a printed media side.
     */
    @JsonIgnore
    public void setCustomCostRulesMedia(final List<IppRuleCost> rules) {
        this.customCostRulesMedia = rules;
    }

    /**
     * @return Custom cost rules for a printed sheet.
     */
    public List<IppRuleCost> getCustomCostRulesSheet() {
        return customCostRulesSheet;
    }

    /**
     * @param rules
     *            Custom cost rules for a printed sheet.
     */
    public void
            setCustomCostRulesSheet(List<IppRuleCost> customCostRulesSheet) {
        this.customCostRulesSheet = customCostRulesSheet;
    }

    /**
     * @return Custom cost rules for a printed copy.
     */
    @JsonIgnore
    public List<IppRuleCost> getCustomCostRulesCopy() {
        return customCostRulesCopy;
    }

    /**
     * @param rules
     *            Custom cost rules for a printed copy.
     */
    @JsonIgnore
    public void setCustomCostRulesCopy(final List<IppRuleCost> rules) {
        this.customCostRulesCopy = rules;
    }

    /**
     * @return Custom cost rules for a printed set.
     */
    @JsonIgnore
    public List<IppRuleCost> getCustomCostRulesSet() {
        return customCostRulesSet;
    }

    /**
     * @param rules
     *            Custom cost rules for a printed set.
     */
    @JsonIgnore
    public void setCustomCostRulesSet(final List<IppRuleCost> rules) {
        this.customCostRulesSet = rules;
    }

    /**
     * @return Custom rules for a handling number-up printing.
     */
    @JsonIgnore
    public List<IppRuleNumberUp> getCustomNumberUpRules() {
        return customNumberUpRules;
    }

    /**
     * @param rules
     *            Custom rules for a handling number-up printing.
     */
    @JsonIgnore
    public void setCustomNumberUpRules(final List<IppRuleNumberUp> rules) {
        this.customNumberUpRules = rules;
    }

    /**
     * @return Constraint rules.
     */
    @JsonIgnore
    public List<IppRuleConstraint> getCustomRulesConstraint() {
        return customRulesConstraint;
    }

    /**
     * @param customRulesConstraint
     *            Constraint rules.
     */
    @JsonIgnore
    public void setCustomRulesConstraint(
            List<IppRuleConstraint> customRulesConstraint) {
        this.customRulesConstraint = customRulesConstraint;
    }

    /**
     * @return Extra Rules for adding PPD options
     */
    @JsonIgnore
    public List<IppRuleExtra> getCustomRulesExtra() {
        return customRulesExtra;
    }

    /**
     * @param rules
     *            Extra Rules for adding PPD options
     */
    @JsonIgnore
    public void setCustomRulesExtra(final List<IppRuleExtra> rules) {
        this.customRulesExtra = rules;
    }

    /**
     * @return Extra Rules for substituting PPD option.
     */
    @JsonIgnore
    public List<IppRuleSubst> getCustomRulesSubst() {
        return customRulesSubst;
    }

    /**
     * @param rules
     *            Extra Rules for substituting PPD option.
     */
    @JsonIgnore
    public void setCustomRulesSubst(final List<IppRuleSubst> rules) {
        this.customRulesSubst = rules;
    }

    /**
     * Removes PPDE injection artifacts.
     */
    @JsonIgnore
    public void removeInjectPpdExt() {
        this.setInjectPpdExt(false);
        this.setCustomCostRulesCopy(null);
        this.setCustomCostRulesMedia(null);
        this.setCustomCostRulesSet(null);
        this.setCustomCostRulesSheet(null);
        this.setCustomNumberUpRules(null);
        this.setCustomRulesConstraint(null);
        this.setCustomRulesExtra(null);
        this.setCustomRulesSubst(null);
    }

    /**
     *
     * @return The {@link State}.
     */
    public State state() {
        if (state.equals("3")) {
            return State.IDLE;
        } else if (state.equals("4")) {
            return State.BUSY;
        } else if (state.equals("5")) {
            return State.STOPPED;
        }
        return State.STOPPED;
    }

    /**
     *
     * @return The UPPERCASE name of the printer.
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name of the printer.
     * <p>
     * IMPORTANT: the name MUST be UPPERCASE.
     * </p>
     *
     * @param name
     *            The UPPERCASE name.
     */
    public void setName(String name) {
        this.name = name;
    }

    public String getPpd() {
        return ppd;
    }

    public void setPpd(String ppd) {
        this.ppd = ppd;
    }

    public String getPpdVersion() {
        return ppdVersion;
    }

    public void setPpdVersion(String ppdVersion) {
        this.ppdVersion = ppdVersion;
    }

    /**
     * @return If {@code true}, PDF landscape sheets are +90 rotated to portrait
     *         print area, so user can -90 rotate the printed sheet to view in
     *         landscape. If {@code false} (default), vice versa.
     */
    @JsonIgnore
    public boolean isPpdLandscapeMinus90() {
        return this.ppdLandscapeMinus90;
    }

    /**
     * @param minus90
     *            If {@code true}, PDF landscape sheets are +90 rotated to
     *            portrait print area, so user can -90 rotate the printed sheet
     *            to view in landscape. If {@code false} (default), vice versa.
     */
    @JsonIgnore
    public void setPpdLandscapeMinus90(final boolean minus90) {
        this.ppdLandscapeMinus90 = minus90;
    }

    @JsonIgnore
    public boolean isBookletClientSide() {
        return bookletClientSide;
    }

    @JsonIgnore
    public void setBookletClientSide(boolean bookletClientSide) {
        this.bookletClientSide = bookletClientSide;
    }

    @JsonIgnore
    public boolean isArchiveDisabled() {
        return archiveDisabled;
    }

    @JsonIgnore
    public void setArchiveDisabled(boolean archiveDisabled) {
        this.archiveDisabled = archiveDisabled;
    }

    @JsonIgnore
    public boolean isJournalDisabled() {
        return journalDisabled;
    }

    @JsonIgnore
    public void setJournalDisabled(boolean journalDisabled) {
        this.journalDisabled = journalDisabled;
    }

    /**
     * @return {@code true} if PPD file is present in CUPS.
     */
    public boolean isPpdPresent() {
        return ppdPresent;
    }

    /**
     * @param ppdPresent
     *            {@code true} if PPD file is present in CUPS.
     */
    public void setPpdPresent(boolean ppdPresent) {
        this.ppdPresent = ppdPresent;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getStateChangeTime() {
        return stateChangeTime;
    }

    public void setStateChangeTime(String stateChangeTime) {
        this.stateChangeTime = stateChangeTime;
    }

    public String getStateReasons() {
        return stateReasons;
    }

    public void setStateReasons(String stateReasons) {
        this.stateReasons = stateReasons;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getInfo() {
        return info;
    }

    public void setInfo(String info) {
        this.info = info;
    }

    public Boolean getColorDevice() {
        return colorDevice;
    }

    public void setColorDevice(Boolean colorDevice) {
        this.colorDevice = colorDevice;
    }

    public Boolean getDuplexDevice() {
        return duplexDevice;
    }

    public void setDuplexDevice(Boolean duplexDevice) {
        this.duplexDevice = duplexDevice;
    }

    public Boolean getManualMediaSource() {
        return this.manualMediaSource;
    }

    public void setManualMediaSource(Boolean manualMediaSource) {
        this.manualMediaSource = manualMediaSource;
    }

    public Boolean getAutoMediaSource() {
        return autoMediaSource;
    }

    public void setAutoMediaSource(Boolean autoMediaSource) {
        this.autoMediaSource = autoMediaSource;
    }

    /**
     * @return {@code true} when printer supports
     *         {@link IppKeyword#SHEET_COLLATE_COLLATED}.
     */
    public Boolean getSheetCollated() {
        return sheetCollated;
    }

    /**
     * @param sheetCollate
     *            {@code true} when printer supports
     *            {@link IppKeyword#SHEET_COLLATE_COLLATED}.
     */
    public void setSheetCollated(Boolean sheetCollated) {
        this.sheetCollated = sheetCollated;
    }

    /**
     * @return {@code true} when printer supports
     *         {@link IppKeyword#SHEET_COLLATE_UNCOLLATED}.
     */
    public Boolean getSheetUncollated() {
        return sheetUncollated;
    }

    /**
     * @param sheetCollate
     *            {@code true} when printer supports
     *            {@link IppKeyword#SHEET_COLLATE_UNCOLLATED}.
     */
    public void setSheetUncollated(Boolean sheetUncollated) {
        this.sheetUncollated = sheetUncollated;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public void setManufacturer(String manufacturer) {
        this.manufacturer = manufacturer;
    }

    public Boolean getAcceptingJobs() {
        return acceptingJobs;
    }

    public void setAcceptingJobs(Boolean acceptingJobs) {
        this.acceptingJobs = acceptingJobs;
    }

    public ArrayList<JsonProxyPrinterOptGroup> getGroups() {
        return groups;
    }

    public void setGroups(ArrayList<JsonProxyPrinterOptGroup> groups) {
        this.groups = groups;
    }

    /**
     * Flattens the printer options from groups and subgroups into one (1)
     * lookup.
     *
     * @return The lookup {@link Map} with
     *         {@link JsonProxyPrinterOpt#getKeyword()} as key.
     */
    public Map<String, JsonProxyPrinterOpt> getOptionsLookup() {

        final Map<String, JsonProxyPrinterOpt> lookup = new HashMap<>();

        for (final JsonProxyPrinterOptGroup group : groups) {

            for (final JsonProxyPrinterOpt option : group.getOptions()) {
                lookup.put(option.getKeyword(), option);
            }

            for (final JsonProxyPrinterOptGroup subgroup : group
                    .getSubgroups()) {

                for (final JsonProxyPrinterOpt option : subgroup.getOptions()) {
                    lookup.put(option.getKeyword(), option);
                }
            }
        }
        return lookup;
    }

    /**
     * Check if this printer has the same signature as printer in parameter. The
     * signature is the combination of name (ignore case), PPD file and PPD
     * version.
     *
     * @param printer
     *            The printer to compare with.
     * @return {@code true} if printers have the same signature.
     */
    public final boolean hasSameSignature(final JsonProxyPrinter printer) {

        return (hasSameName(printer)
                && printer.getPpd().equals(printer.getPpd())
                && printer.getPpdVersion().equals(printer.getPpdVersion()));

    }

    /**
     * Check if this printer has the same name (ignore case) as printer in
     * parameter.
     *
     * @param printer
     *            The printer to compare with.
     * @return {@code true} if printers have the same name.
     */
    public final boolean hasSameName(final JsonProxyPrinter printer) {
        return (printer.getName().equalsIgnoreCase(printer.getName()));
    }

    public URI getDeviceUri() {
        return deviceUri;
    }

    public void setDeviceUri(URI deviceUri) {
        this.deviceUri = deviceUri;
    }

    @JsonIgnore
    public URI getPrinterUri() {
        return printerUri;
    }

    @JsonIgnore
    public void setPrinterUri(final URI printerUri) {
        this.printerUri = printerUri;
    }

    /**
     * @return If this is a CUPS printer class, the number of printer members.
     *         If {@code 0} (zero) this is <i>not</i> a printer class.
     */
    @JsonIgnore
    public int getCupsClassMembers() {
        return cupsClassMembers;
    }

    /**
     * @param nMembers
     *            If this is a CUPS printer class, the number of printer
     *            members. If {@code 0} (zero) this is <i>not</i> a printer
     *            class.
     */
    @JsonIgnore
    public void setCupsClassMembers(final int nMembers) {
        this.cupsClassMembers = nMembers;
    }

    /**
     * @return {@code true} when info was injected from a SavaPage PPD
     *         extension.
     */
    public boolean isInjectPpdExt() {
        return injectPpdExt;
    }

    /**
     * @param injectPpdExt
     *            {@code true} when info was injected from a SavaPage PPD
     *            extension.
     */
    public void setInjectPpdExt(final boolean injectPpdExt) {
        this.injectPpdExt = injectPpdExt;
    }

    /**
     * @return {@code true} when this is a job ticket printer.
     */
    public Boolean getJobTicket() {
        return jobTicket;
    }

    /**
     * @param ticket
     *            {@code true} when this is a job ticket printer.
     */
    public void setJobTicket(final Boolean ticket) {
        this.jobTicket = ticket;
    }

    /**
     * @return {@code true} when Job Ticket Labels are enabled.
     */
    public Boolean getJobTicketLabelsEnabled() {
        return jobTicketLabelsEnabled;
    }

    /**
     * @param enabled
     *            {@code true} when Job Ticket Labels are enabled.
     */
    public void setJobTicketLabelsEnabled(final Boolean enabled) {
        this.jobTicketLabelsEnabled = enabled;
    }

    /**
     * Creates a deep copy instance.
     *
     * @return The new copy.
     */
    @JsonIgnore
    public JsonProxyPrinter copy() {

        final JsonProxyPrinter copy = new JsonProxyPrinter();

        copy.acceptingJobs = this.acceptingJobs;
        copy.colorDevice = this.colorDevice;
        copy.dbPrinter = this.dbPrinter;
        copy.duplexDevice = this.duplexDevice;
        copy.info = this.info;
        copy.location = this.location;
        copy.manualMediaSource = this.manualMediaSource;
        copy.autoMediaSource = this.autoMediaSource;
        copy.sheetCollated = this.sheetCollated;
        copy.sheetUncollated = this.sheetUncollated;
        copy.manufacturer = this.manufacturer;
        copy.modelName = this.modelName;
        copy.name = this.name;
        copy.ppd = this.ppd;
        copy.ppdPresent = this.ppdPresent;
        copy.injectPpdExt = this.injectPpdExt;
        copy.jobTicket = this.jobTicket;
        copy.jobTicketLabelsEnabled = this.jobTicketLabelsEnabled;
        copy.ppdVersion = this.ppdVersion;
        copy.ppdLandscapeMinus90 = this.ppdLandscapeMinus90;
        copy.printerUri = this.printerUri;
        copy.state = this.state;
        copy.stateChangeTime = this.stateChangeTime;
        copy.stateReasons = this.stateReasons;
        copy.customCostRulesCopy = this.customCostRulesCopy;
        copy.customCostRulesMedia = this.customCostRulesMedia;
        copy.customCostRulesSheet = this.customCostRulesSheet;

        copy.archiveDisabled = this.archiveDisabled;
        copy.journalDisabled = this.journalDisabled;

        copy.customRulesConstraint = this.customRulesConstraint;
        copy.customRulesExtra = this.customRulesExtra;
        copy.customRulesSubst = this.customRulesSubst;

        copy.groups = new ArrayList<>();
        copy.groups.addAll(this.getGroups());

        return copy;
    }

    /**
     * Calculates Media Cost of IPP choices according to the list of cost rules.
     *
     * @param ippChoices
     *            The IPP attribute key/choices.
     * @return {@code null} when none of the rules apply.
     */
    public BigDecimal
            calcCustomCostMedia(final Map<String, String> ippChoices) {
        return calcCost(this.getCustomCostRulesMedia(), ippChoices, false);
    }

    /**
     * Calculates Sheet Cost of IPP choices according to the list of cost rules.
     *
     * @param ippChoices
     *            The IPP attribute key/choices.
     * @return {@code null} when none of the rules apply.
     */
    public BigDecimal
            calcCustomCostSheet(final Map<String, String> ippChoices) {
        return calcCost(this.getCustomCostRulesSheet(), ippChoices, false);
    }

    /**
     * Calculates Copy Cost of IPP choices according to the list of cost rules.
     *
     * @param ippChoices
     *            The IPP attribute key/choices.
     * @return {@code null} when none of the rules apply.
     */
    public BigDecimal calcCustomCostCopy(final Map<String, String> ippChoices) {
        return calcCost(this.getCustomCostRulesCopy(), ippChoices, true);
    }

    /**
     * Calculates Set Cost of IPP choices according to the list of cost rules.
     *
     * @param ippChoices
     *            The IPP attribute key/choices.
     * @return {@code null} when none of the rules apply.
     */
    public BigDecimal calcCustomCostSet(final Map<String, String> ippChoices) {
        return calcCost(this.getCustomCostRulesSet(), ippChoices, true);
    }

    /**
     * Finds a matching {@link IppRuleNumberUp} for a template rule.
     *
     * @param template
     *            The template rule with <i>independent</i> variables.
     * @return The template rule object supplemented with <i>dependent</i>
     *         variables, or {@code null} when no rule found.
     */
    public IppRuleNumberUp findCustomRule(final IppRuleNumberUp template) {
        if (this.customNumberUpRules != null) {
            for (final IppRuleNumberUp wlk : this.customNumberUpRules) {
                if (template.isParameterMatch(wlk)) {
                    template.setDependentVars(wlk);
                    return template;
                }
            }
        }
        return null;
    }

    /**
     * Finds the matching {@link IppRuleExtra} objects for a map of IPP options.
     *
     * @param ippOptionValues
     *            The IPP option map.
     * @return The list of matching (can be empty).
     */
    public List<IppRuleExtra>
            findCustomRulesExtra(final Map<String, String> ippOptionValues) {

        final List<IppRuleExtra> rulesFound = new ArrayList<>();

        if (this.customRulesExtra != null) {

            for (final IppRuleExtra wlk : this.customRulesExtra) {
                if (wlk.doesRuleApply(ippOptionValues)) {
                    rulesFound.add(wlk);
                }
            }
        }
        return rulesFound;
    }

    /**
     * Finds the matching {@link IppRuleSubst} objects for a map of IPP options.
     *
     * @param ippOptionValues
     *            The IPP option map.
     * @return The map of matching rules with key IPP attribute (map can be
     *         empty).
     */
    public Map<String, IppRuleSubst>
            findCustomRulesSubst(final Map<String, String> ippOptionValues) {

        final Map<String, IppRuleSubst> rulesFound = new HashMap<>();

        if (this.customRulesSubst != null) {

            for (final IppRuleSubst wlk : this.customRulesSubst) {
                if (wlk.doesRuleApply(ippOptionValues)) {
                    rulesFound.put(wlk.getMainIpp().getKey(), wlk);
                }
            }
        }
        return rulesFound;
    }

    /**
     * Gets the cover costs according to the list of cost rules.
     *
     * @param ippCoverChoice
     *            The IPP choice of attribute
     *            {@link IppDictJobTemplateAttr#ORG_SAVAPAGE_ATTR_COVER_TYPE}.
     * @return {@code null} when none of the rules apply.
     */
    public BigDecimal getCustomCostCover(final String ippCoverChoice) {

        if (ippCoverChoice == null) {
            return null;
        }

        final Map<String, String> singleIppChoice = new HashMap<>();

        singleIppChoice.put(IppDictJobTemplateAttr.ORG_SAVAPAGE_ATTR_COVER_TYPE,
                ippCoverChoice);

        return calcCost(this.getCustomCostRulesCopy(), singleIppChoice, false);
    }

    /**
     * Checks if an option is valid according to at least one (1) of the Custom
     * Sheet Cost rules.
     *
     * @param option
     *            The IPP option key/value pair.
     * @param ippChoices
     *            The full context of IPP choices.
     * @return {@code null} when no rule applies. {@link Boolean#TRUE} when at
     *         least one rule applies and is valid.
     */
    public Boolean isCustomSheetCostOptionValid(
            final Pair<String, String> option,
            final Map<String, String> ippChoices) {
        return isCustomCostOptionValid(getCustomCostRulesCopy(), option,
                ippChoices);
    }

    /**
     * Checks if an option is valid according to at least one (1) of the Custom
     * Copy Cost rules.
     *
     * @param option
     *            The IPP option key/value pair.
     * @param ippChoices
     *            The full context of IPP choices.
     * @return {@code null} when no rule applies. {@link Boolean#TRUE} when at
     *         least one rule applies and is valid.
     */
    public Boolean isCustomCopyCostOptionValid(
            final Pair<String, String> option,
            final Map<String, String> ippChoices) {
        return isCustomCostOptionValid(getCustomCostRulesCopy(), option,
                ippChoices);
    }

    /**
     * Checks if an option is valid according to at least one (1) of the rules.
     *
     * @param rules
     *            The list of cost rules.
     * @param option
     *            The IPP option key/value pair.
     * @param ippChoices
     *            The full context of IPP choices.
     * @return {@code null} when no rule applies. {@link Boolean#TRUE} when at
     *         least one rule applies and is valid.
     */
    private static Boolean isCustomCostOptionValid(
            final List<IppRuleCost> rules, final Pair<String, String> option,
            final Map<String, String> ippChoices) {

        // Assume no rule found.
        Boolean isValid = null;

        for (final IppRuleCost rule : rules) {

            final Boolean result = rule.isOptionValid(option, ippChoices);

            if (result == null) {
                continue;
            }

            // The first valid rule is OK.
            if (result.booleanValue()) {
                return result;
            }

            // Assume FALSE.
            isValid = result;
        }

        return isValid;
    }

    /**
     * Calculates cost of IPP choices according to a list of cost rules.
     *
     * @param rules
     *            The list of cost rules.
     * @param ippChoices
     *            The IPP attribute key/choices.
     * @param accumulate
     *            If {@code true} all rules are checked, and the cost result of
     *            each rule is accumulated. When {@code false} the cost of the
     *            first rule with a non-null result is returned.
     * @return {@code null} when none of the rules apply.
     */
    private static BigDecimal calcCost(final List<IppRuleCost> rules,
            final Map<String, String> ippChoices, final boolean accumulate) {

        if (rules == null) {
            return null;
        }

        BigDecimal total = null;

        for (final IppRuleCost rule : rules) {

            final BigDecimal cost = rule.calcCost(ippChoices);

            if (cost != null) {
                if (accumulate) {
                    if (total == null) {
                        total = cost;
                    } else {
                        total = total.add(cost);
                    }
                } else {
                    return cost;
                }
            }
        }
        return total;
    }

    /**
     * @return {@code true} when custom Media cost rules are present.
     */
    public boolean hasCustomCostRulesMedia() {
        final List<IppRuleCost> rules = this.getCustomCostRulesMedia();
        return rules != null && !rules.isEmpty();
    }

    /**
     * @return {@code true} when custom Sheet cost rules are present.
     */
    public boolean hasCustomCostRulesSheet() {
        final List<IppRuleCost> rules = this.getCustomCostRulesSheet();
        return rules != null && !rules.isEmpty();
    }

    /**
     * @return {@code true} when custom Set cost rules are present.
     */
    public boolean hasCustomCostRulesSet() {
        final List<IppRuleCost> rules = this.getCustomCostRulesSet();
        return rules != null && !rules.isEmpty();
    }

    /**
     * @return {@code true} when custom Copy cost rules are present.
     */
    public boolean hasCustomCostRulesCopy() {
        final List<IppRuleCost> rules = this.getCustomCostRulesCopy();
        return rules != null && !rules.isEmpty();
    }

    /**
     * @return {@code true} when custom constraint rules are present.
     */
    public boolean hasCustomRulesConstraint() {
        final List<IppRuleConstraint> rules = this.getCustomRulesConstraint();
        return rules != null && !rules.isEmpty();
    }

    /**
     * @return {@code true} if
     *         {@link IppDictJobTemplateAttr#ORG_SAVAPAGE_ATTR_JOB_SHEETS} is
     *         present.
     */
    public boolean hasJobSheets() {
        return this.getOptionsLookup().containsKey(
                IppDictJobTemplateAttr.ORG_SAVAPAGE_ATTR_JOB_SHEETS);
    }

    /**
     * @return {@code true} if
     *         {@link IppDictJobTemplateAttr#ORG_SAVAPAGE_ATTR_JOB_SHEETS_MEDIA}
     *         is present.
     */
    public boolean hasJobSheetsMedia() {
        return this.getOptionsLookup().containsKey(
                IppDictJobTemplateAttr.ORG_SAVAPAGE_ATTR_JOB_SHEETS_MEDIA);
    }
}
