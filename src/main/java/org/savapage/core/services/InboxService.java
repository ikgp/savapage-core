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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.savapage.core.LetterheadNotFoundException;
import org.savapage.core.PostScriptDrmException;
import org.savapage.core.cli.AppDb;
import org.savapage.core.inbox.InboxInfoDto;
import org.savapage.core.inbox.InboxInfoDto.InboxJob;
import org.savapage.core.inbox.InboxInfoDto.InboxJobRange;
import org.savapage.core.inbox.LetterheadInfo;
import org.savapage.core.inbox.PageImages;
import org.savapage.core.inbox.RangeAtom;
import org.savapage.core.ipp.IppMediaSizeEnum;
import org.savapage.core.job.DocLogClean;
import org.savapage.core.jpa.DocIn;
import org.savapage.core.jpa.DocLog;
import org.savapage.core.jpa.User;
import org.savapage.core.pdf.PdfDocumentFonts;
import org.savapage.core.print.proxy.ProxyPrintJobChunk;
import org.savapage.core.print.proxy.ProxyPrintJobChunkRange;
import org.savapage.core.services.helpers.InboxContext;
import org.savapage.core.services.helpers.InboxPageImageInfo;
import org.savapage.core.services.helpers.PageRangeException;

/**
 *
 * @author Rijk Ravestein
 *
 */
public interface InboxService {

    /**
     * File extension for EcoPrint shadow PDF file.
     */
    String FILENAME_EXT_ECO = "eco";

    /**
     * Checks if user home directory (SafePages) exists.
     *
     * @param userId
     *            The unique user id.
     * @return {@code true} is inbox exists.
     */
    boolean doesHomeDirExist(String userId);

    /**
     * Reads and updates the {@link InboxInfoDto} JSON file from user home
     * directory.
     * <p>
     * The JSON file is read and updated (written) with newly arrived jobs. The
     * order of the jobs is the result of end-user editing operations, and only
     * initially reflects the order of production.
     * </p>
     *
     * @param userId
     *            The unique user id.
     * @return The {@link InboxInfoDto}.
     */
    InboxInfoDto getInboxInfo(String userId);

    /**
     * Reads and updates the {@link InboxInfoDto} JSON file from user home
     * directory.
     * <p>
     * The JSON file is read and updated (written) with newly arrived jobs. The
     * order of the jobs is the result of end-user editing operations, and only
     * initially reflects the order of production.
     * </p>
     *
     * @param inboxContext
     *            The {@link InboxContext}.
     * @return The {@link InboxInfoDto}.
     */
    InboxInfoDto getInboxInfo(InboxContext inboxContext);

    /**
     * Reads {@link InboxInfoDto} JSON file from user home directory.
     * <p>
     * NOTE: The JSON file is just read and NOT lazy updated with newly arrived
     * jobs. If you want the file to be updated use
     * {@link #getInboxInfo(String)}.
     * </p>
     *
     * @param userIdInbox
     *            The unique user id of the inbox (SafePages).
     * @return {@link InboxInfoDto} object.
     */
    InboxInfoDto readInboxInfo(String userIdInbox);

    /**
     * Stores {@link InboxInfoDto} as JSON file in user home directory.
     *
     * @param userIdInbox
     *            The unique user id of the inbox (SafePages).
     * @param inboxInfo
     *            The {@link InboxInfoDto} object.
     */
    void storeInboxInfo(String userIdInbox, InboxInfoDto inboxInfo);

    /**
     * Updates the last preview time of the {@link InboxInfoDto} JSON file in
     * the user home directory with the current date-time.
     *
     * @param userIdInbox
     *            The unique user id of the inbox (SafePages).
     * @return The touched {@link InboxInfoDto}.
     */
    InboxInfoDto touchLastPreviewTime(String userIdInbox);

    /**
     * Gets the number of pages in a PDF file.
     *
     * @param filePathPdf
     *            The full path of the PDF file.
     * @return The number of pages.
     */
    int getNumberOfPagesInPdfFile(String filePathPdf);

    /**
     * Gets sequence of chunked SafePages of all jobs.
     *
     * @param ctx
     *            The {@link InboxContext}.
     * @param firstDetailPage
     *            The first page of the detail sequence: null or LT or EQ to
     *            zero indicates the default first detail page.
     * @param uniqueUrlValue
     *            Value to make the output page URL's unique, so the browser
     *            will not use its cache, but will retrieve the image from the
     *            server again.
     * @param base64
     *            {@code true}: create image URL for inline BASE64 embedding.
     * @return The page images.
     */
    PageImages getPageChunks(InboxContext ctx, Integer firstDetailPage,
            String uniqueUrlValue, boolean base64);

    /**
     * Returns the private directory of letterheads for a user, or the public
     * directory if user is {@code null}.
     *
     * @param userId
     *            The unique user id. If {@code null} the public letterhead
     *            directory is returned.
     * @return Directory path.
     */
    String getLetterheadsDir(String userId);

    /**
     * Returns the private directory of letterheads for a user, or the public
     * directory if user is {@code null}.
     *
     * @param user
     *            If {@code null} the public letterhead directory is returned.
     * @return Directory path.
     */
    String getLetterheadLocation(User user);

    /**
     * Gets the list of letterheads for a user (or the public list if the user
     * is {@code null}).
     * <p>
     * Dangling reference to default letterhead in user jobinfo is
     * auto-corrected by setting the default letterhead to {@code null}.
     * </p>
     * <p>
     * Causes of dangling reference:
     * <ul>
     * <li>Administrator deleted a public letterhead which was picked as default
     * by a user.</li>
     * <li>Same user deleted a private letterhead in another session.</li>
     * </ul>
     * </p>
     *
     * @param userObj
     *            If specified, the public and private letterheads are returned.
     *            If {@code null} just the public letterheads are returned.
     * @return Letterhead objects by key.
     */
    Map<String, Object> getLetterheadList(User userObj);

    /**
     * Detaches the letterhead from the SafePages.
     *
     * @param userId
     *            The unique user id.
     */
    void detachLetterhead(String userId);

    /**
     * Attaches a letterhead to the user's SafePages.
     *
     * @param userId
     *            The unique user id.
     * @param letterheadId
     * @param isPublic
     * @throws LetterheadNotFoundException
     *             When letterheadId is not found in the letterhead store.
     */
    void attachLetterhead(User userId, String letterheadId, boolean isPublic)
            throws LetterheadNotFoundException;

    /**
     * Sets letterhead properties.
     * <p>
     * NOTE: When {@code isPublic} differs from {@code isPublicNew} the
     * letterhead is moved.
     * </p>
     *
     * @param user
     *            The {@link User}.
     * @param letterheadId
     *            The unique id of the letterhead.
     * @param name
     *            The name of the letterhead.
     * @param foreground
     *            {@code true} when letterhead is applied to the foreground.
     * @param isPublic
     *            The current value for public letterhead.
     * @param isPublicNew
     *            The new value for public letterhead.
     * @throws IOException
     *             When letterhead file operation fails.
     * @throws LetterheadNotFoundException
     *             When letterheadId is not found in the letterhead store.
     */
    void setLetterhead(User user, String letterheadId, String name,
            boolean foreground, boolean isPublic, boolean isPublicNew)
            throws IOException, LetterheadNotFoundException;

    /**
     * Deletes a private or public letterhead. If the deleted letterhead is
     * selected by the requesting user, it is detached.
     *
     * @param userReq
     *            The requesting user.
     * @param letterheadId
     *            Id of the letterhead.
     * @param isPublic
     *            if {@code true} the letterhead is public.
     * @throws LetterheadNotFoundException
     *             When letterheadId not found in letterhead store, or
     *             letterhead file could not be found.
     */
    void deleteLetterhead(User userReq, String letterheadId, boolean isPublic)
            throws LetterheadNotFoundException;

    /**
     * Creates a private letterhead PDF from the current safe pages. The newly
     * created letterhead is attached as default.
     *
     * @param user
     *            The user.
     * @return the created letterhead file.
     * @throws PostScriptDrmException
     *             When current SafePages has DRM.
     */
    File createLetterhead(User user) throws PostScriptDrmException;

    /**
     * Finds and returns the letterhead by letterhead ID.
     *
     * @param user
     *            If {@code null} the public letterheads are returned.
     * @param letterheadId
     *            The basename of the letterhead file.
     *
     * @return <code>null</code> when not found
     */
    LetterheadInfo.LetterheadJob getLetterhead(User user, String letterheadId);

    /**
     * Finds and returns the letterhead by letterhead ID.
     *
     * @param user
     *            Unique user id. If {@code null} the public letterheads are
     *            returned.
     * @param letterheadId
     *            The basename of the letterhead file.
     *
     * @return <code>null</code> when not found
     */
    LetterheadInfo.LetterheadJob getLetterheadExt(String user,
            String letterheadId);

    /**
     * Gets the page URLs and other properties of a letterhead.
     *
     * @param userObj
     *            The {@link User}.
     * @param letterheadId
     *            The basename of the letterhead file.
     * @param isPublic
     *            {@link true} for a public letterhead.
     * @param imgBase64
     * @return The letterhead details.
     * @throws LetterheadNotFoundException
     *             When the letterhead is not found.
     */
    Map<String, Object> getLetterheadDetails(User userObj, String letterheadId,
            Boolean isPublic, boolean imgBase64)
            throws LetterheadNotFoundException;

    /**
     * Returns private or public letterhead store.
     * <p>
     * The store is read from a json file and updated (written) with newly
     * arrived PDF letterheads.
     * </p>
     * <p>
     * The user Letterhead directory is created when it does not exist.
     * </p>
     *
     * @param userObj
     *            If {@code null} the public letterheads are returned.
     * @return The {@link LetterheadInfo}.
     */
    LetterheadInfo getLetterheads(User userObj);

    /**
     * Deletes ALL SafePages.
     *
     * @param userId
     *            The unique user id.
     * @return The number of deleted pages.
     */
    int deleteAllPages(String userId);

    /**
     * Deletes ALL SafePages.
     *
     * @param userId
     *            The unique user id.
     * @return The number of deleted jobs.
     */
    int deleteAllJobs(String userId);

    /**
     * Deletes pages from the virtual document.
     *
     * @param userId
     *            The unique user id.
     * @param ranges
     *            String with lpr style (1-based) page ranges.
     * @return The number of deleted pages.
     *
     * @throws IllegalArgumentException
     *             When page range syntax error.
     */
    int deletePages(String userId, String ranges);

    /**
     * Deletes pages from a <i>vanilla</i> inbox job.
     *
     * @param userId
     *            The unique user id.
     * @param iVanillaJobIndex
     *            The zero-based job index within the vanilla inbox.
     * @param ranges
     *            String with lpr style (1-based) page ranges of the job
     *            document.
     * @return The number of deleted pages.
     *
     * @throws IllegalStateException
     *             When inbox is not vanilla.
     */
    int deleteJobPages(String userId, int iVanillaJobIndex, String ranges);

    /**
     * Deletes jobs from the inbox that are part of the
     * {@link ProxyPrintJobChunk} list.
     * <p>
     * Note: a complete job is deleted when at least one (1) of its pages is
     * printed.
     * </p>
     *
     * @param userId
     *            The unique user id.
     * @param chunks
     *            The list of {@link ProxyPrintJobChunk} objects.
     * @return The number of deleted jobs.
     */
    int deleteJobs(String userId, List<ProxyPrintJobChunk> chunks);

    /**
     * Deletes jobs from the inbox that are expired.
     *
     * @param userid
     *            The unique user id.
     * @param msecReferenceTime
     *            The reference time in milliseconds.
     * @param msecExpiry
     *            The expiration period in milliseconds.
     * @return The number of deleted jobs.
     */
    int deleteJobs(String userid, long msecReferenceTime, long msecExpiry);

    /**
     * Deletes a job from the inbox.
     *
     * @param userId
     *            The unique user id.
     * @param iJob
     *            Zero-based index of the job.
     */
    void deleteJob(String userId, int iJob);

    /**
     * Edits (rotates, undeletes) a job.
     *
     * @param userId
     *            The unique user id.
     * @param iJob
     *            Zero-based index of the job.
     * @param rotate
     *            The absolute rotate setting.
     * @param undelete
     *            When {@code true} the job is fully restored at the first job
     *            page instance, all subsequent pages are discarded, so any
     *            inter-job ordering is gone.
     */
    void editJob(String userId, int iJob, boolean rotate, boolean undelete);

    /**
     *
     * @param userId
     *            The unique user id.
     * @param iJob
     *            Zero-based index of the job.
     * @return {@link PdfDocumentFonts}.
     */
    PdfDocumentFonts getJobFonts(String userId, int iJob);

    /**
     * Moves page ranges to a page position.
     *
     * @param userId
     *            The unique user id.
     * @param nRanges
     *            string with lpr style (1-based) page ranges.
     * @param nPage2Move2
     *            1-based page ordinal to move at. If this ordinal is negative,
     *            the move is NOT executed, and this method works as a DELETE.
     * @return dictionary $result['result'], $result['msg'],
     *         $result['values'][0]
     */
    int movePages(String userId, String nRanges, int nPage2Move2);

    /**
     * Checks if the {@link InboxInfoDto} is vanilla, i.e. user did not edit the
     * inbox content.
     * <p>
     * A vanilla inbox MUST:
     * <ul>
     * <li>have one (1) page range per job.</li>
     * <li>have page ranges each including all pages of a job and be in line
     * with the job ordinal.</li>
     * </ul>
     * </p>
     *
     * @param jobInfo
     *            The {@link InboxInfoDto} to check.
     * @return {@code true} if the {@link InboxInfoDto} is vanilla.
     */
    boolean isInboxVanilla(InboxInfoDto jobInfo);

    /**
     * Converts a sorted {@link RangeAtom} list with page numbers in job context
     * to an inbox context range string.
     * <p>
     * Note: the jobInfo must be vanilla.
     * </p>
     *
     * @param jobInfo
     *            The {@link InboxInfoDto}.
     * @param iVanillaJobIndex
     *            The zero-based vanilla job index in the jobInfo.
     * @param sortedRangeArrayJob
     *            The sorted {@link RangeAtom} list with page numbers in job
     *            context.
     * @return The range string.
     *
     * @throws IllegalStateException
     *             When inbox is not vanilla.
     */
    String toVanillaJobInboxRange(InboxInfoDto jobInfo, int iVanillaJobIndex,
            List<RangeAtom> sortedRangeArrayJob);

    /**
     * Prunes the print-in jobs which are not referenced anymore and deletes the
     * corresponding PS or PDF job file.
     * <p>
     * NOTE: the pruned print jobs are NOT persisted, just returned.
     * </p>
     *
     * @param homedir
     *            The SafePages directory.
     * @param userIdInbox
     *            The unique user id of the inbox (SafePages).
     * @param jobs
     *            The jobs to prune.
     * @return The pruned jobs.
     */
    InboxInfoDto pruneJobs(String homedir, String userIdInbox,
            InboxInfoDto jobs);

    /**
     * Prunes the print-in jobs which are <i>orphaned</i>, i.e. jobs that are
     * absent in the database, or jobs which are present in the {@code JSON}
     * index file, but are <i>missing</i> as inbox document.
     * <p>
     * Jobs can become <i>orphaned</i> when:
     * <ul>
     * <li>A database backup is restored.</li>
     * <li>An old backup of the SafePages directory is restored.</li>
     * <li>When {@link AppDb} was used to {@code --db-delete-logs}</li>
     * <li>A {@link User} logs on after a long time and his {@link DocIn
     * instances are cleaned up by the nightly {@link DocLogClean} job.</li>
     * </ul>
     * </p>
     * <p>
     * Jobs become <i>missing</i> due to unexpected IO errors, or manual remove
     * from the filesystem.
     * </p>
     *
     * @param userIdInbox
     *            The User ID of the Inbox (SafePages).
     * @param homedir
     *            The SafePages directory.
     * @param userDocLog
     *            The {@link User} of the {@link DocLog} instances.
     */
    void pruneOrphanJobs(String userIdInbox, String homedir, User userDocLog);

    /**
     * Prunes the print-in {@link InboxJobRange} instances in
     * {@link InboxInfoDto} for for Fast Proxy Printing. When the user edited
     * the inbox <i>all</i> jobs are pruned. When inbox is "vanilla" only
     * <i>expired</i> jobs are pruned.
     * <p>
     * NOTE: Since no user information is supplied, the pruned result is NOT
     * persisted.
     * </p>
     * <ul>
     * <li>The {@link InboxJob} instances are not pruned but, by not having
     * associated {@link InboxJobRange} instances, can become orphaned and
     * themselves become candidates for pruning.</li>
     * <li>If the user previewed the inbox within the expiration window, the
     * complete (edited) job info can be fast proxy printed as it is, i.e.
     * nothing is pruned. See: {@link InboxInfoDto#getLastPreviewTime()}.</li>
     * <li>IMPORTANT: when nothing is pruned the {@link InboxInfoDto}
     * <b>input</b> object is returned.</li>
     * </ul>
     *
     * @since 0.9.6
     *
     * @param jobInfo
     *            The full {@link InboxInfoDto}
     * @param expiryRef
     *            The reference date for calculating the expiration.
     * @param expiryMins
     *            The number of minutes after which a job expires as Fast Proxy
     *            Printing candidate.
     * @return A new {@link InboxInfoDto} object with a subset of valid Fast
     *         Proxy Printing jobs, or the {@link InboxInfoDto} input object
     *         when nothing was pruned.
     */
    InboxInfoDto pruneForFastProxyPrint(InboxInfoDto jobInfo, Date expiryRef,
            int expiryMins);

    /**
     * Prunes the print-in jobs which are expired for Fast Proxy Printing.
     * <p>
     * NOTE: This method is identical to
     * {@link #pruneForFastProxyPrint(String, Date, int)}, however the pruned
     * result <b>is persisted</b>.
     * </p>
     *
     * @param userId
     *            The unique user id.
     * @param expiryRef
     *            The reference date for calculating the expiration.
     * @param expiryMins
     *            The number of minutes after which a job expires as Fast Proxy
     *            Printing candidate.
     * @return The pruned {@link InboxInfoDto}.
     */
    InboxInfoDto pruneForFastProxyPrint(String userId, Date expiryRef,
            int expiryMins);

    /**
     * Calculates the inbox page range of a job identified by {@link UUID}.
     * <p>
     * Note: Database is NOT accessed.
     * </p>
     *
     * @param inbox
     *            The {@link InboxInfoDto}.
     * @param uuidJob
     *            The {@link UUID} of the job.
     * @return the {@link RangeAtom} or {@code null} when job not found or not
     *         vanilla.
     */
    RangeAtom calcVanillaInboxJobRange(InboxInfoDto inbox, UUID uuidJob);

    /**
     * Calculates total the number of SafePages.
     *
     * @param jobinfo
     *            The inbox with SafePages.
     * @return The number of SafePages.
     */
    int calcNumberOfPagesInJobs(InboxInfoDto jobinfo);

    /**
     * Calculates total the number of pages of page objects of jobs.
     *
     * @param jobs
     *            The jobs of the pages.
     * @param pages
     *            The pages.
     * @return The number of pages.
     */
    int calcNumberOfPages(List<InboxInfoDto.InboxJob> jobs,
            List<InboxJobRange> pages);

    /**
     * Calculates the number of pages in selected page ranges.
     *
     * @param jobs
     *            The {@link InboxInfoDto}.
     * @param iJob
     *            0-based job index. When {@code -1}, the scope is <i>all</i>
     *            documents.
     * @param rangesIn
     *            The page ranges.
     * @param sortedRangesOut
     *            The sorted rangesIn as output (can be {@code null}).
     * @return Number of pages in selected range.
     * @throws PageRangeException
     *             When rangesIn has syntax- or logical error.
     */
    int calcPagesInRanges(InboxInfoDto jobs, int iJob, String rangesIn,
            StringBuilder sortedRangesOut) throws PageRangeException;

    /**
     * Checks if the file represents a supported job type. It is irrelevant if
     * the file actually exists.
     * <p>
     * pdf, ps, and pnm (scan) are supported job types.
     * </p>
     *
     * @param file
     *            The file to check.
     * @return {@code true} if supported.
     */
    boolean isSupportedJobType(File file);

    /**
     * Checks is inbox file name syntax is valid.
     *
     * @param filename
     *            File name.
     * @return {@code true} if inbox file name syntax is valid.
     */
    boolean isValidInboxFileName(String filename);

    /**
     * Checks is letterhead inbox file name syntax is valid.
     *
     * @param filename
     *            File name.
     * @return {@code true} if letterhead inbox file name syntax is valid.
     */
    boolean isValidInboxLetterheadFileName(String filename);

    /**
     * Gets the {@link InboxPageImageInfo} belonging to the overall SafePages
     * page index.
     *
     * @param userId
     *            The unique user id.
     * @param iPage
     *            The zero-based page number of the accumulated SafePages.
     * @return {@code null} when the job is not found.
     */
    InboxPageImageInfo getPageImageInfo(String userId, int iPage);

    /**
     * Gets the {@link InboxPageImageInfo} belonging to a SafePage job page
     * index.
     *
     * @param userId
     *            The unique user id.
     * @param jobName
     *            The basename of the job file.
     * @param iPage
     *            The zero-based page number of the SafePage job.
     * @return {@code null} when the job is not found.
     */
    InboxPageImageInfo getPageImageInfo(String userId, String jobName,
            int iPage);

    /**
     * Gets the {@link IppMediaSizeEnum} of media used in {@link InboxInfoDto}.
     *
     * @param media
     *            The RFC2911 IPP media keyword as used in the inbox.
     * @return The {@link IppMediaSizeEnum} if a mapping is known,
     *         <code>null</code> when not.
     */
    IppMediaSizeEnum getIppMediaSize(String media);

    /**
     * Checks if the {@link InboxInfoDto} has jobs which all have the same
     * media.
     *
     * @param inboxInfo
     *            The {@link InboxInfoDto}.
     * @return The common {@link IppMediaSizeEnum} of all jobs or {@code null}
     *         when not all jobs have the same media.
     */
    IppMediaSizeEnum checkSingleInboxMedia(InboxInfoDto inboxInfo);

    /**
     * Creates a sorted array with key (begin) and value (end) from a string
     * with comma separated ranges.
     *
     * Each range in the string is a hyphen separated tuple of 2 digits, for
     * example: "12-,-3,6,5,8-9".
     *
     * Thus, `4-6' specifies four through six, inclusive. If you omit the first
     * number in a pair, the first in the range is assumed, and if you omit the
     * last number, the last in the range is assumed.
     *
     * @param rangesIn
     *            String with comma separated ranges.
     * @return The sorted array, or null when the input string has a syntax
     *         error.
     */
    List<RangeAtom> createSortedRangeArray(String rangesIn);

    /**
     * Filters the {@link InboxJobRange} list (pages) in the
     * {@link InboxInfoDto} object. The list {@link InboxJob} list (jobs)
     * remains untouched.
     * <p>
     * NOTE: the page numbers in page range filter refer to one-based page
     * numbers of the integrated (virtual) {@link InboxInfoDto} document as
     * observed by the end-user.
     * <p>
     *
     * @param jobs
     *            The {@link InboxInfoDto} object to filter.
     * @param documentPageRangeFilter
     *            The page range filter. For example: '1,2,5-6'. The page
     *            numbers in page range filter refer to one-based page numbers
     *            of the integrated {@link InboxInfoDto} document.
     * @return A new {@link InboxInfoDto} with a {@link InboxJobRange} list that
     *         correspond to the filtered ranges. Or the unchanged input object
     *         in case of a full page range filter: see
     *         {@link InboxJobRange#FULL_PAGE_RANGE}
     */
    InboxInfoDto filterInboxInfoPages(InboxInfoDto jobs,
            String documentPageRangeFilter);

    /**
     * Replaces the {@link InboxJobRange} list (pages) in the
     * {@link InboxInfoDto} object with information from the
     * {@link ProxyPrintJobChunkRange} list.
     *
     * @param inboxInfo
     *            The {@link InboxInfoDto}.
     * @param chunkRanges
     *            The {@link ProxyPrintJobChunkRange} list.
     * @return The original (replaced) pages.
     */
    ArrayList<InboxJobRange> replaceInboxInfoPages(InboxInfoDto inboxInfo,
            List<ProxyPrintJobChunkRange> chunkRanges);

    /**
     * Starts task to create a shadow EcoPrint PDF file.
     *
     * @param homedir
     *            The SafePages directory.
     * @param pdfIn
     *            The PDF input file.
     * @param uuid
     *            The {@link UUID} of the task.
     */
    void startEcoPrintPdfTask(String homedir, File pdfIn, UUID uuid);

    /**
     * Lazy starts EcoPrint PDF conversion for the jobs that are referred to in
     * the pages of the {@link InboxInfoDto}.
     * <p>
     * See {@link InboxInfoDto#getPages()}.
     * </p>
     *
     * @param homedir
     *            The SafePages directory.
     * @param inboxInfo
     *            The {@link InboxInfoDto} with the job pages to be Eco Printed.
     * @return The number of tasks that are busy or lazy started. If {@code 0}
     *         (zero), all shadow EcoPrint files are present.
     */
    int lazyStartEcoPrintPdfTasks(String homedir, InboxInfoDto inboxInfo);

    /**
     * Creates the EcoPrint shadow file path for a PDF file.
     *
     * @param pdfPath
     *            The path of the main PDF file.
     * @return The file path of the PDF EcoPrint shadow.
     */
    String createEcoPdfShadowPath(String pdfPath);

    /**
     * Gets the most recent modified time of a user's time print-in file (job).
     *
     * @param userId
     *            The user ID.
     * @return The time in milliseconds or {@code null} when no jobs are
     *         present.
     * @throws IOException
     *             When file system error.
     */
    Long getLastPrintInTime(String userId) throws IOException;

}
