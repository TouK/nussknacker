import { genSpel, parseSpel } from "./spelUtils";

/** Collapse all whitespace sequences to a single space and trim. */
function normalize(s: string): string {
    return s.replace(/\s+/g, " ").trim();
}

/**
 * Round-trip test cases: [input, expectedNormalizedOutput]
 *
 * The expected output is the expression with:
 *  - outer parentheses stripped from each condition part
 *  - whitespace normalized to single spaces
 *
 * Cases marked (*) are similar to another case in the list —
 * consider keeping only one.
 */
const ROUND_TRIP_CASES: [string, string][] = [
    // ── simple comparisons ────────────────────────────────────────────────────
    ["#transactionVolumeStats.count > 5", "#transactionVolumeStats.count > 5"],

    // (*) same pattern as above, differs only in field/value names
    ["#transactionVolumeStats.totalAmount > 20000", "#transactionVolumeStats.totalAmount > 20000"],

    // arithmetic in left operand
    ["#distanceInKm / #timeInHours > 900", "#distanceInKm / #timeInHours > 900"],

    // string literal on right side
    ['#customerData.billType == "STANDARD"', '#customerData.billType == "STANDARD"'],

    // long string value with special characters (slashes, dots, dashes)
    [
        '#messageSchema == "iglu:com.snowplowanalytics.snowplow/payload_data/jsonschema/1-0-4"',
        '#messageSchema == "iglu:com.snowplowanalytics.snowplow/payload_data/jsonschema/1-0-4"',
    ],

    // ── outer parentheses stripped ────────────────────────────────────────────
    ["(#timeInHours == 0 ) || (#distanceInKm == 0)", "#timeInHours == 0 || #distanceInKm == 0"],

    // ── whitespace normalization ──────────────────────────────────────────────

    // no space before || and extra spaces inside operands
    [
        "#userStats.sum > 10 * #customer.daily_amount_transactions_limit ||#userStats.count >  #customer.daily_transactions_limit",
        "#userStats.sum > 10 * #customer.daily_amount_transactions_limit || #userStats.count > #customer.daily_transactions_limit",
    ],

    // no spaces around && and multiple spaces between conditions
    [
        "#anonymousUserId != null &&#product.slug != null &&#product.viewTimestamp != null",
        "#anonymousUserId != null && #product.slug != null && #product.viewTimestamp != null",
    ],

    // extreme whitespace inside a single condition
    [
        "#NUMERIC.abs(    #statistics.get(#ROW.measurementName).average    - #statistics.get(#ROW.measurementName).previousAverage)> #ROW.alertThreshold    * #statistics.get(#ROW.measurementName).standardDeviation",
        "#NUMERIC.abs(#statistics.get(#ROW.measurementName).average - #statistics.get(#ROW.measurementName).previousAverage) > #ROW.alertThreshold * #statistics.get(#ROW.measurementName).standardDeviation",
    ],

    [
        "#statistics.get(#ROW.measurementName).max> #statistics.get(#ROW.measurementName).average    + #ROW.alertThreshold        * #statistics.get(#ROW.measurementName).standardDeviation",
        "#statistics.get(#ROW.measurementName).max > #statistics.get(#ROW.measurementName).average + #ROW.alertThreshold * #statistics.get(#ROW.measurementName).standardDeviation",
    ],

    // ── method calls with inner parentheses ──────────────────────────────────

    // nested function call with parens in left operand
    [
        "#NUMERIC.abs(#advancedTransactionStats.avgAmount - #advancedTransactionStats.totalAmount / #advancedTransactionStats.count) < 100",
        "#NUMERIC.abs(#advancedTransactionStats.avgAmount - #advancedTransactionStats.totalAmount / #advancedTransactionStats.count) < 100",
    ],

    // method calls with arguments in both conditions
    [
        '#DATE.toInstant(#input.eventDate).atZone("UTC").hour >= 1 && #DATE.toInstant(#input.eventDate).atZone("UTC").hour <= 5',
        '#DATE.toInstant(#input.eventDate).atZone("UTC").hour >= 1 && #DATE.toInstant(#input.eventDate).atZone("UTC").hour <= 5',
    ],

    // ── multiple conditions ───────────────────────────────────────────────────

    // (*) simpler && case (similar to DATE case above)
    [
        "#advancedTransactionStats.count >= 5 && #advancedTransactionStats.maxAmount > 2000",
        "#advancedTransactionStats.count >= 5 && #advancedTransactionStats.maxAmount > 2000",
    ],

    // mixed >= and < in same expression (tests that < doesn't greedily match <=)
    [
        "#aggregate.durationSum >= #ROW.durationAggFrom && #aggregate.durationSum < #ROW.durationAggTo",
        "#aggregate.durationSum >= #ROW.durationAggFrom && #aggregate.durationSum < #ROW.durationAggTo",
    ],

    // three conditions
    [
        '#snowplowEvent["e"] == "se" && #snowplowEvent["se_ac"] == "product-view" && #snowplowEvent["se_la"] != null',
        '#snowplowEvent["e"] == "se" && #snowplowEvent["se_ac"] == "product-view" && #snowplowEvent["se_la"] != null',
    ],

    ["#input.location.x == 1 && #input.location.y == 3", "#input.location.x == 1 && #input.location.y == 3"],

    // second condition is a plain method call — treated as "is true"
    [
        '#input.type == "SIMPLE" && #input.data.idHex.startsWith("340225c835a558aa")',
        '#input.type == "SIMPLE" && #input.data.idHex.startsWith("340225c835a558aa")',
    ],

    // ── SpEL collection operators (treated as opaque "is true" conditions) ───

    // collection selection ?[...]
    ["#shopAndOnlineViews.?[#this.isPremium].size > 0", "#shopAndOnlineViews.?[#this.isPremium].size > 0"],

    // first-match ^[...] — whole expression is a boolean, no comparison operator
    ["#products.^[#this.id == #productViewEvent.productId]", "#products.^[#this.id == #productViewEvent.productId]"],
];

describe("spelUtils", () => {
    describe("parseSpel + genSpel round-trip", () => {
        it.each(ROUND_TRIP_CASES)("round-trips: %s", (input, expectedNormalized) => {
            const parsed = parseSpel(input);

            expect(parsed).not.toBeNull();

            const generated = genSpel(parsed!.conditions, parsed!.combinator);
            expect(normalize(generated)).toBe(expectedNormalized);
        });
    });

    describe("outer parentheses handling", () => {
        it("single condition wrapped in parens parses identically to unwrapped", () => {
            const wrapped = parseSpel("(#isOutOfBorder == true)");
            const plain = parseSpel("#isOutOfBorder == true");

            expect(wrapped).not.toBeNull();
            expect(wrapped).toEqual(plain);
        });

        it("each part wrapped in parens parses identically to unwrapped", () => {
            const wrapped = parseSpel('(#isOutOfBorder == true) || ("123123".toInteger >= 1231)');
            const plain = parseSpel('#isOutOfBorder == true || "123123".toInteger >= 1231');

            expect(wrapped).not.toBeNull();
            expect(wrapped).toEqual(plain);
        });

        it("doubly-nested outer parens are stripped", () => {
            const double = parseSpel("((#x == 1))");
            const plain = parseSpel("#x == 1");

            expect(double).not.toBeNull();
            expect(double).toEqual(plain);
        });
    });

    describe("empty expression returns null", () => {
        it("returns null for empty string", () => {
            expect(parseSpel("")).toBeNull();
        });

        it("returns null for whitespace-only string", () => {
            expect(parseSpel("   ")).toBeNull();
        });
    });
});

// ─── genSpel ─────────────────────────────────────────────────────────────────

describe("genSpel", () => {
    it("single condition — is true", () => {
        expect(genSpel([{ left: "#x.active", operator: "is true", right: "" }], "&&")).toBe("#x.active");
    });

    it("single condition — is false", () => {
        expect(genSpel([{ left: "#x.active", operator: "is false", right: "" }], "&&")).toBe("!#x.active");
    });

    it("single condition — == null", () => {
        expect(genSpel([{ left: "#x", operator: "== null", right: "" }], "&&")).toBe("#x == null");
    });

    it("single condition — != null", () => {
        expect(genSpel([{ left: "#x", operator: "!= null", right: "" }], "&&")).toBe("#x != null");
    });

    it("two conditions joined with &&", () => {
        expect(
            genSpel(
                [
                    { left: "#a", operator: ">", right: "0" },
                    { left: "#b", operator: "== null", right: "" },
                ],
                "&&",
            ),
        ).toBe("#a > 0\n&& #b == null");
    });

    it("two conditions joined with ||", () => {
        expect(
            genSpel(
                [
                    { left: "#a", operator: "is true", right: "" },
                    { left: "#b", operator: "is false", right: "" },
                ],
                "||",
            ),
        ).toBe("#a\n|| !#b");
    });

    it("empty conditions array returns empty string", () => {
        expect(genSpel([], "&&")).toBe("");
    });

    it("matches generates left matches 'pattern'", () => {
        expect(genSpel([{ left: "#input.name", operator: "matches", right: "[A-Z]{3}.*" }], "&&")).toBe("#input.name matches '[A-Z]{3}.*'");
    });

    it("not matches generates !(left matches 'pattern')", () => {
        expect(genSpel([{ left: "#input.code", operator: "not matches", right: "\\d+" }], "&&")).toBe("!(#input.code matches '\\d+')");
    });
});

// ─── matches operator ─────────────────────────────────────────────────────────

describe("matches operator", () => {
    it("parses: left matches 'pattern'", () => {
        expect(parseSpel("#input.name matches '[A-Z]{3}.*'")?.conditions[0]).toEqual({
            left: "#input.name",
            operator: "matches",
            right: "[A-Z]{3}.*",
        });
    });

    it("parses: left matches with double-quoted pattern", () => {
        expect(parseSpel('#input.name matches "[A-Z]{3}.*"')?.conditions[0]).toEqual({
            left: "#input.name",
            operator: "matches",
            right: "[A-Z]{3}.*",
        });
    });

    it("parses: !(left matches 'pattern') as not matches", () => {
        expect(parseSpel("!(#input.code matches '\\d+')")?.conditions[0]).toEqual({
            left: "#input.code",
            operator: "not matches",
            right: "\\d+",
        });
    });

    it("round-trips matches", () => {
        const input = "#input.name matches '[A-Z]{3}.*'";
        const parsed = parseSpel(input);
        expect(parsed).not.toBeNull();
        expect(normalize(genSpel(parsed!.conditions, parsed!.combinator))).toBe(input);
    });

    it("round-trips not matches", () => {
        const input = "!(#input.code matches '\\d+')";
        const parsed = parseSpel(input);
        expect(parsed).not.toBeNull();
        expect(normalize(genSpel(parsed!.conditions, parsed!.combinator))).toBe(input);
    });

    it("matches combined with && condition", () => {
        const input = "#input.active == true && #input.name matches '[A-Z].*'";
        const parsed = parseSpel(input);
        expect(parsed).not.toBeNull();
        expect(parsed!.conditions).toHaveLength(2);
        expect(parsed!.conditions[1]).toEqual({ left: "#input.name", operator: "matches", right: "[A-Z].*" });
        expect(normalize(genSpel(parsed!.conditions, parsed!.combinator))).toBe(input);
    });
});
