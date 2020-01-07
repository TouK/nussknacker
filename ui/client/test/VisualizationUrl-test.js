import {extractCountParams} from "../common/VisualizationUrl";
import moment from "moment";

describe("validating url counts extractors", () => {
  const sampleTimestamp = 1541761164902;
  const sampleTimestampAsString = sampleTimestamp.toString();
  const sampleTimestampAsDateString = "2018-11-09T11:59:24.902+01:00";

  it("extract time from string timestamps (from)", () => {
    const params = {
      from: sampleTimestampAsString
    };
    expect(extractCountParams(params).from.isSame(moment(sampleTimestampAsDateString))).toBe(true);
  });

  it("extract time from string timestamps (to)", () => {
    const params = {
      to: sampleTimestampAsString
    };
    expect(extractCountParams(params).to.isSame(moment(sampleTimestampAsDateString))).toBe(true);
  });

  it("extract time from string date (from)", () => {
    const params = {
      from: sampleTimestampAsDateString
    };
    expect(extractCountParams(params).from.isSame(moment(sampleTimestamp))).toBe(true);
  });

  it("extract time from string date (to)", () => {
    const params = {
      to: sampleTimestampAsDateString
    };
    expect(extractCountParams(params).to.isSame(moment(sampleTimestamp))).toBe(true);
  });
});
