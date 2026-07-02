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

        // Switch to remote environment (mocked useTranslation returns the raw key, ignoring interpolation)
        fireEvent.keyDown(screen.getByText("dialog.compareVersions.local"), DOWN_ARROW);
        fireEvent.click(await screen.findByText("dialog.compareVersions.remoteWithName"));

        // Open version picker and select the remote version
        fireEvent.keyDown(await screen.findByText("Select..."), DOWN_ARROW);

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

        const historyItemText = "34 - created by admin 2024-05-31|00:00";

        await waitFor(() => {
            fireEvent.click(screen.getByText(historyItemText));
        });

        expect(await screen.findByText("Difference to pick")).toBeInTheDocument();
        expect(await screen.findByText(historyItemText)).toBeInTheDocument();
    });

    it("should keep the predefined version visible in the dropdown even when it has no meaningful diff", async () => {
        mock.onGet(`/remoteEnvironment/${scenario.name}/versions`).replyOnce(200, []);
        // version 34 (the predefined one) is absent from the returned page - e.g. it only had a layout-only
        // diff, filtered out server-side - while version 35 does have a meaningful diff.
        mock.onGet(`/processes/${scenario.name}/${scenario.processVersionId}/versions-with-differences`).replyOnce(200, {
            versions: [{ versionId: 35, changedElements: ["Node 'x' modified"] }],
            hasMore: false,
            pageSize: 5,
        });
        mock.onGet(`/remoteEnvironment/${scenario.name}/${scenario.processVersionId}/versions-with-differences`).replyOnce(200, {
            versions: [],
            hasMore: false,
            pageSize: 5,
        });
        mock.onGet(`/processes/${scenario.name}/${scenario.processVersionId}/compare/34`).replyOnce(200, {});

        render(
            <NuThemeProvider>
                <Provider store={store}>
                    <CompareVersionsDialog
                        data={{
                            title: "compare versions",
                            kind: 12,
                            id: "8b0a9e43-9d18-4837-950c-858d35b7c60c",
                            meta: { scenarioVersionId: "34" },
                        }}
                    />
                </Provider>
            </NuThemeProvider>,
        );

        const historyItemText = "34 - created by admin 2024-05-31|00:00";
        expect(await screen.findByText(historyItemText)).toBeInTheDocument();
    });

    it("should show the local environment's configured name in the environment picker when set", async () => {
        const storeWithLocalEnvironmentName = mockStore({
            graphReducer,
            settings: {
                featuresSettings: {
                    remoteEnvironment: { targetEnvironmentId: "remote environment" },
                    environmentAlert: { content: "local environment" },
                },
            },
            processActivity: { activities: [] },
        });

        mock.onGet(`/remoteEnvironment/${scenario.name}/versions`).replyOnce(200, []);
        mock.onGet(`/processes/${scenario.name}/${scenario.processVersionId}/versions-with-differences`).replyOnce(200, localVersionsWithDifferences);
        mock.onGet(`/remoteEnvironment/${scenario.name}/${scenario.processVersionId}/versions-with-differences`).replyOnce(200, {
            versions: [],
            hasMore: false,
            pageSize: 5,
        });

        render(
            <NuThemeProvider>
                <Provider store={storeWithLocalEnvironmentName}>
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

        // mocked useTranslation returns the raw key, ignoring interpolation - confirms the "with name" key is
        // used instead of the plain "local"/"remoteWithName" fallback when environmentAlert.content is set
        expect(await screen.findByText("dialog.compareVersions.localWithName")).toBeInTheDocument();
        fireEvent.keyDown(screen.getByText("dialog.compareVersions.localWithName"), DOWN_ARROW);
        expect(await screen.findByText("dialog.compareVersions.remoteWithName")).toBeInTheDocument();
    });
});
