/**
 * Copyright (C) 2011 Akiban Technologies Inc.
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses.
 */

package com.akiban.server.test.it.qp;

import com.akiban.qp.expression.Expression;
import com.akiban.qp.physicaloperator.ArrayBindings;
import com.akiban.qp.physicaloperator.Bindings;
import com.akiban.qp.physicaloperator.Cursor;
import com.akiban.qp.physicaloperator.PhysicalOperator;
import com.akiban.qp.row.RowBase;
import com.akiban.qp.rowtype.RowType;
import com.akiban.server.api.dml.scan.NewRow;
import com.akiban.server.error.NegativeLimitException;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static com.akiban.qp.expression.API.field;
import static com.akiban.qp.physicaloperator.API.*;
import static com.akiban.qp.physicaloperator.API.JoinType.*;

public class LimitIT extends PhysicalOperatorITBase
{
    @Before
    public void before()
    {
        super.before();
        NewRow[] dbRows = new NewRow[]{
            createNewRow(customer, 1L, "northbridge"),
            createNewRow(customer, 2L, "foundation"),
            createNewRow(customer, 4L, "highland"),
            createNewRow(customer, 5L, "matrix"),
            createNewRow(customer, 6L, "sigma"),
            createNewRow(customer, 7L, "crv"),
        };
        use(dbRows);
    }

    // Limit tests

    @Test
    public void testLimit()
    {
        PhysicalOperator plan = limit_Default(groupScan_Default(coi),
                                              3);
        Cursor cursor = cursor(plan, adapter);
        RowBase[] expected = new RowBase[]{
            row(customerRowType, 1L, "northbridge"),
            row(customerRowType, 2L, "foundation"),
            row(customerRowType, 4L, "highland"),
        };
        compareRows(expected, cursor);
    }

    @Test
    public void testSkip()
    {
        PhysicalOperator plan = limit_Default(groupScan_Default(coi),
                                              2, false, Integer.MAX_VALUE, false);
        Cursor cursor = cursor(plan, adapter);
        RowBase[] expected = new RowBase[]{
            row(customerRowType, 4L, "highland"),
            row(customerRowType, 5L, "matrix"),
            row(customerRowType, 6L, "sigma"),
            row(customerRowType, 7L, "crv"),
        };
        compareRows(expected, cursor);
    }

    @Test
    public void testSkipAndLimit()
    {
        PhysicalOperator plan = limit_Default(groupScan_Default(coi),
                                              2, false, 2, false);
        Cursor cursor = cursor(plan, adapter);
        RowBase[] expected = new RowBase[]{
            row(customerRowType, 4L, "highland"),
            row(customerRowType, 5L, "matrix"),
        };
        compareRows(expected, cursor);
    }

    @Test
    public void testSkipExhausted()
    {
        PhysicalOperator plan = limit_Default(groupScan_Default(coi),
                                              10, false, 1, false);
        Cursor cursor = cursor(plan, adapter);
        RowBase[] expected = new RowBase[]{
        };
        compareRows(expected, cursor);
    }

    @Test
    public void testLimitFromBinding()
    {
        PhysicalOperator plan = limit_Default(groupScan_Default(coi),
                                              0, false, 0, true);
        Cursor cursor = cursor(plan, adapter);
        Bindings bindings = new ArrayBindings(1);
        bindings.set(0, 2);
        RowBase[] expected = new RowBase[]{
            row(customerRowType, 1L, "northbridge"),
            row(customerRowType, 2L, "foundation"),
        };
        compareRows(expected, cursor, bindings);
    }

    @Test(expected = NegativeLimitException.class)
    public void testLimitFromBadBinding()
    {
        PhysicalOperator plan = limit_Default(groupScan_Default(coi),
                                              0, false, 0, true);
        Cursor cursor = cursor(plan, adapter);
        Bindings bindings = new ArrayBindings(1);
        bindings.set(0, -1);
        RowBase[] expected = new RowBase[]{
        };
        compareRows(expected, cursor, bindings);
    }

}