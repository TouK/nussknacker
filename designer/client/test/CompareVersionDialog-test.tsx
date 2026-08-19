import { jest } from "@jest/globals";
import { configureStore } from "@reduxjs/toolkit";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
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

jest.mock("../src/windowManager/WindowContent", () => ({
    WindowContent: ({ children }) => <div>{children}</div>,
}));

// this module brings nothing but problems with some nested imports to this test, so it could be safely mocked
jest.mock("../src/components/graph/node-modal/NodeDetailsContent", () => ({
    NodeDetailsContent: ({ children }) => <div>{children}</div>,
}));

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
                },
                {
                    processVersionId: 34,
                    createDate: "2024-05-31",
                    user: "admin",
                },
            ],
        },
    },
};

const buildStore = (options: { environmentAlert?: { content: string } } = {}) =>
    configureStore({
        reducer: (state) => state,
        preloadedState: {
            graphReducer,
            settings: {
                featuresSettings: {
                    remoteEnvironment: { targetEnvironmentId: "remote environment" },
                    ...(options.environmentAlert ? { environmentAlert: options.environmentAlert } : {}),
                },
            },
            processActivity: { activities: [] },
        },
        devTools: false,
    });

const store = buildStore();

const scenario = graphReducer.present.scenario;

const DOWN_ARROW = { keyCode: 40 };

const changedVersion = (versionId: number) => ({
    versionId,
    changedElements: ["Node 'x' modified"],
    differencesUnknown: false,
});

const localVersionsWithDifferences = { versions: [changedVersion(35), changedVersion(34)] };

const remoteVersion = (processVersionId: number): ProcessVersionType => ({
    processVersionId,
    createDate: "2024-05-31",
    user: "test",
});

const renderDialog = (options: { store?: ReturnType<typeof buildStore>; predefinedVersionId?: string } = {}) =>
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
const remoteVersionsUrl = () => `/remoteEnvironment/${scenario.name}/versions`;

const openVersionPicker = async () => fireEvent.keyDown(await screen.findByText("Select..."), DOWN_ARROW);

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
        mock.resetHistory();
        mock.onGet(`/processes/${scenario.name}/activity/activities`).reply(200, { activities: [] });
        mock.onGet(`/processes/${scenario.name}/activity/activities/metadata`).reply(200, { activities: [], actions: [] });
    });

    afterAll(() => {
        mock.resetHandlers();
    });

    it("should provide remote prefix for remote options and call correct remote endpoint when remote version selected", async () => {
        mock.onGet(remoteVersionsUrl()).replyOnce(200, [remoteVersion(1)]);
        mock.onGet(localVersionsWithDifferencesUrl()).replyOnce(200, localVersionsWithDifferences);
        mock.onGet(remoteVersionsWithDifferencesUrl()).replyOnce(200, { versions: [changedVersion(1)] });
        mock.onGet(`/remoteEnvironment/${scenario.name}/${scenario.processVersionId}/compare/1`).replyOnce(200, {});

        renderDialog();

        await switchToRemoteEnvironment();
        await openVersionPicker();

        const remoteItemText = "1 on remote environment - created by test 2024-05-31|00:00";
        expect(await screen.findByText(remoteItemText)).toBeInTheDocument();

        fireEvent.click(screen.getByText(remoteItemText));

        await waitFor(() => {
            expect(
                mock.history.get.some((r) => r.url === `/remoteEnvironment/${scenario.name}/${scenario.processVersionId}/compare/1`),
            ).toBe(true);
        });
    });

    it("should get all of the other environment's versions in one request", async () => {
        mock.onGet(remoteVersionsUrl()).replyOnce(200, [remoteVersion(1)]);
        mock.onGet(localVersionsWithDifferencesUrl()).replyOnce(200, localVersionsWithDifferences);
        mock.onGet(remoteVersionsWithDifferencesUrl()).replyOnce(200, { versions: [changedVersion(1)] });

        renderDialog();
        await switchToRemoteEnvironment();
        await openVersionPicker();

        expect(await screen.findByText("1 on remote environment - created by test 2024-05-31|00:00")).toBeInTheDocument();
        const remoteRequests = mock.history.get.filter((r) => r.url === remoteVersionsWithDifferencesUrl());
        expect(remoteRequests).toHaveLength(1);
        // the remote bounds its own work with this, so it has to be asked for
        expect(remoteRequests[0].params).toEqual({ limit: 50 });
    });

    it("should compare against a local version when one is selected", async () => {
        mock.onGet(localVersionsWithDifferencesUrl()).replyOnce(200, localVersionsWithDifferences);
        mock.onGet(`/processes/${scenario.name}/${scenario.processVersionId}/compare/34`).replyOnce(200, {});

        renderDialog();
        await openVersionPicker();

        const historyItemText = "34 - created by admin 2024-05-31|00:00";
        fireEvent.click(await screen.findByText(historyItemText));

        expect(await screen.findByText("dialog.compareVersions.differenceToPick:")).toBeInTheDocument();
    });

    it("should hide a version that has no meaningful difference from the current one", async () => {
        mock.onGet(localVersionsWithDifferencesUrl()).replyOnce(200, { versions: [changedVersion(35)] });

        renderDialog();
        await openVersionPicker();

        expect(await screen.findByText("35 - created by admin 2024-05-31|00:00")).toBeInTheDocument();
        expect(screen.queryByText("34 - created by admin 2024-05-31|00:00")).not.toBeInTheDocument();
    });

    it("should keep the predefined version visible in the dropdown even when it has no meaningful diff", async () => {
        mock.onGet(localVersionsWithDifferencesUrl()).replyOnce(200, { versions: [changedVersion(35)] });
        mock.onGet(`/processes/${scenario.name}/${scenario.processVersionId}/compare/34`).replyOnce(200, {});

        renderDialog({ predefinedVersionId: "34" });

        expect(await screen.findByText("34 - created by admin 2024-05-31|00:00")).toBeInTheDocument();
    });

    it("should show the predefined version as selected while the differences are still loading", async () => {
        mock.onGet(localVersionsWithDifferencesUrl()).replyOnce(() => new Promise(() => undefined));
        mock.onGet(`/processes/${scenario.name}/${scenario.processVersionId}/compare/34`).replyOnce(200, {});

        renderDialog({ predefinedVersionId: "34" });

        expect(await screen.findByText("34 - created by admin 2024-05-31|00:00")).toBeInTheDocument();
        expect(within(document.getElementById("otherVersion")).queryByText("Select...")).not.toBeInTheDocument();
    });

    it("should not ask the remote environment for anything until it is selected", async () => {
        mock.onGet(localVersionsWithDifferencesUrl()).replyOnce(200, localVersionsWithDifferences);

        renderDialog();
        await openVersionPicker();
        await screen.findByText("35 - created by admin 2024-05-31|00:00");

        expect(mock.history.get.some((r) => r.url?.startsWith("/remoteEnvironment"))).toBe(false);
    });

    it("should report the remote environment as unreachable when only the version list request fails", async () => {
        mock.onGet(remoteVersionsUrl()).replyOnce(500);
        mock.onGet(localVersionsWithDifferencesUrl()).replyOnce(200, localVersionsWithDifferences);
        mock.onGet(remoteVersionsWithDifferencesUrl()).replyOnce(200, { versions: [] });

        renderDialog();
        await switchToRemoteEnvironment();

        expect(await screen.findByText("dialog.compareVersions.remoteUnavailable")).toBeInTheDocument();
    });

    it("should say it is loading rather than showing an empty picker while the first page loads", async () => {
        let releaseFirstPage: () => void;
        const firstPageLoaded = new Promise<void>((resolve) => {
            releaseFirstPage = resolve;
        });
        mock.onGet(localVersionsWithDifferencesUrl()).replyOnce(async () => {
            await firstPageLoaded;
            return [200, localVersionsWithDifferences];
        });

        renderDialog();
        await openVersionPicker();

        expect(await screen.findByText("Loading...")).toBeInTheDocument();
        expect(screen.queryByText("dialog.compareVersions.noVersionsToCompare")).not.toBeInTheDocument();

        releaseFirstPage();

        expect(await screen.findByText("35 - created by admin 2024-05-31|00:00")).toBeInTheDocument();
        expect(screen.queryByText("Loading...")).not.toBeInTheDocument();
    });

    // The remote list and its differences are separate requests, and this pins the case where the
    // differences land first - the picker then has an answer about versions it has not been given yet.
    it("should say it is loading while the remote version list is still on its way", async () => {
        let releaseRemoteVersions: () => void;
        const remoteVersionsLoaded = new Promise<void>((resolve) => {
            releaseRemoteVersions = resolve;
        });
        mock.onGet(remoteVersionsUrl()).replyOnce(async () => {
            await remoteVersionsLoaded;
            return [200, [remoteVersion(1)]];
        });
        mock.onGet(localVersionsWithDifferencesUrl()).replyOnce(200, localVersionsWithDifferences);
        mock.onGet(remoteVersionsWithDifferencesUrl()).replyOnce(200, { versions: [changedVersion(1)] });

        renderDialog();
        await switchToRemoteEnvironment();
        await openVersionPicker();

        expect(await screen.findByText("Loading...")).toBeInTheDocument();
        expect(screen.queryByText("dialog.compareVersions.noVersionsToCompare")).not.toBeInTheDocument();

        releaseRemoteVersions();

        expect(await screen.findByText("1 on remote environment - created by test 2024-05-31|00:00")).toBeInTheDocument();
        expect(screen.queryByText("Loading...")).not.toBeInTheDocument();
    });

    it("should say there is nothing to compare against when no version differs", async () => {
        mock.onGet(localVersionsWithDifferencesUrl()).replyOnce(200, { versions: [] });

        renderDialog();
        await openVersionPicker();

        expect(await screen.findByText("dialog.compareVersions.noVersionsToCompare")).toBeInTheDocument();
    });

    // Nothing was computed for versions past the limit, so hiding them would claim they are identical to the
    // current one. They are listed plainly instead, as the dialog did before it filtered anything.
    it("should list versions older than the compared window even though they have no differences", async () => {
        mock.onGet(localVersionsWithDifferencesUrl()).replyOnce(200, {
            versions: [changedVersion(35)],
            oldestComparedVersionId: 35,
        });

        renderDialog();
        await openVersionPicker();

        expect(await screen.findByText("35 - created by admin 2024-05-31|00:00")).toBeInTheDocument();
        expect(screen.getByText("34 - created by admin 2024-05-31|00:00")).toBeInTheDocument();
        expect(screen.getByText("dialog.compareVersions.comparedRecentOnly")).toBeInTheDocument();
    });

    it("should place the messages under the version picker rather than beside it", async () => {
        mock.onGet(localVersionsWithDifferencesUrl()).replyOnce(200, {
            versions: [changedVersion(35)],
            oldestComparedVersionId: 35,
        });

        renderDialog();

        const message = await screen.findByText("dialog.compareVersions.comparedRecentOnly");
        const row = message.closest(".MuiFormControl-root");
        expect(row).not.toBeNull();
        expect(message.parentElement).not.toBe(row);
        // the label, and the single column holding the picker together with its messages
        expect(row.children).toHaveLength(2);
    });

    it("should keep hiding identical versions that were compared", async () => {
        mock.onGet(localVersionsWithDifferencesUrl()).replyOnce(200, {
            versions: [changedVersion(35)],
            oldestComparedVersionId: 34,
        });

        renderDialog();
        await openVersionPicker();

        expect(await screen.findByText("35 - created by admin 2024-05-31|00:00")).toBeInTheDocument();
        expect(screen.queryByText("34 - created by admin 2024-05-31|00:00")).not.toBeInTheDocument();
    });

    it("should say nothing about a compared window when the whole history was compared", async () => {
        mock.onGet(localVersionsWithDifferencesUrl()).replyOnce(200, localVersionsWithDifferences);

        renderDialog();
        await openVersionPicker();

        await screen.findByText("35 - created by admin 2024-05-31|00:00");
        expect(screen.queryByText("dialog.compareVersions.comparedRecentOnly")).not.toBeInTheDocument();
        expect(screen.queryByText("dialog.compareVersions.versionsCompared:")).not.toBeInTheDocument();
    });

    it("should re-compare with the new limit when the user raises how many versions are compared", async () => {
        mock.onGet(localVersionsWithDifferencesUrl()).replyOnce(200, {
            versions: [changedVersion(35)],
            oldestComparedVersionId: 35,
        });
        mock.onGet(localVersionsWithDifferencesUrl()).replyOnce(200, localVersionsWithDifferences);

        renderDialog();
        await screen.findByText("dialog.compareVersions.versionsCompared:");

        fireEvent.keyDown(await screen.findByText("50"), DOWN_ARROW);
        fireEvent.click(await screen.findByText("250"));

        await waitFor(() => {
            const requests = mock.history.get.filter((r) => r.url === localVersionsWithDifferencesUrl());
            expect(requests.map((r) => r.params.limit)).toEqual([50, 250]);
        });
    });

    // Changing the limit twice in a row must not let the slower earlier answer overwrite the newer one.
    it("should ignore a response that arrives after the limit has changed again", async () => {
        let releaseSecond: () => void;
        const secondSettled = new Promise<void>((resolve) => {
            releaseSecond = resolve;
        });
        mock.onGet(localVersionsWithDifferencesUrl()).replyOnce(200, {
            versions: [changedVersion(35)],
            oldestComparedVersionId: 35,
        });
        mock.onGet(localVersionsWithDifferencesUrl()).replyOnce(async () => {
            await secondSettled;
            return [200, { versions: [changedVersion(34)] }];
        });
        mock.onGet(localVersionsWithDifferencesUrl()).replyOnce(200, { versions: [] });

        renderDialog();
        await screen.findByText("dialog.compareVersions.versionsCompared:");

        fireEvent.keyDown(await screen.findByText("50"), DOWN_ARROW);
        fireEvent.click(await screen.findByText("250"));
        fireEvent.keyDown(await screen.findByText("250"), DOWN_ARROW);
        fireEvent.click(await screen.findByText("500"));

        await openVersionPicker();
        expect(await screen.findByText("dialog.compareVersions.noVersionsToCompare")).toBeInTheDocument();

        releaseSecond();

        // the stale answer would have put version 34 into the list
        await waitFor(() => {
            expect(screen.queryByText("34 - created by admin 2024-05-31|00:00")).not.toBeInTheDocument();
        });
    });

    // Raising the limit far enough to cover the whole history must not remove the means of lowering it.
    it("should keep offering the compared-versions control once the history has exceeded it", async () => {
        mock.onGet(localVersionsWithDifferencesUrl()).replyOnce(200, {
            versions: [changedVersion(35)],
            oldestComparedVersionId: 35,
        });
        mock.onGet(localVersionsWithDifferencesUrl()).replyOnce(200, localVersionsWithDifferences);

        renderDialog();
        await screen.findByText("dialog.compareVersions.versionsCompared:");

        fireEvent.keyDown(await screen.findByText("50"), DOWN_ARROW);
        fireEvent.click(await screen.findByText("500"));

        await waitFor(() => {
            expect(screen.queryByText("dialog.compareVersions.comparedRecentOnly")).not.toBeInTheDocument();
        });
        expect(screen.getByText("dialog.compareVersions.versionsCompared:")).toBeInTheDocument();
    });

    it("should recompare the selected version against the new current one after a save", async () => {
        mock.onGet(localVersionsWithDifferencesUrl()).replyOnce(200, localVersionsWithDifferences);
        mock.onGet(`/processes/${scenario.name}/${scenario.processVersionId}/compare/34`).reply(200, {});

        renderDialog({ predefinedVersionId: "34" });

        await waitFor(() => {
            expect(
                mock.history.get.filter((r) => r.url === `/processes/${scenario.name}/${scenario.processVersionId}/compare/34`),
            ).toHaveLength(1);
        });
    });

    it("should describe every change the server sent, truncation being the server's job", async () => {
        const manyChanges = Array.from({ length: 30 }, (_, i) => `Node 'n${i}' modified`);
        mock.onGet(localVersionsWithDifferencesUrl()).replyOnce(200, {
            versions: [{ versionId: 35, changedElements: manyChanges, differencesUnknown: false }],
        });

        renderDialog();
        await openVersionPicker();

        const described = await screen.findByTitle(/Node 'n0' modified/, { normalizer: (value) => value });
        expect(described.getAttribute("title").split("\n")).toHaveLength(30);
    });

    it("should count the changes the server truncated away", async () => {
        const describedChanges = Array.from({ length: 30 }, (_, i) => `Node 'n${i}' modified`);
        mock.onGet(localVersionsWithDifferencesUrl()).replyOnce(200, {
            versions: [{ versionId: 35, changedElements: describedChanges, differencesUnknown: false, totalChangedElements: 42 }],
        });

        renderDialog();
        await openVersionPicker();

        const described = await screen.findByTitle(/Node 'n0' modified/, { normalizer: (value) => value });
        const lines = described.getAttribute("title").split("\n");
        // the 30 sent, then one line accounting for the 12 the server dropped
        expect(lines).toHaveLength(31);
        expect(lines[30]).toBe("dialog.compareVersions.moreChanges");
    });

    it("should fall back to the unfiltered local version list when the first request fails", async () => {
        mock.onGet(localVersionsWithDifferencesUrl()).replyOnce(500);

        renderDialog();
        await openVersionPicker();

        expect(await screen.findByText("35 - created by admin 2024-05-31|00:00")).toBeInTheDocument();
        expect(await screen.findByText("34 - created by admin 2024-05-31|00:00")).toBeInTheDocument();
    });

    it("should fall back to the unfiltered remote version list when the remote request fails", async () => {
        mock.onGet(remoteVersionsUrl()).replyOnce(200, [remoteVersion(1), remoteVersion(2)]);
        mock.onGet(localVersionsWithDifferencesUrl()).replyOnce(200, localVersionsWithDifferences);
        mock.onGet(remoteVersionsWithDifferencesUrl()).replyOnce(500);

        renderDialog();
        await switchToRemoteEnvironment();
        await openVersionPicker();

        expect(await screen.findByText("1 on remote environment - created by test 2024-05-31|00:00")).toBeInTheDocument();
        expect(await screen.findByText("2 on remote environment - created by test 2024-05-31|00:00")).toBeInTheDocument();
    });

    it("should describe a version's changed elements in its tooltip", async () => {
        mock.onGet(localVersionsWithDifferencesUrl()).replyOnce(200, {
            versions: [
                {
                    versionId: 35,
                    changedElements: ["Node 'x' modified", "Edge 'a' → 'b' added"],
                    differencesUnknown: false,
                },
            ],
        });

        renderDialog();
        await openVersionPicker();

        const expected = "Node 'x' modified\nEdge 'a' → 'b' added";
        expect(await screen.findByTitle(expected, { normalizer: (value) => value })).toBeInTheDocument();
    });

    it("should count the changes the server left out of a truncated list", async () => {
        mock.onGet(localVersionsWithDifferencesUrl()).replyOnce(200, {
            versions: [
                {
                    versionId: 35,
                    changedElements: ["Node 'x' modified", "Edge 'a' → 'b' added"],
                    differencesUnknown: false,
                    totalChangedElements: 5,
                },
            ],
        });

        renderDialog();
        await openVersionPicker();

        const expected = "Node 'x' modified\nEdge 'a' → 'b' added\ndialog.compareVersions.moreChanges";
        expect(await screen.findByTitle(expected, { normalizer: (value) => value })).toBeInTheDocument();
    });

    it("should mark a remote version whose differences could not be determined", async () => {
        mock.onGet(remoteVersionsUrl()).replyOnce(200, [remoteVersion(1)]);
        mock.onGet(localVersionsWithDifferencesUrl()).replyOnce(200, localVersionsWithDifferences);
        mock.onGet(remoteVersionsWithDifferencesUrl()).replyOnce(200, {
            versions: [{ versionId: 1, changedElements: [], differencesUnknown: true }],
        });

        renderDialog();
        await switchToRemoteEnvironment();
        await openVersionPicker();

        expect(await screen.findByTitle("dialog.compareVersions.unknownDifferences")).toBeInTheDocument();
    });

    // The version list itself succeeds here, so only the remoteUnavailable flag can produce the message -
    // otherwise a failed version list would satisfy it and the flag would go untested.
    it("should note that the remote environment did not provide the differences instead of showing nothing to compare", async () => {
        mock.onGet(remoteVersionsUrl()).replyOnce(200, [remoteVersion(1), remoteVersion(2)]);
        mock.onGet(localVersionsWithDifferencesUrl()).replyOnce(200, localVersionsWithDifferences);
        mock.onGet(remoteVersionsWithDifferencesUrl()).replyOnce(200, { versions: [], remoteUnavailable: true });

        renderDialog();
        await switchToRemoteEnvironment();

        const message = await screen.findByText("dialog.compareVersions.remoteDifferencesUnavailable");
        expect(message).toBeInTheDocument();
        expect(message).not.toHaveClass("Mui-error");
        expect(screen.queryByText("dialog.compareVersions.remoteUnavailable")).not.toBeInTheDocument();
    });

    // A failed request is not an environment reporting it could not compare, and the toast it raises is gone
    // a few seconds later - without the helper text the user is left with a full picker and no explanation.
    it("should say the differences could not be computed when the comparison request itself fails", async () => {
        mock.onGet(localVersionsWithDifferencesUrl()).replyOnce(500);

        renderDialog();

        expect(await screen.findByText("dialog.compareVersions.comparisonFailed")).toBeInTheDocument();
    });

    it("should still list every local version when the comparison request fails", async () => {
        mock.onGet(localVersionsWithDifferencesUrl()).replyOnce(500);

        renderDialog();
        await openVersionPicker();

        expect(await screen.findByText("34 - created by admin 2024-05-31|00:00")).toBeInTheDocument();
        expect(screen.getByText("35 - created by admin 2024-05-31|00:00")).toBeInTheDocument();
    });

    // An environment that answered but could not compare says nothing about its versions - hiding them all
    // would claim they are identical, and leaves the user with no way to compare at all.
    it("should still list every remote version when the remote environment could not compare", async () => {
        mock.onGet(remoteVersionsUrl()).replyOnce(200, [remoteVersion(1), remoteVersion(2)]);
        mock.onGet(localVersionsWithDifferencesUrl()).replyOnce(200, localVersionsWithDifferences);
        mock.onGet(remoteVersionsWithDifferencesUrl()).replyOnce(200, { versions: [], remoteUnavailable: true });

        renderDialog();
        await switchToRemoteEnvironment();
        await openVersionPicker();

        expect(await screen.findByText("1 on remote environment - created by test 2024-05-31|00:00")).toBeInTheDocument();
        expect(screen.getByText("2 on remote environment - created by test 2024-05-31|00:00")).toBeInTheDocument();
        expect(screen.queryByText("dialog.compareVersions.noVersionsToCompare")).not.toBeInTheDocument();
    });

    it("should show a version's comment separately from its created-by line", async () => {
        mock.onGet(localVersionsWithDifferencesUrl()).replyOnce(200, {
            ...localVersionsWithDifferences,
            versionComments: { 34: "Updated timeout" },
        });

        renderDialog();
        await openVersionPicker();

        expect(await screen.findByText("Updated timeout")).toBeInTheDocument();
        expect(screen.getByText("34 - created by admin 2024-05-31|00:00")).toBeInTheDocument();
        expect(screen.getByText("35 - created by admin 2024-05-31|00:00").textContent).not.toContain("Updated timeout");
    });

    it("should show a remote version's comment from the differences response", async () => {
        mock.onGet(remoteVersionsUrl()).replyOnce(200, [remoteVersion(1)]);
        mock.onGet(localVersionsWithDifferencesUrl()).replyOnce(200, localVersionsWithDifferences);
        mock.onGet(remoteVersionsWithDifferencesUrl()).replyOnce(200, {
            versions: [changedVersion(1)],
            versionComments: { 1: "Deployed to prod" },
        });

        renderDialog();
        await switchToRemoteEnvironment();
        await openVersionPicker();

        expect(await screen.findByText("Deployed to prod")).toBeInTheDocument();
    });

    // Comments come from the activity list, which costs no graph, so they cover every version - including
    // ones past the comparison limit, which are listed without their differences.
    it("should show a remote version's comment even when that version was not compared", async () => {
        mock.onGet(remoteVersionsUrl()).replyOnce(200, [remoteVersion(1), remoteVersion(2)]);
        mock.onGet(localVersionsWithDifferencesUrl()).replyOnce(200, localVersionsWithDifferences);
        mock.onGet(remoteVersionsWithDifferencesUrl()).replyOnce(200, {
            versions: [changedVersion(2)],
            oldestComparedVersionId: 2,
            versionComments: { 1: "Older, never compared", 2: "Recent" },
        });

        renderDialog();
        await switchToRemoteEnvironment();
        await openVersionPicker();

        expect(await screen.findByText("Older, never compared")).toBeInTheDocument();
        expect(screen.getByText("Recent")).toBeInTheDocument();
    });

    it("should not fetch the activity list, since comments come with the differences", async () => {
        mock.onGet(localVersionsWithDifferencesUrl()).replyOnce(200, localVersionsWithDifferences);

        renderDialog();
        await openVersionPicker();
        await screen.findByText("34 - created by admin 2024-05-31|00:00");

        expect(mock.history.get.some((r) => r.url === `/processes/${scenario.name}/activity/activities`)).toBe(false);
    });

    it("should clear the selected version when the environment is switched", async () => {
        mock.onGet(remoteVersionsUrl()).replyOnce(200, [remoteVersion(1)]);
        mock.onGet(localVersionsWithDifferencesUrl()).replyOnce(200, localVersionsWithDifferences);
        mock.onGet(remoteVersionsWithDifferencesUrl()).replyOnce(200, { versions: [changedVersion(1)] });
        mock.onGet(`/processes/${scenario.name}/${scenario.processVersionId}/compare/34`).replyOnce(200, {});

        renderDialog();
        await openVersionPicker();
        fireEvent.click(await screen.findByText("34 - created by admin 2024-05-31|00:00"));
        expect(await screen.findByText("dialog.compareVersions.differenceToPick:")).toBeInTheDocument();

        await switchToRemoteEnvironment();

        await waitFor(() => {
            expect(screen.queryByText("dialog.compareVersions.differenceToPick:")).not.toBeInTheDocument();
        });
    });

    it("should show the local environment's configured name in the environment picker when set", async () => {
        mock.onGet(localVersionsWithDifferencesUrl()).replyOnce(200, localVersionsWithDifferences);

        renderDialog({ store: buildStore({ environmentAlert: { content: "local environment" } }) });

        expect(await screen.findByText("dialog.compareVersions.localWithName")).toBeInTheDocument();
    });

    it("should not let the environment be switched when the version is predefined", async () => {
        mock.onGet(localVersionsWithDifferencesUrl()).replyOnce(200, localVersionsWithDifferences);
        mock.onGet(`/processes/${scenario.name}/${scenario.processVersionId}/compare/34`).replyOnce(200, {});

        renderDialog({ predefinedVersionId: "34" });

        // switching would clear the read-only version select and dead-end the dialog until it's reopened
        fireEvent.keyDown(await screen.findByText("dialog.compareVersions.local"), DOWN_ARROW);
        expect(screen.queryByText("dialog.compareVersions.remoteWithName")).not.toBeInTheDocument();
    });
});
