/**
 * Copyright (C) 2009-2013 FoundationDB, LLC
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

package com.foundationdb.server;

import com.foundationdb.junit.NamedParameterizedRunner;
import com.foundationdb.junit.Parameterization;
import com.foundationdb.server.collation.AkCollatorFactory;
import com.foundationdb.server.types.AkType;
import com.foundationdb.server.types.ToObjectValueTarget;
import com.foundationdb.server.types.ValueSource;
import com.foundationdb.server.types.ValueTarget;
import com.foundationdb.server.types.typestests.ConversionSuite;
import com.foundationdb.server.types.typestests.ConversionTestBase;
import com.foundationdb.server.types.typestests.SimpleLinkedConversion;
import com.foundationdb.server.types.typestests.TestCase;
import com.persistit.Key;
import com.persistit.Persistit;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.Set;

public final class PersistitKeyConversionTest extends ConversionTestBase {

    @NamedParameterizedRunner.TestParameters
    public static Collection<Parameterization> params() {
        ConversionSuite<?> suite = ConversionSuite.build(new KeyConversionPair()).suite();

        Collection<Parameterization> params = params(suite);

        // Persistit truncates trailing 0s from BigDecimals, which reduces their precision.
        // This is wrong, but that's what we have to deal with. So we'll ignore all such test cases.
        ToObjectValueTarget valueTarget = new ToObjectValueTarget();
        for (Iterator<Parameterization> iterator = params.iterator(); iterator.hasNext(); ) {
            Parameterization param = iterator.next();
            ConversionSuite<?> paramSuite = (ConversionSuite<?>) param.getArgsAsList().get(0);
            int indexWithinSuite = (Integer) param.getArgsAsList().get(1);
            TestCase<?> testCase = paramSuite.testCaseAt(indexWithinSuite);
            if (testCase.type().equals(AkType.DECIMAL)) {
                valueTarget.expectType(AkType.DECIMAL);
                testCase.put(valueTarget);
                BigDecimal expected = (BigDecimal) valueTarget.lastConvertedValue();
                String asString = expected.toPlainString();
                if (asString.contains(".") && asString.charAt(asString.length() - 1) == '0') {
                    iterator.remove();
                }
            }
        }
        return params;
    }

    public PersistitKeyConversionTest(ConversionSuite<?> suite, int indexWithinSuite) {
        super(suite, indexWithinSuite);
    }

    private static final class KeyConversionPair extends SimpleLinkedConversion {
        @Override
        public ValueSource linkedSource() {
            return source;
        }

        @Override
        public ValueTarget linkedTarget() {
            return target;
        }

        @Override
        public Set<? extends AkType> unsupportedTypes() {
            return EnumSet.of(AkType.INTERVAL_MILLIS, AkType.INTERVAL_MONTH);
        }

        @Override
        public void setUp(TestCase<?> testCase) {
            key.clear();
            target.attach(key);
            target.expectingType(testCase.type(), AkCollatorFactory.getAkCollator(testCase.charset()));
            source.attach(key, 0, testCase.type());
        }

        private final Key key = new Key((Persistit)null);
        private final PersistitKeyValueTarget target = new PersistitKeyValueTarget();
        private final PersistitKeyValueSource source = new PersistitKeyValueSource();
    }
}