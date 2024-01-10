import React from "react";
import { ProcessAttachments } from "../src/components/processAttach/ProcessAttachments"; //import redux-independent component
import configureMockStore from "redux-mock-store";
import thunk from "redux-thunk";
import { Provider } from "react-redux";
import * as selectors from "../src/reducers/selectors/other";
import { render } from "@testing-library/react";
import { NuThemeProvider } from "../src/containers/theme/nuThemeProvider";

const mockStore = configureMockStore([thunk]);

jest.spyOn(selectors, "getCapabilities").mockReturnValue({ write: true });

jest.mock("react-i18next", () => ({
    useTranslation: () => ({
        t: (key) => key,
        i18n: {
            changeLanguage: () => {},
        },
    }),
}));

const processAttachment = (id) => ({
    id: `${id}`,
    processVersionId: 1,
    createDate: "2016-10-10T12:39:44.092",
    user: "TouK",
    fileName: `file ${id}`,
});

describe("ProcessAttachments suite", () => {
    it("should render with no problems", () => {
        const store = mockStore({
            graphReducer: { history: { present: { scenario: { name: "proc1", processVersionId: 1 } } } },
            processActivity: { attachments: [processAttachment(3), processAttachment(2), processAttachment(1)] },
        });

        const { container } = render(
            <NuThemeProvider>
                <Provider store={store}>
                    <ProcessAttachments />
                </Provider>
            </NuThemeProvider>,
        );

        expect(container.getElementsByClassName("download-attachment").length).toBe(3);
    });
});
