/*
 * This file is part of the SavaPage project <http://savapage.org>.
 * Copyright (c) 2011-2016 Datraverse B.V.
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
package org.savapage.core.dao.impl;

import java.util.List;

import javax.persistence.Query;

import org.savapage.core.dao.UserGroupMemberDao;
import org.savapage.core.jpa.User;
import org.savapage.core.jpa.UserGroup;
import org.savapage.core.jpa.UserGroupMember;

/**
 *
 * @author Rijk Ravestein
 *
 */
public final class UserGroupMemberDaoImpl
        extends GenericDaoImpl<UserGroupMember> implements UserGroupMemberDao {

    @Override
    public int deleteGroup(final Long groupId) {

        final String jpql =
                "DELETE UserGroupMember U WHERE U.group.id = :groupId";
        final Query query = getEntityManager().createQuery(jpql);
        query.setParameter("groupId", groupId);
        return query.executeUpdate();
    }

    @Override
    public long getGroupCount(final UserFilter filter) {

        final StringBuilder jpql =
                new StringBuilder(JPSQL_STRINGBUILDER_CAPACITY);

        jpql.append("SELECT COUNT(U.id) FROM UserGroupMember U");

        applyGroupFilter(jpql, filter);

        final Query query = createGroupQuery(jpql, filter);
        final Number countResult = (Number) query.getSingleResult();
        return countResult.longValue();
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<UserGroup> getGroupChunk(final UserFilter filter,
            final Integer startPosition, final Integer maxResults,
            final GroupField orderBy, final boolean sortAscending) {

        final StringBuilder jpql =
                new StringBuilder(JPSQL_STRINGBUILDER_CAPACITY);

        jpql.append("SELECT U.group FROM UserGroupMember U");

        applyGroupFilter(jpql, filter);

        //
        jpql.append(" ORDER BY ");

        if (orderBy == GroupField.GROUP_NAME) {
            jpql.append("U.group.groupName");
        } else {
            jpql.append("U.group.groupName");
        }

        if (!sortAscending) {
            jpql.append(" DESC");
        }

        //
        final Query query = createGroupQuery(jpql, filter);

        //
        if (startPosition != null) {
            query.setFirstResult(startPosition);
        }
        if (maxResults != null) {
            query.setMaxResults(maxResults);
        }

        return query.getResultList();
    }

    /**
     * Applies the list filter to the JPQL string.
     *
     * @param jpql
     *            The {@link StringBuilder} to append to.
     * @param filter
     *            The filter.
     */
    private void applyGroupFilter(final StringBuilder jpql,
            final UserFilter filter) {

        final StringBuilder where = new StringBuilder();

        int nWhere = 0;

        if (nWhere > 0) {
            where.append(" AND");
        }
        nWhere++;
        where.append(" U.user.id = :userId");

        if (nWhere > 0) {
            jpql.append(" WHERE ").append(where.toString());
        }

    }

    /**
     * Creates the List Query and sets the filter parameters.
     *
     * @param jpql
     *            The JPA query string.
     * @param filter
     *            The {@link UserFilter}.
     * @return The {@link Query}.
     */
    private Query createGroupQuery(final StringBuilder jpql,
            final UserFilter filter) {

        final Query query = getEntityManager().createQuery(jpql.toString());

        query.setParameter("userId", filter.getUserId());

        return query;
    }

    @Override
    public long getUserCount(final Long groupId) {
        final UserGroupMemberDao.GroupFilter filter =
                new UserGroupMemberDao.GroupFilter();
        filter.setGroupId(groupId);
        return getUserCount(filter);
    }

    @Override
    public long getUserCount(final GroupFilter filter) {

        final StringBuilder jpql =
                new StringBuilder(JPSQL_STRINGBUILDER_CAPACITY);

        jpql.append("SELECT COUNT(U.id) FROM UserGroupMember U");

        applyUserFilter(jpql, filter);

        final Query query = createUserQuery(jpql, filter);
        final Number countResult = (Number) query.getSingleResult();
        return countResult.longValue();

    }

    @SuppressWarnings("unchecked")
    @Override
    public List<User> getUserChunk(final GroupFilter filter,
            final Integer startPosition, final Integer maxResults,
            final UserField orderBy, final boolean sortAscending) {

        final StringBuilder jpql =
                new StringBuilder(JPSQL_STRINGBUILDER_CAPACITY);

        jpql.append("SELECT U.user FROM UserGroupMember U");

        applyUserFilter(jpql, filter);

        //
        jpql.append(" ORDER BY ");

        if (orderBy == UserField.USER_NAME) {
            jpql.append("U.user.userId");
        } else {
            jpql.append("U.user.userId");
        }

        if (!sortAscending) {
            jpql.append(" DESC");
        }

        //
        final Query query = createUserQuery(jpql, filter);

        //
        if (startPosition != null) {
            query.setFirstResult(startPosition);
        }
        if (maxResults != null) {
            query.setMaxResults(maxResults);
        }

        return query.getResultList();
    }

    /**
     * Applies the list filter to the JPQL string.
     *
     * @param jpql
     *            The {@link StringBuilder} to append to.
     * @param filter
     *            The filter.
     */
    private void applyUserFilter(final StringBuilder jpql,
            final GroupFilter filter) {

        final StringBuilder where = new StringBuilder();

        int nWhere = 0;

        if (nWhere > 0) {
            where.append(" AND");
        }
        nWhere++;
        where.append(" U.group.id = :groupId");

        if (nWhere > 0) {
            jpql.append(" WHERE ").append(where.toString());
        }

    }

    /**
     * Creates the List Query and sets the filter parameters.
     *
     * @param jpql
     *            The JPA query string.
     * @param filter
     *            The {@link GroupFilter}.
     * @return The {@link Query}.
     */
    private Query createUserQuery(final StringBuilder jpql,
            final GroupFilter filter) {

        final Query query = getEntityManager().createQuery(jpql.toString());

        query.setParameter("groupId", filter.getGroupId());

        return query;
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<UserGroupMember> getGroupMembers(final Long groupId) {

        final String jpql = "SELECT U FROM UserGroupMember U "
                + "WHERE U.group.id = :groupId " + "ORDER BY U.user.userId";

        final Query query = getEntityManager().createQuery(jpql);

        query.setParameter("groupId", groupId);

        return query.getResultList();
    }

}
