import { extractCountParams } from "../src/common/VisualizationUrl";
import moment from "moment";

describe("validating url counts extractors", () => {
    const sampleTimestamp = 1541761164902;
    const sampleTimestampAsString = sampleTimestamp.toString();
    const sampleTimestampAsDateString = "2018-11-09T11:59:24.902+01:00";

    it("extract time from string timestamps (from)", () => {
        const params = {
            from: sampleTimestampAsString,
        };
        expect(extractCountParams(params).from.isSame(moment(sampleTimestampAsDateString))).toBe(true);
    });

    it("extract time from string timestamps (to)", () => {
        const params = {
            to: sampleTimestampAsString,
        };
        expect(extractCountParams(params).to.isSame(moment(sampleTimestampAsDateString))).toBe(true);
    });

    it("extract time from string date (from)", () => {
        const params = {
            from: sampleTimestampAsDateString,
        };
        expect(extractCountParams(params).from.isSame(moment(sampleTimestamp))).toBe(true);
    });

    it("extract time from string date (to)", () => {
        const params = {
            to: sampleTimestampAsDateString,
        };
        expect(extractCountParams(params).to.isSame(moment(sampleTimestamp))).toBe(true);
    });

    it("extract duration from string", () => {
        expect(extractCountParams({ to: "0", refresh: "1s" }).refreshIn).toBe(1);
        expect(extractCountParams({ to: "0", refresh: "1m" }).refreshIn).toBe(60);
        expect(extractCountParams({ to: "0", refresh: "1h" }).refreshIn).toBe(3600);

        expect(extractCountParams({ to: "0" }).refreshIn).toBe(false);
        expect(extractCountParams({ to: "0", refresh: "" }).refreshIn).toBe(false);
        expect(extractCountParams({ to: "0", refresh: "1000" }).refreshIn).toBe(false);
        expect(extractCountParams({ to: "0", refresh: "1d" }).refreshIn).toBe(false);
    });
});
