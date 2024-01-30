import React from "react";
import { Provider } from "react-redux";
import configureMockStore from "redux-mock-store";
import { ProcessHistoryComponent } from "../src/components/history/ProcessHistory";
import { render, within } from "@testing-library/react";
import { NuThemeProvider } from "../src/containers/theme/nuThemeProvider";

const mockStore = configureMockStore();
jest.mock("../src/windowManager", () => ({
    useWindows: jest.fn(() => ({
        confirm: jest.fn(),
    })),
}));

jest.mock("react-i18next", () => ({
    useTranslation: () => ({
        t: (key) => key,
        i18n: { changeLanguage: () => {} },
    }),
}));

describe("ProcessHistory suite", () => {
    it("should mark latest history entry as current and other as past", () => {
        //given
        const store = mockStore({
            graphReducer: {
                history: {
                    past: {
                        scenario: {
                            scenarioGraph: {},
                            history: [processEntry(3), processEntry(2), processEntry(1)],
                        },
                    },
                    present: {
                        scenario: {
                            scenarioGraph: {},
                            history: [processEntry(3), processEntry(2), processEntry(1)],
                        },
                    },
                },
            },
        });
        //when
        const { container } = render(
            <NuThemeProvider>
                <Provider store={store}>
                    <ProcessHistoryComponent />,
                </Provider>
            </NuThemeProvider>,
        );
        //then
        const currentProcessHistoryEntry = container.getElementsByClassName("current");
        const pastHistoryEntries = container.getElementsByClassName("past");
        expect(currentProcessHistoryEntry.length).toBe(1);
        expect(pastHistoryEntries.length).toBe(2);
        expect(within(currentProcessHistoryEntry[0]).getByText(/v3/)).toBeInTheDocument();
        expect(within(pastHistoryEntries[0]).getByText(/v2/)).toBeInTheDocument();
        expect(within(pastHistoryEntries[1]).getByText(/v1/)).toBeInTheDocument();
    });

    const processEntry = (processVersionId) => {
        return {
            processVersionId: processVersionId,
            createDate: "2016-10-10T12:39:44.092",
            user: "TouK",
            actions: [],
        };
    };
});
