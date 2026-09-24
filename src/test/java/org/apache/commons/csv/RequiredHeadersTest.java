/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.commons.csv;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests {@link CSVFormat.Builder#setRequiredHeaders(String...)}.
 */
class RequiredHeadersTest {

    /**
     * Creates a builder that reads the header from the first line of the input.
     */
    private static CSVFormat.Builder fileHeaderBuilder() {
        return CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true);
    }

    /**
     * Asserts that creating a parser for the input fails with the given missing required header message.
     */
    private static void assertMissingHeaders(final CSVFormat format, final String input, final String expectedMessage) {
        final CSVException e = assertThrows(CSVException.class, () -> format.parse(new StringReader(input)));
        assertEquals(expectedMessage, e.getMessage());
    }

    private static String firstValue(final CSVFormat format, final String input, final String name) throws IOException {
        try (CSVParser parser = format.parse(new StringReader(input))) {
            return parser.iterator().next().get(name);
        }
    }

    /** T01 (H1, H2, H3): a missing column fails when the parser is created, before any record is read. */
    @Test
    void testMissingColumnFailsBeforeFirstRecord() {
        final CSVFormat format = fileHeaderBuilder().setRequiredHeaders("id", "currency", "amount").get();
        assertMissingHeaders(format, "id,amount\n1,10\n", "Missing required header name(s) [currency] in header [id, amount]");
    }

    /** T02 (H2): all missing columns are reported together, in the declared order. */
    @Test
    void testMultipleMissingColumnsReportedInDeclaredOrder() {
        final CSVFormat format = fileHeaderBuilder().setRequiredHeaders("currency", "id", "amount", "date").get();
        assertMissingHeaders(format, "id,note\n1,x\n", "Missing required header name(s) [currency, amount, date] in header [id, note]");
    }

    /** T03 (H10): extra columns and a different column order are accepted. */
    @Test
    void testAllPresentWithExtraColumnsInAnyOrder() throws IOException {
        final CSVFormat format = fileHeaderBuilder().setRequiredHeaders("id", "currency", "amount").get();
        assertEquals("CNY", firstValue(format, "amount,note,currency,id\n10,x,CNY,1\n", "currency"));
    }

    /** T04 (H4): an explicit header given to the builder is checked too. */
    @Test
    void testExplicitHeaderMissingColumn() {
        final CSVFormat format = CSVFormat.DEFAULT.builder().setHeader("id", "amount").setRequiredHeaders("id", "currency", "amount").get();
        assertMissingHeaders(format, "1,10\n", "Missing required header name(s) [currency] in header [id, amount]");
    }

    /** T05 (H4): with an explicit header and a skipped first line, the explicit header is checked, not the skipped line. */
    @Test
    void testExplicitHeaderCheckedEvenIfSkippedLineHasColumn() {
        final CSVFormat format = CSVFormat.DEFAULT.builder().setHeader("id", "amount").setSkipHeaderRecord(true).setRequiredHeaders("currency").get();
        assertMissingHeaders(format, "id,currency,amount\n1,CNY,10\n", "Missing required header name(s) [currency] in header [id, amount]");
    }

    /** T06 (H5): with ignoreHeaderCase, names match regardless of case. */
    @Test
    void testIgnoreHeaderCaseMatchesAnyCase() throws IOException {
        final CSVFormat format = fileHeaderBuilder().setIgnoreHeaderCase(true).setRequiredHeaders("id", "currency", "amount").get();
        assertEquals("CNY", firstValue(format, "ID,Currency,AMOUNT\n1,CNY,10\n", "currency"));
    }

    /** T07 (H5): by default, names are matched case-sensitively. */
    @Test
    void testCaseSensitiveByDefault() {
        final CSVFormat format = fileHeaderBuilder().setRequiredHeaders("id", "currency", "amount").get();
        assertMissingHeaders(format, "ID,Currency,amount\n1,CNY,10\n", "Missing required header name(s) [id, currency] in header [ID, Currency, amount]");
    }

    /** T08 (H7): empty input has no header, so every required column is missing. */
    @Test
    void testEmptyInputReportsAllMissing() {
        final CSVFormat format = fileHeaderBuilder().setRequiredHeaders("id", "currency", "amount").get();
        assertMissingHeaders(format, "", "Missing required header name(s) [id, currency, amount] in header []");
    }

    /** T09 (H6): required headers without any header configuration is a configuration error at build time. */
    @Test
    void testRequiredHeadersWithoutHeaderFailsAtBuild() {
        assertThrows(IllegalArgumentException.class, () -> CSVFormat.DEFAULT.builder().setRequiredHeaders("id").get());
    }

    /** T10 (H9): a null required name is rejected at build time. */
    @Test
    void testNullRequiredNameFailsAtBuild() {
        assertThrows(IllegalArgumentException.class, () -> fileHeaderBuilder().setRequiredHeaders("id", null).get());
    }

    /** T11 (H9): an empty or blank required name is rejected at build time. */
    @ParameterizedTest
    @ValueSource(strings = { "", " " })
    void testBlankRequiredNameFailsAtBuild(final String blank) {
        assertThrows(IllegalArgumentException.class, () -> fileHeaderBuilder().setRequiredHeaders("id", blank).get());
    }

    /** T12 (H8): no arguments or null disables the check, including clearing a previous value. */
    @Test
    void testEmptyOrNullDisablesCheck() throws IOException {
        final CSVFormat clearedByEmpty = fileHeaderBuilder().setRequiredHeaders("currency").setRequiredHeaders().get();
        final CSVFormat clearedByNull = fileHeaderBuilder().setRequiredHeaders("currency").setRequiredHeaders((String[]) null).get();
        assertArrayEquals(new String[0], clearedByEmpty.getRequiredHeaders());
        assertArrayEquals(new String[0], clearedByNull.getRequiredHeaders());
        assertEquals("1", firstValue(clearedByEmpty, "id\n1\n", "id"));
        assertEquals("1", firstValue(clearedByNull, "id\n1\n", "id"));
    }

    /** T13 (H8): the builder and the getter both make defensive copies. */
    @Test
    void testGetterReturnsDefensiveCopy() {
        final String[] input = { "id", "currency" };
        final CSVFormat format = fileHeaderBuilder().setRequiredHeaders(input).get();
        input[0] = "changed";
        assertArrayEquals(new String[] { "id", "currency" }, format.getRequiredHeaders());
        format.getRequiredHeaders()[1] = "changed";
        assertArrayEquals(new String[] { "id", "currency" }, format.getRequiredHeaders());
    }

    /** T14 (H11): builder(), equals() and hashCode() take required headers into account. */
    @Test
    void testCopyAndEquality() {
        final CSVFormat format = fileHeaderBuilder().setRequiredHeaders("currency").get();
        final CSVFormat copy = format.builder().get();
        assertArrayEquals(new String[] { "currency" }, copy.getRequiredHeaders());
        assertEquals(format, copy);
        assertEquals(format.hashCode(), copy.hashCode());
        assertNotEquals(fileHeaderBuilder().get(), format);
    }

    /** T15 (H12): with trim, surrounding spaces in the header do not cause a mismatch. */
    @Test
    void testTrimmedHeaderSatisfiesRequirement() throws IOException {
        final CSVFormat format = fileHeaderBuilder().setTrim(true).setRequiredHeaders("id", "currency").get();
        assertEquals("CNY", firstValue(format, " id , currency \n1,CNY\n", "currency"));
    }

    /** T16 (H12): without trim, header names are compared exactly, spaces included. */
    @Test
    void testUntrimmedHeaderComparedExactly() {
        final CSVFormat format = fileHeaderBuilder().setRequiredHeaders("id", "currency").get();
        assertMissingHeaders(format, "id, currency\n1,CNY\n", "Missing required header name(s) [currency] in header [id,  currency]");
    }

    /** T17 (H13): printing ignores required headers. */
    @Test
    void testPrinterIgnoresRequiredHeaders() throws IOException {
        final CSVFormat format = CSVFormat.DEFAULT.builder().setHeader("id").setRequiredHeaders("currency").get();
        final StringWriter out = new StringWriter();
        try (CSVPrinter printer = format.print(out)) {
            printer.printRecord("1");
        }
        assertEquals("id\r\n1\r\n", out.toString());
    }
}
