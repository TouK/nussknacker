import { parseRecords } from "./parseRecords";

const SOURCE_ID = "test-source";

describe("parseRecords", () => {
    describe("single compact JSON object", () => {
        it("wraps raw event in input field", () => {
            const { rows, errorCount } = parseRecords('{"value": 1}', SOURCE_ID);
            expect(errorCount).toBe(0);
            expect(rows).toHaveLength(1);
            expect(JSON.parse(rows[0].variables)).toEqual({ input: { value: 1 } });
        });

        it("passes through object that already has input key", () => {
            const { rows, errorCount } = parseRecords('{"input": {"value": 1}}', SOURCE_ID);
            expect(errorCount).toBe(0);
            expect(rows).toHaveLength(1);
            expect(JSON.parse(rows[0].variables)).toEqual({ input: { value: 1 } });
        });

        it("passes through object with both input and inputMeta", () => {
            const { rows } = parseRecords('{"input": {"value": 1}, "inputMeta": {"offset": 0}}', SOURCE_ID);
            expect(JSON.parse(rows[0].variables)).toEqual({ input: { value: 1 }, inputMeta: { offset: 0 } });
        });
    });

    describe("pretty-printed single JSON object", () => {
        it("wraps multi-line raw event", () => {
            const text = JSON.stringify({ value: 1 }, null, 2);
            const { rows, errorCount } = parseRecords(text, SOURCE_ID);
            expect(errorCount).toBe(0);
            expect(rows).toHaveLength(1);
            expect(JSON.parse(rows[0].variables)).toEqual({ input: { value: 1 } });
        });

        it("passes through multi-line NK format object", () => {
            const text = JSON.stringify({ input: { value: 1 }, inputMeta: { offset: 5 } }, null, 2);
            const { rows } = parseRecords(text, SOURCE_ID);
            expect(JSON.parse(rows[0].variables)).toEqual({ input: { value: 1 }, inputMeta: { offset: 5 } });
        });
    });

    describe("JSON array", () => {
        it("splits array of raw events into separate records", () => {
            const { rows, errorCount } = parseRecords('[{"value": 1}, {"value": 2}]', SOURCE_ID);
            expect(errorCount).toBe(0);
            expect(rows).toHaveLength(2);
            expect(JSON.parse(rows[0].variables)).toEqual({ input: { value: 1 } });
            expect(JSON.parse(rows[1].variables)).toEqual({ input: { value: 2 } });
        });

        it("splits array of NK format objects into separate records", () => {
            const text = JSON.stringify([{ input: { value: 1 } }, { input: { value: 2 } }]);
            const { rows, errorCount } = parseRecords(text, SOURCE_ID);
            expect(errorCount).toBe(0);
            expect(rows).toHaveLength(2);
            expect(JSON.parse(rows[0].variables)).toEqual({ input: { value: 1 } });
        });

        it("splits pretty-printed array into separate records", () => {
            const text = JSON.stringify([{ input: { value: 1 } }, { input: { value: 2 } }], null, 2);
            const { rows } = parseRecords(text, SOURCE_ID);
            expect(rows).toHaveLength(2);
        });
    });

    describe("NDJSON (one compact object per line)", () => {
        it("parses multiple lines as separate records", () => {
            const text = '{"value": 1}\n{"value": 2}\n{"value": 3}';
            const { rows, errorCount } = parseRecords(text, SOURCE_ID);
            expect(errorCount).toBe(0);
            expect(rows).toHaveLength(3);
            expect(JSON.parse(rows[0].variables)).toEqual({ input: { value: 1 } });
            expect(JSON.parse(rows[2].variables)).toEqual({ input: { value: 3 } });
        });

        it("skips blank lines", () => {
            const text = '{"value": 1}\n\n{"value": 2}';
            const { rows } = parseRecords(text, SOURCE_ID);
            expect(rows).toHaveLength(2);
        });

        it("counts invalid lines as errors", () => {
            const text = '{"value": 1}\nnot-json\n{"value": 2}';
            const { rows, errorCount } = parseRecords(text, SOURCE_ID);
            expect(errorCount).toBe(1);
            expect(rows).toHaveLength(2);
        });
    });

    describe("defaultVariables template", () => {
        const defaultVariables = JSON.stringify({ input: {}, inputMeta: { partition: 0 } });

        it("wraps raw event using template fields", () => {
            const { rows } = parseRecords('{"value": 1}', SOURCE_ID, defaultVariables);
            expect(JSON.parse(rows[0].variables)).toEqual({ input: { value: 1 }, inputMeta: { partition: 0 } });
        });

        it("fills missing inputMeta when object has input but no inputMeta", () => {
            const { rows } = parseRecords('{"input": {"value": 1}}', SOURCE_ID, defaultVariables);
            expect(JSON.parse(rows[0].variables)).toEqual({ input: { value: 1 }, inputMeta: { partition: 0 } });
        });

        it("preserves provided inputMeta over template default", () => {
            const { rows } = parseRecords('{"input": {"value": 1}, "inputMeta": {"partition": 99}}', SOURCE_ID, defaultVariables);
            expect(JSON.parse(rows[0].variables)).toEqual({ input: { value: 1 }, inputMeta: { partition: 99 } });
        });

        it("applies template to each item in array", () => {
            const { rows } = parseRecords('[{"value": 1}, {"value": 2}]', SOURCE_ID, defaultVariables);
            expect(JSON.parse(rows[0].variables)).toEqual({ input: { value: 1 }, inputMeta: { partition: 0 } });
            expect(JSON.parse(rows[1].variables)).toEqual({ input: { value: 2 }, inputMeta: { partition: 0 } });
        });

        it("ignores invalid defaultVariables", () => {
            const { rows } = parseRecords('{"value": 1}', SOURCE_ID, "not-json");
            expect(JSON.parse(rows[0].variables)).toEqual({ input: { value: 1 } });
        });
    });

    describe("sourceId", () => {
        it("sets sourceId on each record", () => {
            const { rows } = parseRecords('[{"value": 1}, {"value": 2}]', SOURCE_ID);
            rows.forEach((r) => expect(r.sourceId).toBe(SOURCE_ID));
        });
    });

    describe("edge cases", () => {
        it("returns empty rows for empty input", () => {
            const { rows, errorCount } = parseRecords("", SOURCE_ID);
            expect(rows).toHaveLength(0);
            expect(errorCount).toBe(0);
        });

        it("returns empty rows for whitespace-only input", () => {
            const { rows } = parseRecords("   \n  ", SOURCE_ID);
            expect(rows).toHaveLength(0);
        });

        it("returns error for entirely invalid input", () => {
            const { rows, errorCount } = parseRecords("not json at all", SOURCE_ID);
            expect(rows).toHaveLength(0);
            expect(errorCount).toBe(1);
        });
    });
});
