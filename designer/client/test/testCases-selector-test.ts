import { hasMultipleTestCases } from "../src/reducers/selectors/testCases";

const buildState = (testCaseList: { id: string; name: string }[], multipleEnabled = true) =>
    ({
        graphReducer: {
            present: {
                scenario: {
                    scenarioGraph: {
                        testCases: { list: testCaseList },
                    },
                },
            },
        },
        settings: {
            featuresSettings: {
                testCases: { multipleEnabled },
            },
        },
    } as any);

describe("hasMultipleTestCases", () => {
    it("returns false when there are no test cases", () => {
        expect(hasMultipleTestCases(buildState([]))).toBe(false);
    });

    it("returns false when there is exactly one test case", () => {
        expect(hasMultipleTestCases(buildState([{ id: "1", name: "TC1" }]))).toBe(false);
    });

    it("returns true when there are more than one test cases", () => {
        expect(
            hasMultipleTestCases(
                buildState([
                    { id: "1", name: "TC1" },
                    { id: "2", name: "TC2" },
                ]),
            ),
        ).toBe(true);
    });

    it("returns false when multipleEnabled is false even if list has multiple items", () => {
        expect(
            hasMultipleTestCases(
                buildState(
                    [
                        { id: "1", name: "TC1" },
                        { id: "2", name: "TC2" },
                    ],
                    false,
                ),
            ),
        ).toBe(false);
    });
});
