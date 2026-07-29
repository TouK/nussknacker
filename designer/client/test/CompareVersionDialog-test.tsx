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
        { versionId: 35, changedElements: [], differencesUnknown: false },
        { versionId: 34, changedElements: [], differencesUnknown: false },
    ],
    hasMore: false,
};

const remoteVersion = (processVersionId: number): ProcessVersionType => ({
    processVersionId,
    createDate: "2024-05-31",
    user: "test",
    modelVersion: 4,
    actions: [],
});

const renderDialog = (options: { store?: ReturnType<typeof mockStore>; predefinedVersionId?: string } = {}) =>
    render(
        <NuThemeProvider>
            <Provider store={options.store ?? store}>
                <CompareVersionsDialog
                    data={{
                        title: "compare versions",
                        kind: 12,
                        id: "8b0a9e43-9d18-4837-950c-858d35b7c60c",
                        meta: { scenarioVersionId: options.predefinedVersionId },
                    }}
                />
            </Provider>
        </NuThemeProvider>,
    );

const localVersionsWithDifferencesUrl = () => `/processes/${scenario.name}/${scenario.processVersionId}/versions-with-differences`;
const remoteVersionsWithDifferencesUrl = () => `/remoteEnvironment/${scenario.name}/${scenario.processVersionId}/versions-with-differences`;

const switchToRemoteEnvironment = async () => {
    // the mocked useTranslation returns the raw key, ignoring interpolation
    fireEvent.keyDown(await screen.findByText("dialog.compareVersions.local"), DOWN_ARROW);
    fireEvent.click(await screen.findByText("dialog.compareVersions.remoteWithName"));
};

describe("CompareVersionsDialog", () => {
    beforeEach(() => {
        // handlers are registered per test with replyOnce - without this, one left unconsumed would be
        // picked up by the next test and make it pass or fail for the wrong reason
        mock.resetHandlers();
    });

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
        mock.onGet(`/processes/${scenario.name}/${scenario.processVersionId}/versions-with-differences`).replyOnce(
            200,
            localVersionsWithDifferences,
        );
        mock.onGet(`/remoteEnvironment/${scenario.name}/${scenario.processVersionId}/versions-with-differences`).replyOnce(200, {
            versions: [{ versionId: 1, changedElements: [], differencesUnknown: false }],
            hasMore: false,
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
        mock.onGet(`/processes/${scenario.name}/${scenario.processVersionId}/versions-with-differences`).replyOnce(
            200,
            localVersionsWithDifferences,
        );
        mock.onGet(`/remoteEnvironment/${scenario.name}/${scenario.processVersionId}/versions-with-differences`).replyOnce(200, {
            versions: [],
            hasMore: false,
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
            versions: [{ versionId: 35, changedElements: ["Node 'x' modified"], differencesUnknown: false }],
            hasMore: false,
        });
        mock.onGet(`/remoteEnvironment/${scenario.name}/${scenario.processVersionId}/versions-with-differences`).replyOnce(200, {
            versions: [],
            hasMore: false,
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

    it("should display a version's comment on its own line, separate from the created-by details", async () => {
        const storeWithComment = mockStore({
            graphReducer,
            settings: { featuresSettings: { remoteEnvironment: { targetEnvironmentId: "remote environment" } } },
            processActivity: {
                activities: [
                    {
                        uiType: "item",
                        type: "SCENARIO_MODIFIED",
                        scenarioVersionId: 34,
                        comment: {
                            content: { status: "AVAILABLE", value: "Updated timeout" },
                            lastModifiedBy: "admin",
                            lastModifiedAt: "2024-05-31",
                        },
                    },
                ],
            },
        });

        mock.onGet(`/remoteEnvironment/${scenario.name}/versions`).replyOnce(200, []);
        mock.onGet(`/processes/${scenario.name}/${scenario.processVersionId}/versions-with-differences`).replyOnce(
            200,
            localVersionsWithDifferences,
        );
        mock.onGet(`/remoteEnvironment/${scenario.name}/${scenario.processVersionId}/versions-with-differences`).replyOnce(200, {
            versions: [],
            hasMore: false,
        });

        render(
            <NuThemeProvider>
                <Provider store={storeWithComment}>
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

        const historyItemText = "34 - created by admin 2024-05-31|00:00\nUpdated timeout";
        const option = await screen.findByText((_, element) => element?.textContent === historyItemText);
        expect(option.textContent).toBe(historyItemText);
    });

    it("should show a loading spinner while fetching an older page of versions, and hide it once the page arrives", async () => {
        mock.onGet(`/remoteEnvironment/${scenario.name}/versions`).replyOnce(200, []);
        mock.onGet(`/processes/${scenario.name}/${scenario.processVersionId}/versions-with-differences`).replyOnce(200, {
            versions: [{ versionId: 35, changedElements: [], differencesUnknown: false }],
            hasMore: true,
        });
        mock.onGet(`/remoteEnvironment/${scenario.name}/${scenario.processVersionId}/versions-with-differences`).replyOnce(200, {
            versions: [],
            hasMore: false,
        });
        mock.onGet(`/processes/${scenario.name}/${scenario.processVersionId}/versions-with-differences`).replyOnce(200, {
            versions: [{ versionId: 34, changedElements: [], differencesUnknown: false }],
            hasMore: false,
        });

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

        fireEvent.keyDown(await screen.findByText("Select..."), DOWN_ARROW);

        const loadOlderVersionsRow = await screen.findByText("dialog.compareVersions.loadOlderVersions");
        expect(screen.queryByRole("progressbar")).not.toBeInTheDocument();

        fireEvent.mouseDown(loadOlderVersionsRow);

        expect(await screen.findByText("dialog.compareVersions.loadingOlderVersions")).toBeInTheDocument();
        expect(screen.getByRole("progressbar")).toBeInTheDocument();

        const olderVersionText = "34 - created by admin 2024-05-31|00:00";
        await waitFor(() => {
            expect(screen.getByText(olderVersionText)).toBeInTheDocument();
        });
        expect(screen.queryByRole("progressbar")).not.toBeInTheDocument();
        expect(screen.queryByText("dialog.compareVersions.loadOlderVersions")).not.toBeInTheDocument();
    });

    it("should fall back to the unfiltered local version list when the versions-with-differences request fails", async () => {
        mock.onGet(`/remoteEnvironment/${scenario.name}/versions`).replyOnce(200, []);
        mock.onGet(`/processes/${scenario.name}/${scenario.processVersionId}/versions-with-differences`).replyOnce(500);
        mock.onGet(`/remoteEnvironment/${scenario.name}/${scenario.processVersionId}/versions-with-differences`).replyOnce(200, {
            versions: [],
            hasMore: false,
        });

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

        fireEvent.keyDown(await screen.findByText("Select..."), DOWN_ARROW);

        expect(await screen.findByText("35 - created by admin 2024-05-31|00:00")).toBeInTheDocument();
        expect(await screen.findByText("34 - created by admin 2024-05-31|00:00")).toBeInTheDocument();
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
        mock.onGet(`/processes/${scenario.name}/${scenario.processVersionId}/versions-with-differences`).replyOnce(
            200,
            localVersionsWithDifferences,
        );
        mock.onGet(`/remoteEnvironment/${scenario.name}/${scenario.processVersionId}/versions-with-differences`).replyOnce(200, {
            versions: [],
            hasMore: false,
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

    it("should keep paging past a page whose versions are all filtered out, until one with content arrives", async () => {
        mock.onGet(`/remoteEnvironment/${scenario.name}/versions`).replyOnce(200, []);
        mock.onGet(remoteVersionsWithDifferencesUrl()).replyOnce(200, { versions: [], hasMore: false });
        // page 0 has content, page 1 is entirely filtered out server-side, page 2 has content again -
        // a single hop would stop on the empty page and leave the spinner up with nothing new shown
        mock.onGet(localVersionsWithDifferencesUrl())
            .replyOnce(200, { versions: [{ versionId: 35, changedElements: [], differencesUnknown: false }], hasMore: true })
            .onGet(localVersionsWithDifferencesUrl())
            .replyOnce(200, { versions: [], hasMore: true })
            .onGet(localVersionsWithDifferencesUrl())
            .replyOnce(200, { versions: [{ versionId: 34, changedElements: [], differencesUnknown: false }], hasMore: false });

        renderDialog();

        fireEvent.keyDown(await screen.findByText("Select..."), DOWN_ARROW);
        fireEvent.mouseDown(await screen.findByText("dialog.compareVersions.loadOlderVersions"));

        // only page 2 contains version 34, so seeing it proves the chain continued past the empty page 1
        expect(await screen.findByText("34 - created by admin 2024-05-31|00:00")).toBeInTheDocument();
        await waitFor(() => {
            expect(screen.queryByRole("progressbar")).not.toBeInTheDocument();
        });
        expect(screen.queryByText("dialog.compareVersions.loadOlderVersions")).not.toBeInTheDocument();
    });

    it("should stop paging and hide the spinner when a request in the middle of the chain fails", async () => {
        mock.onGet(`/remoteEnvironment/${scenario.name}/versions`).replyOnce(200, []);
        mock.onGet(remoteVersionsWithDifferencesUrl()).replyOnce(200, { versions: [], hasMore: false });
        mock.onGet(localVersionsWithDifferencesUrl())
            .replyOnce(200, { versions: [{ versionId: 35, changedElements: [], differencesUnknown: false }], hasMore: true })
            .onGet(localVersionsWithDifferencesUrl())
            .replyOnce(200, { versions: [], hasMore: true })
            .onGet(localVersionsWithDifferencesUrl())
            .replyOnce(500);

        renderDialog();

        fireEvent.keyDown(await screen.findByText("Select..."), DOWN_ARROW);
        fireEvent.mouseDown(await screen.findByText("dialog.compareVersions.loadOlderVersions"));

        // a failure must break the chain rather than leave a spinner spinning forever
        await waitFor(() => {
            expect(screen.queryByRole("progressbar")).not.toBeInTheDocument();
        });
        // and the list falls back to the unfiltered versions instead of dead-ending
        expect(await screen.findByText("34 - created by admin 2024-05-31|00:00")).toBeInTheDocument();
    });

    it("should fall back to the unfiltered remote version list when the remote versions-with-differences request fails", async () => {
        mock.onGet(`/remoteEnvironment/${scenario.name}/versions`).replyOnce(200, [remoteVersion(1), remoteVersion(2)]);
        mock.onGet(localVersionsWithDifferencesUrl()).replyOnce(200, localVersionsWithDifferences);
        mock.onGet(remoteVersionsWithDifferencesUrl()).replyOnce(500);

        renderDialog();

        await switchToRemoteEnvironment();
        fireEvent.keyDown(await screen.findByText("Select..."), DOWN_ARROW);

        expect(await screen.findByText("1 on remote environment - created by test 2024-05-31|00:00")).toBeInTheDocument();
        expect(await screen.findByText("2 on remote environment - created by test 2024-05-31|00:00")).toBeInTheDocument();
    });

    it("should describe a version's changed elements in its tooltip", async () => {
        mock.onGet(`/remoteEnvironment/${scenario.name}/versions`).replyOnce(200, []);
        mock.onGet(remoteVersionsWithDifferencesUrl()).replyOnce(200, { versions: [], hasMore: false });
        mock.onGet(localVersionsWithDifferencesUrl()).replyOnce(200, {
            versions: [{ versionId: 35, changedElements: ["Node 'x' modified", "Node 'y' added"], differencesUnknown: false }],
            hasMore: false,
        });

        renderDialog();

        fireEvent.keyDown(await screen.findByText("Select..."), DOWN_ARROW);

        // the default normalizer collapses the newline the elements are joined with, so opt out of it
        expect(await screen.findByTitle("Node 'x' modified\nNode 'y' added", { normalizer: (value) => value })).toBeInTheDocument();
    });

    it("should mark a remote version whose differences could not be determined", async () => {
        mock.onGet(`/remoteEnvironment/${scenario.name}/versions`).replyOnce(200, [remoteVersion(1)]);
        mock.onGet(localVersionsWithDifferencesUrl()).replyOnce(200, localVersionsWithDifferences);
        mock.onGet(remoteVersionsWithDifferencesUrl()).replyOnce(200, {
            versions: [{ versionId: 1, changedElements: [], differencesUnknown: true }],
            hasMore: false,
        });

        renderDialog();

        await switchToRemoteEnvironment();
        fireEvent.keyDown(await screen.findByText("Select..."), DOWN_ARROW);

        expect(await screen.findByTitle("dialog.compareVersions.unknownDifferences")).toBeInTheDocument();
    });

    it("should say that the remote environment could not be reached instead of showing nothing to compare", async () => {
        mock.onGet(`/remoteEnvironment/${scenario.name}/versions`).replyOnce(500);
        mock.onGet(localVersionsWithDifferencesUrl()).replyOnce(200, localVersionsWithDifferences);
        mock.onGet(remoteVersionsWithDifferencesUrl()).replyOnce(200, { versions: [], hasMore: false, remoteUnavailable: true });

        renderDialog();

        await switchToRemoteEnvironment();

        expect(await screen.findByText("dialog.compareVersions.remoteUnavailable")).toBeInTheDocument();
    });

    it("should not let the environment be switched when the version is predefined", async () => {
        mock.onGet(`/remoteEnvironment/${scenario.name}/versions`).replyOnce(200, []);
        mock.onGet(localVersionsWithDifferencesUrl()).replyOnce(200, localVersionsWithDifferences);
        mock.onGet(remoteVersionsWithDifferencesUrl()).replyOnce(200, { versions: [], hasMore: false });
        mock.onGet(`/processes/${scenario.name}/${scenario.processVersionId}/compare/34`).replyOnce(200, {});

        renderDialog({ predefinedVersionId: "34" });

        // switching would clear the read-only version select and dead-end the dialog until it's reopened
        fireEvent.keyDown(await screen.findByText("dialog.compareVersions.local"), DOWN_ARROW);
        expect(screen.queryByText("dialog.compareVersions.remoteWithName")).not.toBeInTheDocument();
    });
});
