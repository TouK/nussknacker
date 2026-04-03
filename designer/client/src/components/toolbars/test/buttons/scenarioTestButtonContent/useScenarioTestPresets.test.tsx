import { configureStore } from "@reduxjs/toolkit";
import { renderHook } from "@testing-library/react";
import React from "react";
import { Provider } from "react-redux";

import { RUN_ALL, useScenarioTestPresets } from "./useScenarioTestPresets";

jest.mock("../../../../../assets/img/toolbarButtons/test.svg", () => () => null);

const buildState = (testCaseList: { id: string; name: string }[], multipleEnabled = true) => ({
    graphReducer: {
        present: {
            scenario: {
                scenarioGraph: {
                    testCases: { list: testCaseList },
                },
            },
            testing: {
                testCasesResults: {},
                activeTestCaseId: null,
            },
        },
    },
    settings: {
        featuresSettings: {
            testCases: { multipleEnabled },
        },
    },
});

const renderPresetsHook = (state: ReturnType<typeof buildState>) => {
    const store = configureStore({ reducer: (s) => s, preloadedState: state as any, devTools: false });
    const wrapper = ({ children }) => <Provider store={store}>{children}</Provider>;
    return renderHook(() => useScenarioTestPresets(), { wrapper });
};

describe("useScenarioTestPresets", () => {
    it("does not include runAll preset when there is only one test case", () => {
        const { result } = renderPresetsHook(buildState([{ id: "1", name: "TC1" }]));
        const presetValues = result.current.presets.filter((p) => "value" in p).map((p) => (p as any).value);
        expect(presetValues).not.toContain(RUN_ALL);
    });

    it("includes runAll preset as first item when there are multiple test cases", () => {
        const { result } = renderPresetsHook(
            buildState([
                { id: "1", name: "TC1" },
                { id: "2", name: "TC2" },
            ]),
        );
        const presetValues = result.current.presets.filter((p) => "value" in p).map((p) => (p as any).value);
        expect(presetValues[0]).toBe(RUN_ALL);
    });

    it("does not include runAll preset when multipleEnabled is false", () => {
        const { result } = renderPresetsHook(
            buildState(
                [
                    { id: "1", name: "TC1" },
                    { id: "2", name: "TC2" },
                ],
                false,
            ),
        );
        const presetValues = result.current.presets.filter((p) => "value" in p).map((p) => (p as any).value);
        expect(presetValues).not.toContain(RUN_ALL);
    });
});
