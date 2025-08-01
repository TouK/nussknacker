import { jest } from "@jest/globals";
import { configureStore } from "@reduxjs/toolkit";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import MockAdapter from "axios-mock-adapter";
import React from "react";
import { Provider } from "react-redux";
import api from "../src/api";

import CompareVersionsDialog from "../src/components/modals/CompareVersionsDialog";
import { ProcessVersionType } from "../src/components/Process/types";
import { NuThemeProvider } from "../src/containers/theme/nuThemeProvider";

const mock = new MockAdapter(api);

jest.mock("react-i18next", () => ({
    useTranslation: () => ({
        t: (key) => key,
        i18n: { changeLanguage: () => {} },
    }),
}));

jest.mock("../src/windowManager", () => ({
    WindowContent: ({ children }) => <div>{children}</div>,
}));

// this module brings nothing but problems with some nested imports to this test, so it could be safely mocked
jest.mock("../src/components/graph/node-modal/NodeDetailsContent", () => ({
    NodeDetailsContent: ({ children }) => <div>{children}</div>,
}));

const preloadedState = {
    graphReducer: {
        present: {
            scenario: {
                name: "proc1",
                processVersionId: 4,
                history: [
                    {
                        processVersionId: 35,
                        createDate: "2024-05-31",
                        user: "admin",
                        modelVersion: 4,
                        actions: [],
                    },
                    {
                        processVersionId: 34,
                        createDate: "2024-05-31",
                        user: "admin",
                        modelVersion: 4,
                        actions: [],
                    },
                ],
            },
        },
    },
    settings: { featuresSettings: { remoteEnvironment: { targetEnvironmentId: "remote environment" } } },
};

const store = configureStore({
    reducer: (state) => state,
    preloadedState,
    devTools: false,
});

const scenario = preloadedState.graphReducer.present.scenario;

const DOWN_ARROW = { keyCode: 40 };

describe("CompareVersionsDialog", () => {
    afterAll(() => {
        mock.resetHandlers();
    });

    it("should provide remote prefix for remote options and call correct remote endpoint when remote version selected", async () => {
        const remoteVersions: ProcessVersionType[] = [
            {
                processVersionId: 1,
                createDate: "2024-05-31",
                user: "test",
            },
        ];

        mock.onGet(`/remoteEnvironment/${scenario.name}/versions`).replyOnce(200, remoteVersions);
        mock.onGet(
            `/remoteEnvironment/${scenario.name}/${scenario.processVersionId}/compare/${remoteVersions[0].processVersionId}`,
        ).replyOnce(200, {});

        render(
            <NuThemeProvider>
                <Provider store={store}>
                    <CompareVersionsDialog
                        data={{
                            title: "compare versions",
                            kind: 12,
                            id: "8b0a9e43-9d18-4837-950c-858d35b7c60c",
                            meta: { scenarioVersionId: undefined },
                        }}
                    />
                </Provider>
            </NuThemeProvider>,
        );

        fireEvent.keyDown(screen.getByText("Select..."), DOWN_ARROW);

        expect(await screen.findByText("34 - created by admin 2024-05-31|00:00")).toBeInTheDocument();

        const remoteItemText = "1 on remote environment - created by test 2024-05-31|00:00";

        await waitFor(() => {
            fireEvent.click(screen.getByText(remoteItemText));
        });

        expect(await screen.findByText("Difference to pick")).toBeInTheDocument();
        expect(await screen.findByText(remoteItemText)).toBeInTheDocument();
    });

    it("should select history version and call correct processes endpoint when history version selected", async () => {
        const remoteVersions: ProcessVersionType[] = [
            {
                processVersionId: 1,
                createDate: "2024-05-31",
                user: "test",
            },
        ];

        mock.onGet(`/remoteEnvironment/${scenario.name}/versions`).replyOnce(200, remoteVersions);
        mock.onGet(`/processes/${scenario.name}/${scenario.processVersionId}/compare/${scenario.history[1].processVersionId}`).replyOnce(
            200,
            {},
        );

        render(
            <NuThemeProvider>
                <Provider store={store}>
                    <CompareVersionsDialog
                        data={{
                            title: "compare versions",
                            kind: 12,
                            id: "8b0a9e43-9d18-4837-950c-858d35b7c60c",
                            meta: { scenarioVersionId: undefined },
                        }}
                    />
                </Provider>
            </NuThemeProvider>,
        );

        fireEvent.keyDown(screen.getByText("Select..."), DOWN_ARROW);

        const historyItemText = "34 - created by admin 2024-05-31|00:00";

        await waitFor(() => {
            fireEvent.click(screen.getByText(historyItemText));
        });

        expect(await screen.findByText("Difference to pick")).toBeInTheDocument();
        expect(await screen.findByText(historyItemText)).toBeInTheDocument();
    });
});
