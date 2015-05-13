/*
 * This file is part of the SavaPage project <http://savapage.org>.
 * Copyright (c) 2011-2014 Datraverse B.V.
 * Authors: Rijk Ravestein.
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

import org.apache.commons.lang3.StringUtils;
import org.savapage.core.SpException;
import org.savapage.core.config.ConfigManager;
import org.savapage.core.config.IConfigProp.Key;
import org.savapage.core.dao.PosPurchaseDao.ReceiptNumberPrefixEnum;
import org.savapage.core.dao.PrinterDao;
import org.savapage.core.dao.helpers.AccountTrxTypeEnum;
import org.savapage.core.dto.AccountDisplayInfoDto;
import org.savapage.core.dto.AccountVoucherRedeemDto;
import org.savapage.core.dto.IppMediaCostDto;
import org.savapage.core.dto.IppMediaSourceCostDto;
import org.savapage.core.dto.MediaCostDto;
import org.savapage.core.dto.MediaPageCostDto;
import org.savapage.core.dto.PosDepositDto;
import org.savapage.core.dto.PosDepositReceiptDto;
import org.savapage.core.dto.UserAccountingDto;
import org.savapage.core.dto.UserPaymentGatewayDto;
import org.savapage.core.jpa.Account;
import org.savapage.core.jpa.Account.AccountTypeEnum;
import org.savapage.core.jpa.AccountTrx;
import org.savapage.core.jpa.AccountVoucher;
import org.savapage.core.jpa.DocLog;
import org.savapage.core.jpa.PosPurchase;
import org.savapage.core.jpa.Printer;
import org.savapage.core.jpa.User;
import org.savapage.core.jpa.UserAccount;
import org.savapage.core.jpa.UserGroup;
import org.savapage.core.json.rpc.AbstractJsonRpcMethodResponse;
import org.savapage.core.json.rpc.JsonRpcMethodResult;
import org.savapage.core.json.rpc.impl.ResultPosDeposit;
import org.savapage.core.print.proxy.ProxyPrintException;
import org.savapage.core.print.proxy.ProxyPrintJobChunk;
import org.savapage.core.print.proxy.ProxyPrintJobChunkInfo;
import org.savapage.core.services.AccountingService;
import org.savapage.core.services.ServiceContext;
import org.savapage.core.services.helpers.AccountTrxInfo;
import org.savapage.core.services.helpers.AccountTrxInfoSet;
import org.savapage.core.services.helpers.ProxyPrintCostParms;
import org.savapage.core.util.BigDecimalUtil;

/**
 *
 * @author Datraverse B.V.
 *
 */
public final class AccountingServiceImpl extends AbstractService implements
        AccountingService {

    @Override
    public PrinterDao.CostMediaAttr getCostMediaAttr() {
        return printerDAO().getCostMediaAttr();
    }

    @Override
    public PrinterDao.CostMediaAttr getCostMediaAttr(final String ippMediaName) {
        return printerDAO().getCostMediaAttr(ippMediaName);
    }

    @Override
    public PrinterDao.MediaSourceAttr getMediaSourceAttr(
            final String ippMediaSourceName) {

        return printerDAO().getMediaSourceAttr(ippMediaSourceName);
    }

    @Override
    public UserAccount getActiveUserAccount(final String userId,
            final Account.AccountTypeEnum accountType) {

        return userAccountDAO().findByActiveUserId(userId, accountType);
    }

    /**
     * Checks if account is a shared account, if not an {@link SpException} is
     * thrown.
     *
     * @param account
     *            The account to check.
     */
    private void checkSharedAccountType(final Account account) {

        final AccountTypeEnum accountType =
                AccountTypeEnum.valueOf(account.getAccountType());

        if (accountType != AccountTypeEnum.SHARED) {
            throw new SpException(String.format(
                    "AccountType [%s] expected: found [%s]",
                    AccountTypeEnum.SHARED.toString(), accountType.toString()));
        }
    }

    @Override
    public Account lazyGetSharedAccount(final String accountName,
            final Account accountTemplate) {

        checkSharedAccountType(accountTemplate);

        final Account parent = accountTemplate.getParent();
        Account account;

        if (parent == null) {
            account = accountDAO().findActiveSharedAccountByName(accountName);
        } else {
            account =
                    accountDAO().findActiveSharedChildAccountByName(
                            parent.getId(), accountName);
        }

        if (account == null) {
            account =
                    accountDAO().createFromTemplate(accountName,
                            accountTemplate);
        }
        return account;
    }

    @Override
    public UserAccount lazyGetUserAccount(final User user,
            final Account.AccountTypeEnum accountType) {

        UserAccount userAccount =
                this.getActiveUserAccount(user.getUserId(), accountType);

        if (userAccount == null) {

            final UserGroup userGroup;

            if (accountType == AccountTypeEnum.USER) {
                if (user.getInternal()) {
                    userGroup = userGroupService().getInternalUserGroup();
                } else {
                    userGroup = userGroupService().getExternalUserGroup();
                }
            } else {
                userGroup = null;
            }

            userAccount = createUserAccount(user, userGroup);
        }
        return userAccount;
    }

    @Override
    public UserAccountingDto getUserAccounting(final User user) {

        UserAccountingDto dto = new UserAccountingDto();

        dto.setLocale(ServiceContext.getLocale().toLanguageTag());

        Account account =
                lazyGetUserAccount(user, AccountTypeEnum.USER).getAccount();

        try {
            dto.setBalance(BigDecimalUtil.localize(account.getBalance(),
                    ConfigManager.getUserBalanceDecimals(),
                    ServiceContext.getLocale(), true));

            UserAccountingDto.CreditLimitEnum creditLimit;

            if (account.getRestricted()) {
                if (account.getUseGlobalOverdraft()) {
                    creditLimit = UserAccountingDto.CreditLimitEnum.DEFAULT;
                } else {
                    creditLimit = UserAccountingDto.CreditLimitEnum.INDIVIDUAL;
                }
            } else {
                creditLimit = UserAccountingDto.CreditLimitEnum.NONE;
            }

            dto.setCreditLimit(creditLimit);
            dto.setCreditLimitAmount(BigDecimalUtil.localize(
                    account.getOverdraft(),
                    ConfigManager.getUserBalanceDecimals(),
                    ServiceContext.getLocale(), true));

        } catch (ParseException e) {
            throw new SpException(e);
        }
        return dto;
    }

    @Override
    public AbstractJsonRpcMethodResponse setUserAccounting(final User user,
            final UserAccountingDto dto) {

        final Locale dtoLocale;

        if (dto.getLocale() != null) {
            dtoLocale = Locale.forLanguageTag(dto.getLocale());
        } else {
            dtoLocale = ServiceContext.getLocale();
        }

        final AccountTypeEnum accountType = AccountTypeEnum.USER;

        final UserAccount userAccount =
                this.getActiveUserAccount(user.getUserId(), accountType);
        final boolean isNewAccount = userAccount == null;

        final Account account;

        if (isNewAccount) {
            account = lazyGetUserAccount(user, accountType).getAccount();
        } else {
            account = userAccount.getAccount();
        }

        /*
         * Change balance?
         */
        if ((dto.getBalance() != null)
                && (isNewAccount || !dto.getKeepBalance().booleanValue())) {

            final String amount = dto.getBalance();

            try {

                final BigDecimal balanceNew =
                        BigDecimalUtil.parse(amount, dtoLocale, false, false);

                final BigDecimal balanceDiff =
                        balanceNew.subtract(account.getBalance());

                if (balanceDiff.compareTo(BigDecimal.ZERO) != 0) {

                    String comment = dto.getComment();
                    if (StringUtils.isBlank(comment)) {
                        comment = "";
                    }

                    final AccountTrx trx =
                            createAccountTrx(account,
                                    AccountTrxTypeEnum.ADJUST, balanceDiff,
                                    balanceNew, comment);

                    accountTrxDAO().create(trx);

                    account.setBalance(balanceNew);
                }

            } catch (ParseException e) {
                return createError("msg-amount-error", amount);
            }
        }

        final UserAccountingDto.CreditLimitEnum creditLimit =
                dto.getCreditLimit();

        if (creditLimit != null) {

            account.setRestricted(creditLimit != UserAccountingDto.CreditLimitEnum.NONE);
            account.setUseGlobalOverdraft(creditLimit == UserAccountingDto.CreditLimitEnum.DEFAULT);

            if (creditLimit == UserAccountingDto.CreditLimitEnum.INDIVIDUAL) {
                final String amount = dto.getCreditLimitAmount();
                try {
                    account.setOverdraft(BigDecimalUtil.parse(amount,
                            dtoLocale, false, false));
                } catch (ParseException e) {
                    return createError("msg-amount-error", amount);
                }
            }
        }

        account.setModifiedBy(ServiceContext.getActor());
        account.setModifiedDate(ServiceContext.getTransactionDate());

        accountDAO().update(account);

        return JsonRpcMethodResult.createOkResult();
    }

    /**
     * Creates an {@link Account} of type {@link Account.AccountTypeEnum#USER}
     * for a {@link User} (including the related {@link UserAccount}).
     *
     * @param user
     *            The {@link User} as owner of the account.
     * @param userGroupTemplate
     *            The {@link UserGroup} to be used as template. Is {@code null}
     *            when NO template is available.
     * @return The {@link UserAccount} created.
     */
    private UserAccount createUserAccount(final User user,
            final UserGroup userGroupTemplate) {

        final String actor = ServiceContext.getActor();
        final Date trxDate = ServiceContext.getTransactionDate();

        //
        final Account account = new Account();

        if (userGroupTemplate != null
                && userGroupTemplate.getInitialSettingsEnabled()) {

            account.setBalance(userGroupTemplate.getInitialCredit());
            account.setOverdraft(userGroupTemplate.getInitialOverdraft());
            account.setRestricted(userGroupTemplate.getInitiallyRestricted());
            account.setUseGlobalOverdraft(userGroupTemplate
                    .getInitialUseGlobalOverdraft());

        } else {
            account.setBalance(BigDecimal.ZERO);
            account.setOverdraft(BigDecimal.ZERO);
            account.setRestricted(true);
            account.setUseGlobalOverdraft(false);
        }

        account.setAccountType(Account.AccountTypeEnum.USER.toString());
        account.setComments(Account.CommentsEnum.COMMENT_OPTIONAL.toString());
        account.setInvoicing(Account.InvoicingEnum.USER_CHOICE_ON.toString());
        account.setDeleted(false);
        account.setDisabled(false);
        account.setName(user.getUserId());
        account.setNameLower(user.getUserId().toLowerCase());

        account.setCreatedBy(actor);
        account.setCreatedDate(trxDate);

        accountDAO().create(account);

        //
        final UserAccount userAccount = new UserAccount();

        userAccount.setAccount(account);
        userAccount.setUser(user);

        userAccount.setCreatedBy(actor);
        userAccount.setCreatedDate(trxDate);

        userAccountDAO().create(userAccount);

        //
        return userAccount;
    }

    /**
     * Creates a transaction.
     * <p>
     * Transaction Actor and Date are retrieved from the {@link ServiceContext}.
     * </p>
     *
     * @param account
     *            The {@link Account} the transaction is executed on.
     * @param trxType
     *            The {@link AccountTrxTypeEnum}.
     * @param amount
     *            The transaction amount.
     * @param accountBalance
     *            The balance of the account AFTER this transaction.
     * @param comment
     *            The transaction comment.
     * @return The created transaction.
     */
    private AccountTrx createAccountTrx(final Account account,
            final AccountTrxTypeEnum trxType, final BigDecimal amount,
            final BigDecimal accountBalance, final String comment) {

        AccountTrx trx = new AccountTrx();
        trx.setAccount(account);

        trx.setAmount(amount);
        trx.setBalance(accountBalance);
        trx.setComment(comment);
        trx.setIsCredit(amount.compareTo(BigDecimal.ZERO) > 0);

        trx.setTrxType(trxType.toString());

        trx.setTransactionWeight(Integer.valueOf(1));

        trx.setTransactedBy(ServiceContext.getActor());
        trx.setTransactionDate(ServiceContext.getTransactionDate());

        return trx;
    }

    @Override
    public void createAccountTrx(final Account account, final DocLog docLog,
            final AccountTrxTypeEnum trxType) {
        createAccountTrx(account, docLog, trxType, 1, 1);
    }

    @Override
    public void createAccountTrxs(final AccountTrxInfoSet accountTrxInfoSet,
            final DocLog docLog, final AccountTrxTypeEnum trxType) {

        final int nTotalWeight = accountTrxInfoSet.calcTotalWeight();

        for (final AccountTrxInfo trxInfo : accountTrxInfoSet
                .getAccountTrxInfoList()) {

            createAccountTrx(trxInfo.getAccount(), docLog, trxType,
                    nTotalWeight, trxInfo.getWeight());
        }
    }

    /**
     * Calculates the weighted amount.
     *
     * @param amount
     *            The amount to weigh.
     * @param weightTotal
     *            The total of all weights.
     * @param weight
     *            The mathematical weight of the transaction in the context of a
     *            transaction set.
     * @param scale
     *            The scale (precision).
     * @return The weighted amount.
     */
    @Override
    public BigDecimal calcWeightedAmount(final BigDecimal amount,
            final int weightTotal, final int weight, final int scale) {
        return amount.multiply(BigDecimal.valueOf(weight)).divide(
                BigDecimal.valueOf(weightTotal), scale, RoundingMode.HALF_EVEN);
    }

    /**
     * Creates an {@link AccountTrx} of {@link AccountTrx.AccountTrxTypeEnum},
     * updates the {@link Account} and adds the {@link AccountTrx} to the
     * {@link DocLog}.
     *
     * @param account
     *            The {@link Account} to update.
     * @param docLog
     *            The {@link DocLog} to be accounted for.
     * @param trxType
     *            The {@link AccountTrxTypeEnum} of the {@link AccountTrx}.
     * @param weightTotal
     *            The total of all weights.
     * @param weight
     *            The mathematical weight of the transaction in the context of a
     *            transaction set.
     */
    private void createAccountTrx(final Account account, final DocLog docLog,
            final AccountTrxTypeEnum trxType, final int weightTotal,
            final int weight) {

        final String actor = ServiceContext.getActor();
        final Date trxDate = ServiceContext.getTransactionDate();

        /*
         * Weighted amount.
         */
        BigDecimal trxAmount = docLog.getCostOriginal().negate();

        if (weight != weightTotal) {
            trxAmount =
                    calcWeightedAmount(trxAmount, weightTotal, weight,
                            ConfigManager.getFinancialDecimalsInDatabase());
        }

        /*
         * Update account.
         */
        account.setBalance(account.getBalance().add(trxAmount));

        account.setModifiedBy(actor);
        account.setModifiedDate(trxDate);

        accountDAO().update(account);

        /*
         * Create transaction
         */
        final AccountTrx trx = new AccountTrx();

        trx.setAccount(account);
        trx.setDocLog(docLog);

        trx.setAmount(trxAmount);
        trx.setBalance(account.getBalance());
        trx.setComment("");
        trx.setIsCredit(false);

        trx.setTrxType(trxType.toString());

        trx.setTransactionWeight(weight);

        trx.setTransactedBy(ServiceContext.getActor());
        trx.setTransactionDate(ServiceContext.getTransactionDate());

        accountTrxDAO().create(trx);

        /*
         * Add transaction to the DocLog transaction list.
         */
        if (docLog.getTransactions() == null) {
            docLog.setTransactions(new ArrayList<AccountTrx>());
        }
        docLog.getTransactions().add(trx);
    }

    /**
     * Calculates the cost of a priont job.
     * <p>
     * The pageCostTwoSided is applied to pages that are print on both sides of
     * a sheet. If a job has an odd number of pages, the pageCostTwoSided is not
     * applied to the last page. For example, if a 3 page document is printed as
     * duplex, the pageCostTwoSided is applied to the first 2 pages: the last
     * page has pageCostOneSided.
     * </p>
     *
     * @param nPages
     *            The number of pages.
     * @param nCopies
     *            the number of copies.
     * @param duplex
     *            {@code true} if a duplex print job.
     * @param pageCostOneSided
     *            Cost per page when single-sided.
     * @param pageCostTwoSided
     *            Cost per page when double-sided.
     * @return The {@link BigDecimal}.
     */
    public static BigDecimal
            calcPrintJobCost(final int nPages, final int nCopies,
                    final boolean duplex, final BigDecimal pageCostOneSided,
                    final BigDecimal pageCostTwoSided) {

        final BigDecimal copies = BigDecimal.valueOf(nCopies);

        BigDecimal pagesOneSided = BigDecimal.ZERO;
        BigDecimal pagesTwoSided = BigDecimal.ZERO;

        if (duplex) {
            pagesTwoSided = new BigDecimal((nPages / 2) * 2);
            pagesOneSided = new BigDecimal(nPages % 2);
        } else {
            pagesOneSided = new BigDecimal(nPages);
        }

        return pageCostOneSided.multiply(pagesOneSided).multiply(copies)
                .add(pageCostTwoSided.multiply(pagesTwoSided).multiply(copies));

    }

    /**
     *
     * @param cost
     * @param grayscale
     * @return The {@link BigDecimal}.
     */
    private BigDecimal getCost(final MediaPageCostDto cost,
            final boolean grayscale) {

        String strCost = null;

        if (grayscale) {
            strCost = cost.getCostGrayscale();
        } else {
            strCost = cost.getCostColor();
        }

        return new BigDecimal(strCost);
    }

    @Override
    public boolean isBalanceSufficient(final Account account,
            final BigDecimal cost) {

        boolean isChargeable = true;

        if (account.getRestricted()) {

            final BigDecimal creditLimit;

            if (account.getUseGlobalOverdraft()) {
                creditLimit =
                        ConfigManager.instance().getConfigBigDecimal(
                                Key.FINANCIAL_GLOBAL_CREDIT_LIMIT);
            } else {
                creditLimit = account.getOverdraft();
            }

            final BigDecimal balanceAfter = account.getBalance().subtract(cost);

            isChargeable = balanceAfter.compareTo(creditLimit) >= 0;
        }
        return isChargeable;
    }

    @Override
    public BigDecimal calcProxyPrintCost(final Printer printer,
            final ProxyPrintCostParms costParms) {

        BigDecimal pageCostOneSided = BigDecimal.ZERO;
        BigDecimal pageCostTwoSided = BigDecimal.ZERO;

        final IppMediaSourceCostDto mediaSourceCost =
                costParms.getMediaSourceCost();

        if (mediaSourceCost != null && !mediaSourceCost.isManualSource()) {

            final MediaCostDto pageCost =
                    mediaSourceCost.getMedia().getPageCost();

            pageCostOneSided =
                    this.getCost(pageCost.getCostOneSided(),
                            costParms.isGrayscale());

            pageCostTwoSided =
                    this.getCost(pageCost.getCostTwoSided(),
                            costParms.isGrayscale());

        } else {

            switch (printerDAO().getChargeType(printer.getChargeType())) {

            case SIMPLE:

                pageCostOneSided = printer.getDefaultCost();
                pageCostTwoSided = pageCostOneSided;
                break;

            case MEDIA:

                final IppMediaCostDto costDto =
                        printerDAO().getMediaCost(printer,
                                costParms.getIppMediaOption());

                if (costDto != null) {

                    final MediaCostDto pageCost = costDto.getPageCost();

                    pageCostOneSided =
                            this.getCost(pageCost.getCostOneSided(),
                                    costParms.isGrayscale());

                    pageCostTwoSided =
                            this.getCost(pageCost.getCostTwoSided(),
                                    costParms.isGrayscale());
                }
                break;

            default:
                throw new SpException("Charge type [" + printer.getChargeType()
                        + "] not supported");
            }
        }

        return calcPrintJobCost(costParms.getNumberOfPages(),
                costParms.getNumberOfCopies(), costParms.isDuplex(),
                pageCostOneSided, pageCostTwoSided);
    }

    /**
     * Gets the localized string for a BigDecimal.
     *
     * @param decimal
     *            The {@link BigDecimal}.
     * @param locale
     *            The {@link Locale}.
     * @param currencySymbol
     *            {@code null} when not available.
     * @return The localized string.
     * @throws ParseException
     */
    private String localizedPrinterCost(final BigDecimal decimal,
            final Locale locale, final String currencySymbol) {

        BigDecimal value = decimal;

        if (value == null) {
            value = BigDecimal.ZERO;
        }

        String cost = null;

        try {
            cost =
                    BigDecimalUtil.localize(value,
                            ConfigManager.getPrinterCostDecimals(), locale,
                            true);
        } catch (ParseException e) {
            throw new SpException(e.getMessage());
        }

        if (StringUtils.isBlank(currencySymbol)) {
            return cost;
        }
        return currencySymbol + cost;
    }

    @Override
    public void validateProxyPrintUserCost(final User user,
            final BigDecimal cost, final Locale locale,
            final String currencySymbol) throws ProxyPrintException {
        /*
         * INVARIANT: User is NOT allowed to print when resulting balance is
         * below credit limit.
         */
        final Account account =
                this.lazyGetUserAccount(user, AccountTypeEnum.USER)
                        .getAccount();

        final BigDecimal balanceBefore = account.getBalance();
        final BigDecimal balanceAfter = balanceBefore.subtract(cost);

        if (account.getRestricted()) {

            final BigDecimal creditLimit;

            if (account.getUseGlobalOverdraft()) {
                creditLimit =
                        ConfigManager.instance().getConfigBigDecimal(
                                Key.FINANCIAL_GLOBAL_CREDIT_LIMIT);
            } else {
                creditLimit = account.getOverdraft();
            }

            if (balanceAfter.compareTo(creditLimit) < 0) {

                if (creditLimit.compareTo(BigDecimal.ZERO) == 0) {

                    throw new ProxyPrintException(localize(
                            "msg-print-denied-no-balance",
                            localizedPrinterCost(balanceBefore, locale,
                                    currencySymbol),
                            localizedPrinterCost(cost, locale, currencySymbol)));

                } else {
                    throw new ProxyPrintException(localize(
                            "msg-print-denied-no-credit",
                            localizedPrinterCost(creditLimit, locale,
                                    currencySymbol),
                            localizedPrinterCost(cost, locale, currencySymbol),
                            localizedPrinterCost(balanceBefore, locale,
                                    currencySymbol)));
                }
            }
        }
    }

    @Override
    public BigDecimal calcProxyPrintCost(final Locale locale,
            final String currencySymbol, final User user,
            final Printer printer, final ProxyPrintCostParms costParms,
            final ProxyPrintJobChunkInfo jobChunkInfo)
            throws ProxyPrintException {

        BigDecimal totalCost = BigDecimal.ZERO;

        /*
         * Traverse the chunks and calculate.
         */
        for (final ProxyPrintJobChunk chunk : jobChunkInfo.getChunks()) {

            costParms.setNumberOfPages(chunk.getNumberOfPages());
            costParms.setIppMediaOption(chunk.getAssignedMedia()
                    .getIppKeyword());
            costParms.setMediaSourceCost(chunk.getAssignedMediaSource());

            final BigDecimal chunkCost =
                    this.calcProxyPrintCost(printer, costParms);

            chunk.setCost(chunkCost);

            totalCost = totalCost.add(chunkCost);
        }

        validateProxyPrintUserCost(user, totalCost, locale, currencySymbol);

        return totalCost;
    }

    @Override
    public String getFormattedUserBalance(final User user, final Locale locale,
            final String currencySymbol) {

        final String balance;

        if (user.getDeleted()) {

            balance =
                    formatUserBalance(
                            userAccountDAO().findByUserId(user.getId(),
                                    AccountTypeEnum.USER), locale,
                            currencySymbol);
        } else {
            balance =
                    getFormattedUserBalance(user.getUserId(), locale,
                            currencySymbol);
        }
        return balance;
    }

    @Override
    public String getFormattedUserBalance(final String userId,
            final Locale locale, final String currencySymbol) {

        return formatUserBalance(
                getActiveUserAccount(userId, AccountTypeEnum.USER), locale,
                currencySymbol);

    }

    /**
     * Formats the {@link UserAccount} balance.
     *
     * @param userAccount
     *            The {@link UserAccount}.
     * @param locale
     * @param currencySymbol
     * @return
     */
    private String formatUserBalance(final UserAccount userAccount,
            final Locale locale, final String currencySymbol) {

        final BigDecimal userBalance;

        if (userAccount != null) {
            userBalance = userAccount.getAccount().getBalance();
        } else {
            userBalance = BigDecimal.ZERO;
        }

        final String currencySymbolWrk;

        if (currencySymbol == null) {
            currencySymbolWrk = "";
        } else {
            currencySymbolWrk = currencySymbol;
        }

        try {

            return BigDecimalUtil.localize(userBalance,
                    ConfigManager.getUserBalanceDecimals(), locale,
                    currencySymbolWrk, true);

        } catch (ParseException e) {
            throw new SpException(e);
        }
    }

    @Override
    public AccountDisplayInfoDto getAccountDisplayInfo(final User user,
            final Locale locale, final String currencySymbol) {

        String currencySymbolWrk = currencySymbol;
        if (currencySymbolWrk == null) {
            currencySymbolWrk = "";
        }

        AccountDisplayInfoDto dto = new AccountDisplayInfoDto();

        final Account account =
                this.lazyGetUserAccount(user, AccountTypeEnum.USER)
                        .getAccount();

        // ------------------
        String formattedCreditLimit;
        AccountDisplayInfoDto.Status status;

        if (account.getRestricted()) {

            BigDecimal creditLimit;

            if (account.getUseGlobalOverdraft()) {
                creditLimit =
                        ConfigManager.instance().getConfigBigDecimal(
                                Key.FINANCIAL_GLOBAL_CREDIT_LIMIT);
            } else {
                creditLimit = account.getOverdraft();
            }

            try {
                formattedCreditLimit =
                        BigDecimalUtil.localize(creditLimit,
                                ConfigManager.getUserBalanceDecimals(),
                                locale, currencySymbolWrk,
                                true);
            } catch (ParseException e) {
                throw new SpException(e);
            }

            if (account.getBalance().compareTo(creditLimit.negate()) < 0) {
                status = AccountDisplayInfoDto.Status.OVERDRAFT;
            } else if (account.getBalance().compareTo(BigDecimal.ZERO) > 0) {
                status = AccountDisplayInfoDto.Status.DEBIT;
            } else {
                status = AccountDisplayInfoDto.Status.CREDIT;
            }

        } else {

            formattedCreditLimit = null;

            if (account.getBalance().compareTo(BigDecimal.ZERO) > 0) {
                status = AccountDisplayInfoDto.Status.DEBIT;
            } else {
                status = AccountDisplayInfoDto.Status.CREDIT;
            }
        }

        try {
            dto.setBalance(BigDecimalUtil.localize(account.getBalance(),
                    ConfigManager.getUserBalanceDecimals(), locale,
                    currencySymbolWrk, true));
        } catch (ParseException e) {
            throw new SpException(e);
        }
        dto.setLocale(locale.getDisplayLanguage());
        dto.setCreditLimit(formattedCreditLimit);
        dto.setStatus(status);

        return dto;
    }

    @Override
    public AbstractJsonRpcMethodResponse redeemVoucher(
            final AccountVoucherRedeemDto dto) {

        final AccountVoucher voucher =
                accountVoucherDAO().findByCardNumber(dto.getCardNumber());

        final Date redeemDate = new Date(dto.getRedeemDate().longValue());

        /*
         * INVARIANT: cardNumber MUST exist, MUST NOT already be redeemed, and
         * MUST NOT be expired on the redeem date.
         */
        if (voucher == null
                || voucher.getRedeemedDate() != null
                || accountVoucherService()
                        .isVoucherExpired(voucher, redeemDate)) {
            return createErrorMsg(MSG_KEY_VOUCHER_REDEEM_NUMBER_INVALID);
        }

        /*
         * INVARIANT: User MUST exist.
         */
        final User user = userDAO().findActiveUserByUserId(dto.getUserId());

        if (user == null) {
            return createErrorMsg(MSG_KEY_VOUCHER_REDEEM_USER_UNKNOWN,
                    dto.getUserId());
        }

        /*
         * Update account.
         */
        final Account account =
                this.lazyGetUserAccount(user, AccountTypeEnum.USER)
                        .getAccount();

        account.setBalance(account.getBalance().add(voucher.getValueAmount()));
        account.setModifiedBy(ServiceContext.getActor());
        account.setModifiedDate(ServiceContext.getTransactionDate());

        accountDAO().update(account);

        /*
         * Create transaction.
         */
        final String comment =
                localize("msg-voucher-redeem-trx-comment", dto.getCardNumber());

        final AccountTrx accountTrx =
                this.createAccountTrx(account, AccountTrxTypeEnum.VOUCHER,
                        voucher.getValueAmount(), account.getBalance(), comment);

        accountTrx.setAccountVoucher(voucher);

        accountTrxDAO().create(accountTrx);

        /*
         * Update voucher
         */
        voucher.setRedeemedDate(redeemDate);
        voucher.setAccountTrx(accountTrx);

        accountVoucherDAO().update(voucher);

        //
        return JsonRpcMethodResult.createOkResult();
    }

    @Override
    public void acceptFundsFromGateway(final User user,
            final UserPaymentGatewayDto dto,
            final Account orphanedPaymentAccount) {

        /*
         * Find the account to add the amount on.
         */
        final Account account;

        if (user == null) {
            account = orphanedPaymentAccount;
        } else {
            account =
                    this.lazyGetUserAccount(user, AccountTypeEnum.USER)
                            .getAccount();
        }
        /*
         * Create a unique receipt number.
         */
        final String receiptNumber =
                String.format("%s-%s-%s", ReceiptNumberPrefixEnum.GATEWAY, dto
                        .getGatewayId().toUpperCase(), dto.getTransactionId());

        //
        addFundsToAccount(account, AccountTrxTypeEnum.GATEWAY,
                dto.getPaymentMethod(), receiptNumber, dto.getAmount(),
                dto.getComment());
    }

    /**
     * Add funds to an {@link Account}.
     *
     * @param account
     *            The {@link Account}.
     * @param accountTrxType
     *            The {@link AccountTrxTypeEnum}.
     * @param paymentType
     *            The payment type.
     * @param receiptNumber
     *            The receipt number.
     * @param amount
     *            The funds amount.
     * @param comment
     *            The comment set in {@link PosPurchase} and {@link AccountTrx}.
     * @return The {@link AbstractJsonRpcMethodResponse}.
     */
    private AbstractJsonRpcMethodResponse addFundsToAccount(
            final Account account, final AccountTrxTypeEnum accountTrxType,
            final String paymentType, final String receiptNumber,
            final BigDecimal amount, final String comment) {

        account.setBalance(account.getBalance().add(amount));
        account.setModifiedBy(ServiceContext.getActor());
        account.setModifiedDate(ServiceContext.getTransactionDate());

        /*
         * Create PosPurchase.
         */
        final PosPurchase purchase = new PosPurchase();

        purchase.setComment(comment);
        purchase.setPaymentType(paymentType);
        purchase.setTotalCost(amount);
        purchase.setReceiptNumber(receiptNumber);

        /*
         * Create transaction.
         */
        final AccountTrx accountTrx =
                this.createAccountTrx(account, accountTrxType, amount,
                        account.getBalance(), comment);

        // Set references.
        accountTrx.setPosPurchase(purchase);
        purchase.setAccountTrx(accountTrx);

        /*
         * Database update/persist.
         */
        accountDAO().update(account);
        accountTrxDAO().create(accountTrx);
        purchaseDAO().create(purchase);

        //
        final JsonRpcMethodResult methodResult =
                JsonRpcMethodResult.createOkResult();

        final ResultPosDeposit resultData = new ResultPosDeposit();
        resultData.setAccountTrxDbId(accountTrx.getId());

        methodResult.getResult().setData(resultData);

        return methodResult;

    }

    @Override
    public AbstractJsonRpcMethodResponse depositFunds(final PosDepositDto dto) {

        /*
         * INVARIANT: User MUST exist.
         */
        final User user = userDAO().findActiveUserByUserId(dto.getUserId());

        if (user == null) {
            return createErrorMsg(MSG_KEY_DEPOSIT_FUNDS_USER_UNKNOWN,
                    dto.getUserId());
        }

        /*
         * INVARIANT: Amount MUST be valid.
         */
        final String plainAmount =
                dto.getAmountMain() + "." + dto.getAmountCents();

        if (!BigDecimalUtil.isValid(plainAmount)) {
            return createErrorMsg(MSG_KEY_DEPOSIT_FUNDS_AMOUNT_ERROR);
        }

        final BigDecimal depositAmount = BigDecimalUtil.valueOf(plainAmount);

        /*
         * INVARIANT: Amount MUST be GT zero.
         */
        if (depositAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return createErrorMsg(MSG_KEY_DEPOSIT_FUNDS_AMOUNT_INVALID);
        }

        /*
         * Deposit amount into Account.
         */
        final Account account =
                this.lazyGetUserAccount(user, AccountTypeEnum.USER)
                        .getAccount();

        final String receiptNumber =
                purchaseDAO().getNextReceiptNumber(
                        ReceiptNumberPrefixEnum.DEPOSIT);

        return addFundsToAccount(account, AccountTrxTypeEnum.DEPOSIT,
                dto.getPaymentType(), receiptNumber, depositAmount,
                dto.getComment());

    }

    @Override
    public PosDepositReceiptDto createPosDepositReceiptDto(
            final Long accountTrxId) {

        final AccountTrx accountTrx = accountTrxDAO().findById(accountTrxId);

        if (accountTrx == null) {
            throw new SpException("Transaction not found.");
        }

        if (!accountTrx.getTrxType().equals(
                AccountTrxTypeEnum.DEPOSIT.toString())) {
            throw new SpException("This is not a DEPOSIT transaction.");
        }

        final PosPurchase purchase = accountTrx.getPosPurchase();

        final User user =
                userAccountDAO().findByAccountId(
                        accountTrx.getAccount().getId()).getUser();

        //
        final PosDepositReceiptDto receipt = new PosDepositReceiptDto();

        receipt.setComment(purchase.getComment());
        receipt.setPlainAmount(BigDecimalUtil.toPlainString(accountTrx
                .getAmount()));
        receipt.setPaymentType(purchase.getPaymentType());
        receipt.setReceiptNumber(purchase.getReceiptNumber());
        receipt.setTransactedBy(accountTrx.getTransactedBy());
        receipt.setTransactionDate(accountTrx.getTransactionDate().getTime());
        receipt.setUserId(user.getUserId());
        receipt.setUserFullName(user.getFullName());

        return receipt;
    }

}
