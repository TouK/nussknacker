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

const mockStore = configureMockStore([thunk]);
const graphReducer = {
    history: {
        present: {
            scenario: {
                name: "proc1",
                processVersionId: 4,
                history: [
                    {
                        processVersionId: 35,
                        createDate: "2024-05-31T07:52:42.660408Z",
                        user: "admin",
                        modelVersion: 4,
                        actions: [],
                    },
                    {
                        processVersionId: 34,
                        createDate: "2024-05-31T07:40:19.115208Z",
                        user: "admin",
                        modelVersion: 4,
                        actions: [],
                    },
                ],
            },
        },
    },
};

const store = mockStore({
    graphReducer,
    settings: { featuresSettings: { remoteEnvironment: { targetEnvironmentId: "remote environment" } } },
});

describe(CompareVersionsDialog.name, () => {
    afterAll(() => {
        mock.resetHandlers();
    });

    it("should provide remote prefix for remote options", async () => {
        const remoteVersions: ProcessVersionType[] = [
            {
                processVersionId: 1,
                createDate: "2024-05-31",
                user: "test",
                modelVersion: 1,
                actions: [],
            },
        ];

        mock.onGet(`/remoteEnvironment/${graphReducer.history.present.scenario.name}/versions`).reply(200, remoteVersions);

        mock.onGet(
            `/remoteEnvironment/${graphReducer.history.present.scenario.name}/${graphReducer.history.present.scenario.processVersionId}/compare/${remoteVersions[0].processVersionId}`,
        ).reply(200, {});

        render(
            <NuThemeProvider>
                <Provider store={store}>
                    <CompareVersionsDialog
                        data={{
                            title: "compare versions",
                            kind: 12,
                            id: "8b0a9e43-9d18-4837-950c-858d35b7c60c",
                        }}
                    />
                </Provider>
            </NuThemeProvider>,
        );

        const DOWN_ARROW = { keyCode: 40 };
        fireEvent.keyDown(screen.getByText("Select..."), DOWN_ARROW);

        expect(await screen.findByText("34 - created by admin 2024-05-31|09:40")).toBeInTheDocument();

        const remoteItemText = "1 on remote environment - created by test 2024-05-31|00:00";

        await waitFor(() => {
            fireEvent.click(screen.getByText(remoteItemText));
        });

        expect(await screen.findByText("Difference to pick")).toBeInTheDocument();
        expect(await screen.findByText(remoteItemText)).toBeInTheDocument();
    });
});
