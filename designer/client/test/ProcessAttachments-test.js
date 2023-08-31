import React from "react";
import { ProcessAttachments } from "../src/components/ProcessAttachments"; //import redux-independent component
import configureMockStore from "redux-mock-store";
import thunk from "redux-thunk";
import { Provider } from "react-redux";
import * as selectors from "../src/reducers/selectors/other";
import { render } from "@testing-library/react";

const mockStore = configureMockStore([thunk]);

jest.mock("../src/containers/theme");
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
    processId: "proc1",
    processVersionId: 1,
    createDate: "2016-10-10T12:39:44.092",
    user: "TouK",
    fileName: `file ${id}`,
});

describe("ProcessAttachments suite", () => {
    it("should render with no problems", () => {
        const store = mockStore({
            graphReducer: { history: { present: { fetchedProcessDetails: { name: "proc1", processVersionId: 1 } } } },
            processActivity: { attachments: [processAttachment(3), processAttachment(2), processAttachment(1)] },
        });

        const { container } = render(
            <Provider store={store}>
                <ProcessAttachments />
            </Provider>,
        );

        expect(container.getElementsByClassName("download-attachment").length).toBe(3);
    });
});
