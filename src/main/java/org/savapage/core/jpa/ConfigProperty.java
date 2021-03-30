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
package org.savapage.core.jpa;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.TableGenerator;

/**
 *
 * @author Rijk Ravestein
 *
 */
@Entity
@javax.persistence.Table(name = ConfigProperty.TABLE_NAME)
public class ConfigProperty extends org.savapage.core.jpa.Entity {

    public static final String TABLE_NAME = "tbl_config";

    /** */
    public static final int COL_VALUE_NAME = 100;
    /** */
    public static final int COL_VALUE_LENGTH = 1000;
    /** */
    public static final int COL_CREATED_BY_LENGTH = 50;
    /** */
    public static final int COL_MODIFIED_BY_LENGTH = 50;

    @Id
    @Column(name = "config_id")
    @TableGenerator(name = "configPropGen", table = Sequence.TABLE_NAME,
            pkColumnName = Sequence.COL_SEQUENCE_NAME,
            valueColumnName = Sequence.COL_SEQUENCE_NEXT_VALUE,
            pkColumnValue = TABLE_NAME, allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.TABLE,
            generator = "configPropGen")
    private Long id;

    @Column(name = "property_name", length = COL_VALUE_NAME, nullable = false,
            insertable = true, updatable = false)
    private String propertyName;

    @Column(name = "property_value", length = COL_VALUE_LENGTH, nullable = true,
            insertable = true, updatable = true)
    private String value;

    @Column(name = "created_date", nullable = false, insertable = true,
            updatable = true)
    private Date createdDate;

    @Column(name = "created_by", length = COL_CREATED_BY_LENGTH,
            nullable = false, insertable = true, updatable = true)
    private String createdBy;

    @Column(name = "modified_date", nullable = false, insertable = true,
            updatable = true)
    private Date modifiedDate;

    @Column(name = "modified_by", length = COL_MODIFIED_BY_LENGTH,
            nullable = false, insertable = true, updatable = true)
    private String modifiedBy;

    /**
     *
     * @return
     */
    public Long getId() {
        return id;
    }

    /**
     *
     * @param id
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     *
     * @return
     */
    public String getPropertyName() {
        return propertyName;
    }

    /**
     *
     * @param propertyName
     */
    public void setPropertyName(String propertyName) {
        this.propertyName = propertyName;
    }

    /**
     *
     * @return
     */
    public String getValue() {
        return value;
    }

    /**
     *
     * @param value
     */
    public void setValue(String value) {
        this.value = value;
    }

    /**
     *
     * @return
     */
    public Date getCreatedDate() {
        return createdDate;
    }

    /**
     *
     * @param createdDate
     */
    public void setCreatedDate(Date createdDate) {
        this.createdDate = createdDate;
    }

    /**
     *
     * @return
     */
    public String getCreatedBy() {
        return createdBy;
    }

    /**
     *
     * @param createdBy
     */
    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    /**
     *
     * @return
     */
    public Date getModifiedDate() {
        return modifiedDate;
    }

    /**
     *
     * @param modifiedDate
     */
    public void setModifiedDate(Date modifiedDate) {
        this.modifiedDate = modifiedDate;
    }

    /**
     *
     * @return
     */
    public String getModifiedBy() {
        return modifiedBy;
    }

    /**
     *
     * @param modifiedBy
     */
    public void setModifiedBy(String modifiedBy) {
        this.modifiedBy = modifiedBy;
    }

}
