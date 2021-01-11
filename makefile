#
# This file is part of the SavaPage project <https://www.savapage.org>.
# Copyright (c) 2020 Datraverse B.V.
# Author: Rijk Ravestein.
#
# SPDX-FileCopyrightText: © 2020 Datraverse B.V. <info@datraverse.com>
# SPDX-License-Identifier: AGPL-3.0-or-later
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU Affero General Public License as
# published by the Free Software Foundation, either version 3 of the
# License, or (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU Affero General Public License for more details.
#
# You should have received a copy of the GNU Affero General Public License
# along with this program.  If not, see <https://www.gnu.org/licenses/>.
#
# For more information, please contact Datraverse B.V. at this
# address: info@datraverse.com
#

.PHONY: help
help:
	@echo Please enter a target.

.PHONY: clean
clean:
	mvn clean

# Mantis #1160
.PHONY: version-info
version-info:
	mvn antrun:run@version-info

.PHONY: package
package: version-info
	mvn package

.PHONY: repackage
repackage: clean package

.PHONY: install
install: clean version-info
	mvn install
	
# end-of-file
