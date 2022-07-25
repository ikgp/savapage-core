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
package org.savapage.core.ipp.helpers;

import java.util.ArrayList;
import java.util.List;

import org.savapage.core.ipp.attribute.syntax.IppKeyword;
import org.savapage.core.ipp.rules.IppRuleNumberUp;
import org.savapage.core.pdf.PdfPageRotateHelper;
import org.savapage.core.services.helpers.PpdExtFileReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @deprecated Use {@link IppNumberUpResolver} instead.
 *
 * @author Rijk Ravestein
 *
 */
@Deprecated
public final class IppNumberUpHelper implements IppNumberUpRuleFinder {

    /** */
    private static final Logger LOGGER =
            LoggerFactory.getLogger(IppNumberUpHelper.class);

    /**
     * Rules for a handling number-up printing.
     */
    private final List<IppRuleNumberUp> numberUpRules;

    /** */
    private static final class SingletonPageRotationHelper {
        /** */
        public static final IppNumberUpHelper INSTANCE =
                new IppNumberUpHelper();
    }

    /** Landscape. */
    private static final String _L_ = "L";
    /** Portrait. */
    private static final String _P_ = "P";

    @SuppressWarnings("unused")
    private static final String C___0 =
            IppKeyword.ORIENTATION_REQUESTED_0_DEGREES;
    @SuppressWarnings("unused")
    private static final String C__90 =
            IppKeyword.ORIENTATION_REQUESTED_90_DEGREES;
    private static final String C_180 =
            IppKeyword.ORIENTATION_REQUESTED_180_DEGREES;
    private static final String C_270 =
            IppKeyword.ORIENTATION_REQUESTED_270_DEGREES;

    private static final String BTLR = IppKeyword.NUMBER_UP_LAYOUT_BTLR;
    @SuppressWarnings("unused")
    private static final String BTRL = IppKeyword.NUMBER_UP_LAYOUT_BTRL;
    @SuppressWarnings("unused")
    private static final String LRBT = IppKeyword.NUMBER_UP_LAYOUT_LRBT;
    private static final String LRTB = IppKeyword.NUMBER_UP_LAYOUT_LRTB;
    @SuppressWarnings("unused")
    private static final String RLBT = IppKeyword.NUMBER_UP_LAYOUT_RLBT;
    private static final String RLTB = IppKeyword.NUMBER_UP_LAYOUT_RLTB;
    private static final String TBLR = IppKeyword.NUMBER_UP_LAYOUT_TBLR;
    private static final String TBRL = IppKeyword.NUMBER_UP_LAYOUT_TBRL;

    private static final String P___0 =
            PdfPageRotateHelper.PDF_ROTATION_0.toString();
    private static final String P__90 =
            PdfPageRotateHelper.PDF_ROTATION_90.toString();
    private static final String P_180 =
            PdfPageRotateHelper.PDF_ROTATION_180.toString();
    private static final String P_270 =
            PdfPageRotateHelper.PDF_ROTATION_270.toString();

    private static final String U__0 = P___0;
    private static final String U_90 = P__90;

    private static final String CTM___0 = P___0;
    private static final String CTM__90 = P__90;
    private static final String CTM_270 = P_270;

    private static final String N_1 = IppKeyword.NUMBER_UP_1;
    private static final String N_2 = IppKeyword.NUMBER_UP_2;
    private static final String N_4 = IppKeyword.NUMBER_UP_4;
    private static final String N_6 = IppKeyword.NUMBER_UP_6;
    private static final String N_9 = IppKeyword.NUMBER_UP_9;
    private static final String N_16 = IppKeyword.NUMBER_UP_16;

    /** Index in {@link #RULES} item for independent variable. */
    private static final int IDX_ORIENTATION = 0;
    /** Index in {@link #RULES} item for independent variable. */
    private static final int IDX_PDF_ROTATION = 1;
    /** Index in {@link #RULES} item for independent variable. */
    private static final int IDX_PDF_CONTENT_ROTATION = 2;
    /** Index in {@link #RULES} item for independent variable. */
    private static final int IDX_USER_ROTATE = 3;
    /** Index in {@link #RULES} item for independent variable. */
    private static final int IDX_N_UP = 4;
    /** Index in {@link #RULES} item for dependent variable. */
    private static final int IDX_CUPS_ORIENTATION = 5;
    /** Index in {@link #RULES} item for dependent variable. */
    private static final int IDX_CUPS_N_UP_LAYOUT = 6;
    /** Index in {@link #RULES} item for dependent variable. */
    private static final int IDX_CUPS_N_UP_ORIENTATION = 7;

    private static final String ____ = null;
    private static final String _____ = null;

    /**
     * Internal number-up rules as tested with CUPS "Generic PostScript
     * Printer".
     * <p>
     * The independent variables hold the values of the first page of the PDF
     * input, and user requested number-up and page rotation, "as is", i.e.
     * these are not the values in the resulting PDF output.
     * </p>
     * <p>
     * Exceptions, like e.g. for Ricoh, are configured as SPRule in PPDE file.
     * See: {@link PpdExtFileReader}.
     * </p>
     */
    private static final String[][] RULES = { //
            /*
             * Portrait.
             */
            { _P_, P___0, CTM___0, U__0, N_1, _____, ____, _P_ }, // OK
            { _P_, P___0, CTM___0, U__0, N_2, _____, LRTB, _L_ }, // OK +LH(_P_)
            { _P_, P___0, CTM___0, U__0, N_4, _____, LRTB, _P_ }, // OK
            { _P_, P___0, CTM___0, U__0, N_6, _____, LRTB, _L_ }, // OK +LH(_P_)

            { _P_, P___0, CTM___0, U_90, N_1, _____, ____, _P_ }, // OK
            { _P_, P___0, CTM___0, U_90, N_2, _____, RLTB, _L_ }, // OK -LH
            { _P_, P___0, CTM___0, U_90, N_4, _____, BTLR, _P_ }, // OK -LH
            { _P_, P___0, CTM___0, U_90, N_6, _____, TBRL, _L_ }, // OK -LH

            { _P_, P__90, CTM___0, U__0, N_1, C_180, ____, _P_ }, // OK -Ricoh
            { _P_, P__90, CTM___0, U__0, N_2, C_180, TBLR, _P_ }, // OK -Ricoh
            { _P_, P__90, CTM___0, U__0, N_4, C_180, BTLR, _P_ }, // OK -Ricoh
            { _P_, P__90, CTM___0, U__0, N_6, C_180, BTLR, _P_ }, // OK -Ricoh

            { _P_, P__90, CTM___0, U_90, N_1, _____, ____ }, //
            { _P_, P__90, CTM___0, U_90, N_2, C_270, TBRL }, //
            { _P_, P__90, CTM___0, U_90, N_4, _____, TBRL }, //
            { _P_, P__90, CTM___0, U_90, N_6, C_270, LRTB }, //

            { _P_, P_180, CTM___0, U__0, N_1, C_180, ____ }, //
            { _P_, P_180, CTM___0, U__0, N_2, C_270, TBRL }, //
            { _P_, P_180, CTM___0, U__0, N_4, C_180, BTLR }, //
            { _P_, P_180, CTM___0, U__0, N_6, C_270, LRTB }, //

            { _P_, P_180, CTM___0, U_90, N_1, _____, ____ }, //
            { _P_, P_180, CTM___0, U_90, N_2, C_180, TBRL }, //
            { _P_, P_180, CTM___0, U_90, N_4, _____, TBRL }, //
            { _P_, P_180, CTM___0, U_90, N_6, C_180, TBRL }, //

            { _P_, P_270, CTM___0, U__0, N_1, _____, ____ }, //
            { _P_, P_270, CTM___0, U__0, N_2, C_180, TBRL }, //
            { _P_, P_270, CTM___0, U__0, N_4, _____, TBRL }, //
            { _P_, P_270, CTM___0, U__0, N_6, C_180, TBRL }, //

            { _P_, P_270, CTM___0, U_90, N_1, _____, ____ }, //
            { _P_, P_270, CTM___0, U_90, N_2, C_180, TBRL }, //
            { _P_, P_270, CTM___0, U_90, N_4, _____, TBRL }, //
            { _P_, P_270, CTM___0, U_90, N_6, C_180, TBRL }, //

            /*
             * Landscape.
             */
            { _L_, P___0, CTM___0, U__0, N_1, C_270, ____, _P_ }, // OK
            { _L_, P___0, CTM___0, U__0, N_2, C_180, TBLR, _P_ }, // OK -Ricoh
            { _L_, P___0, CTM___0, U__0, N_4, C_270, LRTB, _P_ }, // OK
            { _L_, P___0, CTM___0, U__0, N_6, C_180, BTLR, _P_ }, // OK -Ricoh

            { _L_, P___0, CTM___0, U_90, N_1, C_270, ____ }, //
            { _L_, P___0, CTM___0, U_90, N_2, C_270, TBRL }, //
            { _L_, P___0, CTM___0, U_90, N_4, C_270, LRTB }, //
            { _L_, P___0, CTM___0, U_90, N_6, C_270, LRTB }, //

            { _L_, P__90, CTM___0, U__0, N_1, C_270, ____ }, //
            { _L_, P__90, CTM___0, U__0, N_2, C_270, TBRL }, //
            { _L_, P__90, CTM___0, U__0, N_4, C_270, LRTB }, //
            { _L_, P__90, CTM___0, U__0, N_6, C_270, LRTB }, //

            { _L_, P__90, CTM___0, U_90, N_1, _____, ____ }, //
            { _L_, P__90, CTM___0, U_90, N_2, C_180, TBRL }, //
            { _L_, P__90, CTM___0, U_90, N_4, _____, TBRL }, //
            { _L_, P__90, CTM___0, U_90, N_6, C_180, TBRL }, //

            { _L_, P_180, CTM___0, U__0, N_1, _____, ____ }, //
            { _L_, P_180, CTM___0, U__0, N_2, C_180, TBRL }, //
            { _L_, P_180, CTM___0, U__0, N_4, _____, TBRL }, //
            { _L_, P_180, CTM___0, U__0, N_6, C_180, TBRL }, //

            { _L_, P_180, CTM___0, U_90, N_1, _____, ____ }, //
            { _L_, P_180, CTM___0, U_90, N_2, C_180, TBRL }, //
            { _L_, P_180, CTM___0, U_90, N_4, _____, TBRL }, //
            { _L_, P_180, CTM___0, U_90, N_6, C_180, TBRL }, //

            { _L_, P_270, CTM___0, U__0, N_1, _____, ____, _P_ }, // OK
            { _L_, P_270, CTM___0, U__0, N_2, _____, TBLR, _L_ }, // OK
            { _L_, P_270, CTM___0, U__0, N_4, _____, LRTB, _P_ }, // OK
            { _L_, P_270, CTM___0, U__0, N_6, _____, LRTB, _L_ }, // OK

            { _L_, P_270, CTM___0, U_90, N_1, _____, ____, _L_ }, // OK
            { _L_, P_270, CTM___0, U_90, N_2, _____, TBLR, _P_ }, // OK -Ricoh
            { _L_, P_270, CTM___0, U_90, N_4, _____, BTLR, _L_ }, // OK -Ricoh
            { _L_, P_270, CTM___0, U_90, N_6, _____, BTLR, _P_ }, // OK -Ricoh

            // ----------------------------------------------------
            // CTM
            // ----------------------------------------------------
            { _L_, P_270, CTM_270, U__0, N_1, _____, ____, _P_ }, // OK
            { _L_, P_270, CTM_270, U__0, N_2, C_180, LRTB, _P_ }, // OK
            { _L_, P_270, CTM_270, U__0, N_4, _____, LRTB, _L_ }, // OK
            { _L_, P_270, CTM_270, U__0, N_6, C_180, LRTB, _P_ }, // OK -Ricoh

            // EcoPrint
            { _L_, P__90, CTM__90, U_90, N_1, _____, ____, _P_ }, // OK
            { _L_, P__90, CTM__90, U_90, N_2, C_180, LRTB, _P_ }, // OK
            { _L_, P__90, CTM__90, U_90, N_4, C_180, BTLR, _L_ }, // +/-
            { _L_, P__90, CTM__90, U_90, N_6, C_180, BTLR, _L_ }, // +-

    };

    /** */
    private IppNumberUpHelper() {
        this.numberUpRules = createRuleList(RULES);
    }

    /**
     *
     * @param ruleArray
     * @return
     */
    private static List<IppRuleNumberUp>
            createRuleList(final String[][] ruleArray) {

        final List<IppRuleNumberUp> numberUpRules = new ArrayList<>();

        for (final String[] wlk : ruleArray) {
            final IppRuleNumberUp rule = new IppRuleNumberUp("internal");

            rule.setLandscape(wlk[IDX_ORIENTATION].equals(_L_));
            rule.setNumberUp(wlk[IDX_N_UP]);
            rule.setPdfRotation(Integer.parseInt(wlk[IDX_PDF_ROTATION]));
            rule.setPdfContentRotation(
                    Integer.parseInt(wlk[IDX_PDF_CONTENT_ROTATION]));
            rule.setUserRotate(Integer.parseInt(wlk[IDX_USER_ROTATE]));

            rule.setNumberUpLayout(wlk[IDX_CUPS_N_UP_LAYOUT]);
            rule.setOrientationRequested(wlk[IDX_CUPS_ORIENTATION]);

            rule.setLandscapePrint(wlk.length > IDX_CUPS_N_UP_ORIENTATION
                    && wlk[IDX_CUPS_N_UP_ORIENTATION].equals(_L_));

            numberUpRules.add(rule);
        }
        return numberUpRules;
    }

    /**
     *
     * @return The singleton instance.
     */
    public static IppNumberUpHelper instance() {
        return SingletonPageRotationHelper.INSTANCE;
    }

    /**
     * Method for testing number-up rules in DEBUG session.
     * <p>
     * <b>Important:</b> <i>this method MUST NOT contain any test rules in
     * production code.</i>
     * </p>
     *
     * @param template
     *            The template rule with <i>independent</i> variables.
     * @return The template rule object supplemented with <i>dependent</i>
     *         variables, or {@code null} when no rule found.
     */
    private IppRuleNumberUp findCustomRuleTest(final IppRuleNumberUp template) {
        final String[][] testRules = { //
        };

        if (testRules.length == 0) {
            return null;
        }

        final IppRuleNumberUp rule =
                findCustomRule(createRuleList(testRules), template);

        if (rule != null) {
            LOGGER.warn("Test Rule\n" //
                    + "PDF  landscape   [{}] rotation [{}]\n" //
                    + "User rotate      [{}] n-up [{}]\n" //
                    + "---> orientation [{}] layout [{}]", rule.isLandscape(),
                    rule.getPdfRotation(), rule.getUserRotate(),
                    rule.getNumberUp(), rule.getOrientationRequested(),
                    rule.getNumberUpLayout());
        }
        return rule;
    }

    @Override
    public IppRuleNumberUp findNumberUpRule(final boolean landscapeMinus90,
            final IppRuleNumberUp template) {

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Template\n" //
                    + "PDF  landscape [{}] rotation [{}] content [{}]\n"
                    + "User rotate    [{}] n-up [{}]", //
                    template.isLandscape(), template.getPdfRotation(),
                    template.getPdfContentRotation(), template.getUserRotate(),
                    template.getNumberUp());
        }

        final IppRuleNumberUp rule = findCustomRuleTest(template);
        if (rule != null) {
            return rule;
        }

        return findCustomRule(this.numberUpRules, template);
    }

    /**
     * Finds a matching {@link IppRuleNumberUp} for a template rule.
     *
     * @param numberUpRules
     *            The list of rules.
     * @param template
     *            The template rule with <i>independent</i> variables.
     * @return The template rule object supplemented with <i>dependent</i>
     *         variables, or {@code null} when no rule found.
     */
    private static IppRuleNumberUp findCustomRule(
            final List<IppRuleNumberUp> numberUpRules,
            final IppRuleNumberUp template) {

        IppRuleNumberUp rule = null;

        final String savedNup = template.getNumberUp();

        if (template.getNumberUp().equals(N_9)
                || template.getNumberUp().equals(N_16)) {
            template.setNumberUp(N_4);
        }

        for (final IppRuleNumberUp wlk : numberUpRules) {
            if (template.isParameterMatch(wlk)) {
                template.setDependentVars(wlk);
                rule = template;
                break;
            }
        }

        template.setNumberUp(savedNup);
        return rule;
    }

}
