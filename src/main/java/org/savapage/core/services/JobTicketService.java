/*
 * This file is part of the SavaPage project <https://www.savapage.org>.
 * Copyright (c) 2011-2017 Datraverse B.V.
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
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * For more information, please contact Datraverse B.V. at this
 * address: info@datraverse.com
 */
package org.savapage.core.services;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.savapage.core.dao.enums.ACLRoleEnum;
import org.savapage.core.dto.RedirectPrinterDto;
import org.savapage.core.imaging.EcoPrintPdfTask;
import org.savapage.core.imaging.EcoPrintPdfTaskPendingException;
import org.savapage.core.ipp.client.IppConnectException;
import org.savapage.core.ipp.helpers.IppOptionMap;
import org.savapage.core.jpa.PrintOut;
import org.savapage.core.jpa.Printer;
import org.savapage.core.jpa.User;
import org.savapage.core.outbox.OutboxInfoDto.OutboxJobBaseDto;
import org.savapage.core.outbox.OutboxInfoDto.OutboxJobDto;
import org.savapage.core.pdf.PdfCreateInfo;
import org.savapage.core.print.proxy.ProxyPrintDocReq;
import org.savapage.core.print.proxy.ProxyPrintInboxReq;
import org.savapage.core.print.proxy.TicketJobSheetDto;
import org.savapage.core.services.helpers.DocContentPrintInInfo;
import org.savapage.core.services.helpers.JobTicketExecParms;

/**
 *
 * @author Rijk Ravestein
 *
 */
public interface JobTicketService extends StatefulService {

    /**
     * Sends Copy Job to the OutBox.
     * <p>
     * Note: invariants are NOT checked.
     * </p>
     *
     * @param user
     *            The requesting {@link User}.
     * @param request
     *            The {@link ProxyPrintInboxReq}.
     * @param deliveryDate
     *            The requested date of delivery.
     * @return The job ticket created.
     */
    OutboxJobDto createCopyJob(User user, ProxyPrintInboxReq request,
            Date deliveryDate);

    /**
     * Sends Job Ticket(s) to the OutBox.
     * <p>
     * Note: invariants are NOT checked.
     * </p>
     *
     * @param lockedUser
     *            The requesting {@link User}, which should be locked.
     * @param request
     *            The {@link ProxyPrintInboxReq}.
     * @param deliveryDate
     *            The requested date of delivery.
     * @return The job tickets created.
     *
     * @throws EcoPrintPdfTaskPendingException
     *             When {@link EcoPrintPdfTask} objects needed for this PDF are
     *             pending.
     */
    List<OutboxJobDto> proxyPrintInbox(User lockedUser,
            ProxyPrintInboxReq request, Date deliveryDate)
            throws EcoPrintPdfTaskPendingException;

    /**
     * Sends Print Job to the OutBox.
     * <p>
     * NOTE: The PDF file location is arbitrary and NOT part in the user's
     * inbox.
     * </p>
     *
     * @param lockedUser
     *            The requesting {@link User}, which should be locked.
     * @param request
     *            The {@link ProxyPrintDocReq}.
     * @param createInfo
     *            The {@link PdfCreateInfo} with the arbitrary (non-inbox) PDF
     *            file to print.
     * @param printInfo
     *            The {@link DocContentPrintInInfo}.
     * @param deliveryDate
     *            The requested date of delivery.
     * @throws IOException
     *             When file IO error occurs.
     */
    void proxyPrintPdf(User lockedUser, ProxyPrintDocReq request,
            final PdfCreateInfo createInfo, DocContentPrintInInfo printInfo,
            Date deliveryDate) throws IOException;

    /**
     * Creates and formats a unique ticket number.
     *
     * @return The formatted ticket number.
     */
    String createTicketNumber();

    /**
     * Gets the pending Job Tickets.
     *
     * @return The Job Tickets.
     */
    List<OutboxJobDto> getTickets();

    /**
     * Gets the pending Job Ticket.
     *
     * @param fileName
     *            The unique PDF file name of the job (no path).
     * @return The Job Ticket or {@code null} when not found.
     */
    OutboxJobDto getTicket(String fileName);

    /**
     * Gets the pending Job Tickets of a {@link User}.
     *
     * @param userId
     *            The {@link User} database key.
     * @return The Job Tickets.
     */
    List<OutboxJobDto> getTickets(Long userId);

    /**
     * Gets the pending Job Ticket belonging to a {@link User} job file.
     *
     * @param userId
     *            The {@link User} database key.
     * @param fileName
     *            The unique PDF file name of the job (no path).
     * @return The Job Ticket or {@code null} when not found, or not owned by
     *         this user.
     */
    OutboxJobDto getTicket(Long userId, String fileName);

    /**
     * Cancels the pending Job Tickets of a {@link User}.
     *
     * @param userId
     *            The {@link User} database key.
     * @return The number of Job Tickets removed.
     */
    int cancelTickets(Long userId);

    /**
     * Cancels a Job Ticket with an extra user check.
     *
     * @param userId
     *            The {@link User} database key of the ticket owner.
     * @param fileName
     *            The unique PDF file name of the job to remove.
     * @return The removed ticket of {@code null} when ticket was not found.
     * @throws IllegalArgumentException
     *             When Job Ticket is not owned by user.
     */
    OutboxJobDto cancelTicket(Long userId, String fileName);

    /**
     * Cancels a Job Ticket.
     *
     * @param fileName
     *            The unique PDF file name of the job to remove.
     * @return The removed ticket or {@code null} when ticket was not found.
     */
    OutboxJobDto cancelTicket(String fileName);

    /**
     * Cancels a Job Ticket proxy print.
     *
     * @param fileName
     *            The unique PDF file name of the job ticket.
     * @return {@link Boolean#TRUE} when cancelled, {@link Boolean#FALSE} when
     *         cancellation failed, or {@code null} when ticket was not found.
     */
    Boolean cancelTicketPrint(String fileName);

    /**
     * Gets the {@link PrintOut} of a job ticket.
     *
     * @param fileName
     *            The unique PDF file name of the job ticket.
     * @return The {@link PrintOut} or {@code null} when not present.
     * @throws FileNotFoundException
     *             When ticket is not found.
     */
    PrintOut getTicketPrintOut(String fileName) throws FileNotFoundException;

    /**
     * Closes a Job Ticket after proxy print.
     *
     * @param fileName
     *            The unique PDF file name of the job to remove.
     * @return The closed ticket or {@code null} when ticket was not found.
     */
    OutboxJobDto closeTicketPrint(String fileName);

    /**
     * Updates a Job Ticket.
     *
     * @param dto
     *            The ticket.
     * @return {@code true} when found and updated, {@code false} when not
     *         found.
     * @throws IOException
     *             When file IO error occurs.
     */
    boolean updateTicket(OutboxJobDto dto) throws IOException;

    /**
     * Notifies Job Ticket owner (by email) that ticket is completed.
     *
     * @param dto
     *            The {@link OutboxJobBaseDto}.
     * @param operator
     *            The user id of the Job Ticket Operator.
     * @param user
     *            The Job Ticket owner.
     * @param locale
     *            The locale for the email text.
     * @return The email address, or {@code null} when not send.
     */
    String notifyTicketCompletedByEmail(OutboxJobBaseDto dto, String operator,
            User user, Locale locale);

    /**
     * Notifies Job Ticket owner (by email) that ticket is canceled.
     *
     * @param dto
     *            The {@link OutboxJobBaseDto}.
     * @param operator
     *            The user id of the Job Ticket Operator.
     * @param user
     *            The Job Ticket owner.
     * @param locale
     *            The locale for the email text.
     * @return The email address, or {@code null} when not send.
     */
    String notifyTicketCanceledByEmail(OutboxJobBaseDto dto, String operator,
            User user, Locale locale);

    /**
     * Prints and settles a Job Ticket.
     *
     * @param parms
     *            The parameters.
     *
     * @return The printed ticket or {@code null} when ticket was not found.
     * @throws IOException
     *             When IO error.
     * @throws IppConnectException
     *             When connection to CUPS fails.
     */
    OutboxJobDto printTicket(JobTicketExecParms parms)
            throws IOException, IppConnectException;

    /**
     * Retries a Job Ticket Print (typically after a job is cancelled, due to
     * printer failure). Note: this method does <i>not</i> settle the ticket,
     * since it is assumed this is already done at the first print trial.
     *
     * @param parms
     *            The parameters.
     *
     * @return The printed ticket or {@code null} when ticket was not found.
     * @throws IOException
     *             When IO error.
     * @throws IppConnectException
     *             When connection to CUPS fails.
     */
    OutboxJobDto retryTicketPrint(JobTicketExecParms parms)
            throws IOException, IppConnectException;

    /**
     * Settles a Job Ticket without printing it.
     *
     * @param operator
     *            The {@link User#getUserId()} with
     *            {@link ACLRoleEnum#JOB_TICKET_OPERATOR}.
     * @param printer
     *            The redirect printer.
     * @param fileName
     *            The unique PDF file name of the job to print.
     * @return The printed ticket or {@code null} when ticket was not found.
     * @throws IOException
     *             When IO error.
     */
    OutboxJobDto settleTicket(String operator, Printer printer, String fileName)
            throws IOException;

    /**
     * Gets the list of {@link RedirectPrinterDto} compatible printers for a Job
     * Ticket.
     *
     * @param job
     *            The Job Ticket.
     * @param optionFilter
     *            An additional filter, apart from the Job Ticket specification,
     *            of IPP option values that must be present in the redirect
     *            printers.
     * @param locale
     *            The {@link Locale} for UI texts.
     * @return The list of redirect printers (can be empty) or {@code null} when
     *         job ticket is not found.
     */
    List<RedirectPrinterDto> getRedirectPrinters(OutboxJobDto job,
            IppOptionMap optionFilter, Locale locale);

    /**
     * Gets a {@link RedirectPrinterDto} compatible printer for a Job Ticket.
     *
     * @param fileName
     *            The unique PDF file name of the job ticket.
     * @param optionFilter
     *            An additional filter, apart from the Job Ticket specification,
     *            of IPP option values that must be present in the redirect
     *            printer.
     * @param locale
     *            The {@link Locale} for UI texts.
     * @return The redirect printer or {@code null} when no job ticket or
     *         printer is found.
     */
    RedirectPrinterDto getRedirectPrinter(String fileName,
            IppOptionMap optionFilter, Locale locale);

    /**
     * Creates a single page PDF Job Sheet file to proxy print just before the
     * job ticket.
     *
     * @param user
     *            The unique user id.
     * @param jobDto
     *            The {@link OutboxJobDto} job ticket.
     * @param jobSheetDto
     *            Job Sheet information.
     * @return The PDF file.
     */
    File createTicketJobSheet(String user, OutboxJobDto jobDto,
            TicketJobSheetDto jobSheetDto);

    /**
     *
     * @return The number of tickets in queue.
     */
    int getJobTicketQueueSize();

    /**
     *
     * @param options
     *            The Ticket options.
     * @return Job Sheet info for Job Ticket.
     */
    TicketJobSheetDto getTicketJobSheet(IppOptionMap options);
}
