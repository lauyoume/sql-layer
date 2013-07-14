/**
 * Copyright (C) 2009-2013 Akiban Technologies, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.akiban.server.test.it.sort;

import com.akiban.qp.operator.API;
import com.akiban.qp.operator.Cursor;
import com.akiban.qp.operator.QueryBindings;
import com.akiban.qp.operator.QueryContext;
import com.akiban.qp.operator.RowCursor;
import com.akiban.qp.operator.RowsBuilder;
import com.akiban.qp.operator.TestOperator;
import com.akiban.qp.persistitadapter.PersistitAdapter;
import com.akiban.qp.persistitadapter.Sorter;
import com.akiban.qp.row.Row;
import com.akiban.qp.rowtype.RowType;
import com.akiban.qp.rowtype.Schema;
import com.akiban.qp.util.SchemaCache;
import com.akiban.server.test.it.ITBase;
import com.akiban.server.types.AkType;
import com.akiban.server.types.NullValueSource;
import com.akiban.server.types.ValueSource;
import com.akiban.server.types3.TInstance;
import com.akiban.server.types3.mcompat.mtypes.MString;
import com.akiban.util.tap.InOutTap;
import com.akiban.util.tap.Tap;
import com.persistit.exception.PersistitException;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.akiban.server.test.ExpressionGenerators.field;
import static org.junit.Assert.assertEquals;

public abstract class SorterITBase extends ITBase {
    private static final Boolean ASC = Boolean.TRUE;
    private static final Boolean DESC = Boolean.FALSE;
    private static InOutTap TEST_TAP = Tap.createTimer("test");

    private static final List<String[]> SINGLE_UNORDERED  = list("beta", "alpha", "gamma", "delta");
    private static final List<String[]> SINGLE_ASCENDING  = list("alpha", "beta", "delta", "gamma");
    private static final List<String[]> SINGLE_DESCENDING = list("gamma", "delta", "beta", "alpha");

    private static final List<String[]> MULTI_UNORDERED = list("a,b", "a,a", "b,b", "b,a");
    private static final List<String[]> MULTI_ASC_ASC   = list("a,a", "a,b", "b,a", "b,b");
    private static final List<String[]> MULTI_ASC_DESC  = list("a,b", "a,a", "b,b", "b,a");
    private static final List<String[]> MULTI_DESC_DESC = list("b,b", "b,a", "a,b", "a,a");
    private static final List<String[]> MULTI_DESC_ASC  = list("b,a", "b,b", "a,a", "a,b");


    public abstract Sorter createSorter(QueryContext context,
                                        QueryBindings bindings,
                                        Cursor input,
                                        RowType rowType,
                                        API.Ordering ordering,
                                        API.SortOption sortOption,
                                        InOutTap loadTap);


    @Test
    public void singleFieldAscending() {
        runTest(API.SortOption.PRESERVE_DUPLICATES, SINGLE_UNORDERED, SINGLE_ASCENDING, ASC);
    }

    @Test
    public void singleFieldDescending() {
        runTest(API.SortOption.PRESERVE_DUPLICATES, SINGLE_UNORDERED, SINGLE_DESCENDING, DESC);
    }

    @Test
    public void multiFieldAscAsc() {
        runTest(API.SortOption.PRESERVE_DUPLICATES, MULTI_UNORDERED, MULTI_ASC_ASC, ASC, ASC);
    }

    @Test
    public void multiFieldAscDesc() {
        runTest(API.SortOption.PRESERVE_DUPLICATES, MULTI_UNORDERED, MULTI_ASC_DESC, ASC, DESC);
    }

    @Test
    public void multiFieldDescDesc() {
        runTest(API.SortOption.PRESERVE_DUPLICATES, MULTI_UNORDERED, MULTI_DESC_DESC, DESC, DESC);
    }

    @Test
    public void multiFieldDescAsc() {
        runTest(API.SortOption.PRESERVE_DUPLICATES, MULTI_UNORDERED, MULTI_DESC_ASC, DESC, ASC);
    }

    @Test
    public void firstRowNullAscending() {
        List<String[]> input = new ArrayList<>();
        input.add(new String[]{null});
        input.addAll(SINGLE_UNORDERED);
        List<String[]> expected = new ArrayList<>();
        expected.add(new String[]{null});
        expected.addAll(SINGLE_ASCENDING);
        runTest(API.SortOption.PRESERVE_DUPLICATES, input, expected, ASC);
    }


    protected static List<String[]> list(String... values) {
        List<String[]> rows = new ArrayList<>();
        for(String s : values) {
            String[] fields = s.split(",");
            rows.add(fields);
        }
        return rows;
    }

    protected static RowsBuilder createBuilder(List<String[]> values) {
        TInstance[] tinsts = new TInstance[values.get(0).length];
        Arrays.fill(tinsts, MString.varchar());
        RowsBuilder rowsBuilder = new RowsBuilder(tinsts);
        for(String[] s : values) {
            rowsBuilder.row(s);
        }
        return rowsBuilder;
    }

    protected void runTest(API.SortOption sortOption, List<String[]> input, List<String[]> expected, boolean... fieldOrdering) {
        assertEquals("input = expected size", input.size(), expected.size());

        RowsBuilder inputRows = createBuilder(input);
        Schema schema = SchemaCache.globalSchema(ddl().getAIS(session()));
        PersistitAdapter adapter = persistitAdapter(schema);
        TestOperator inputOperator = new TestOperator(inputRows);

        QueryContext context = queryContext(adapter);
        QueryBindings bindings = context.createBindings();
        Cursor inputCursor = API.cursor(inputOperator, context, bindings);
        inputCursor.openTopLevel();

        API.Ordering ordering = API.ordering();
        for(int i = 0; i < fieldOrdering.length; ++i) {
            ordering.append(field(inputOperator.rowType(), i), fieldOrdering[i]);
        }

        Sorter sorter = createSorter(context, bindings, inputCursor, inputOperator.rowType(), ordering, sortOption, TEST_TAP);
        RowCursor sortedCursor = sorter.sort();

        Row[] expectedRows = createBuilder(expected).rows().toArray(new Row[expected.size()]);
        compareRows(expectedRows, sortedCursor);
    }
}