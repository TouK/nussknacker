import React from "react";

import CompareVersionsDialog from "../src/components/modals/CompareVersionsDialog";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { jest } from "@jest/globals";
import { NuThemeProvider } from "../src/containers/theme/nuThemeProvider";
import configureMockStore from "redux-mock-store/lib";
import thunk from "redux-thunk";
import { Provider } from "react-redux";
import { ProcessVersionType } from "../src/components/Process/types";
import MockAdapter from "axios-mock-adapter";
import api from "../src/api";

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

const mockStore = configureMockStore([thunk]);
const graphReducer = {
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
};

const store = mockStore({
    graphReducer,
    settings: { featuresSettings: { remoteEnvironment: { targetEnvironmentId: "remote environment" } } },
    processActivity: { activities: [] },
});

const scenario = graphReducer.present.scenario;

const DOWN_ARROW = { keyCode: 40 };

const localVersionsWithDifferences = {
    versions: [
        { versionId: 35, changedElements: [] },
        { versionId: 34, changedElements: [] },
    ],
    hasMore: false,
    pageSize: 5,
};

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
        mock.onGet(`/processes/${scenario.name}/${scenario.processVersionId}/versions-with-differences`).replyOnce(200, localVersionsWithDifferences);
        mock.onGet(`/remoteEnvironment/${scenario.name}/${scenario.processVersionId}/versions-with-differences`).replyOnce(200, {
            versions: [{ versionId: 1, changedElements: [] }],
            hasMore: false,
            pageSize: 5,
        });
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

        // Switch to remote environment
        fireEvent.keyDown(screen.getByText("Local"), DOWN_ARROW);
        fireEvent.click(await screen.findByText("remote environment"));

        // Open version picker and select the remote version
        fireEvent.keyDown(await screen.findByText("Select..."), DOWN_ARROW);

        const remoteItemText = "1 on remote environment - 2024-05-31|00:00 test";

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
        mock.onGet(`/processes/${scenario.name}/${scenario.processVersionId}/versions-with-differences`).replyOnce(200, localVersionsWithDifferences);
        mock.onGet(`/remoteEnvironment/${scenario.name}/${scenario.processVersionId}/versions-with-differences`).replyOnce(200, {
            versions: [],
            hasMore: false,
            pageSize: 5,
        });
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

        const historyItemText = "34 - 2024-05-31|00:00 admin";

        await waitFor(() => {
            fireEvent.click(screen.getByText(historyItemText));
        });

        expect(await screen.findByText("Difference to pick")).toBeInTheDocument();
        expect(await screen.findByText(historyItemText)).toBeInTheDocument();
    });
});
