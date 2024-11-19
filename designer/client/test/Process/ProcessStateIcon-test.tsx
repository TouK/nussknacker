import React from "react";
import ProcessStateIcon from "../../src/components/Process/ProcessStateIcon";
import { jest } from "@jest/globals";
import { render, waitFor } from "@testing-library/react";

const processState = {
    allowedActions: ["DEPLOY"],
    icon: "/states/stopping-success.svg",
    status: { type: "StoppedStateStatus", name: "CANCELED" },
    tooltip: "The scenario has been successfully cancelled.",
};

const noDataProcessState = {
    allowedActions: ["DEPLOY"],
    status: { type: "StoppedStateStatus", name: "CANCELED" },
};

global.fetch = jest.fn((url) =>
    Promise.resolve({
        text: () => Promise.resolve(`<svg data-testid="svg">${url}</svg>`),
    }),
) as any;

describe("ProcessStateIcon tests", () => {
    it("should show defaults for missing process.state and stateProcess", async () => {
        const scenario = { processingType: "streaming" };
        const { getByTestId, container } = render(<ProcessStateIcon scenario={scenario as any} />);
        expect(fetch).toHaveBeenLastCalledWith("/be-static/assets/states/status-unknown.svg");
        await waitFor(() => getByTestId("svg"));
        expect(container).toMatchSnapshot();
    });

    it("should show defaults for loaded process.state without data", async () => {
        const scenario = { processingType: "streaming", state: noDataProcessState };
        const { getByTestId, container } = render(<ProcessStateIcon scenario={scenario as any} />);
        await waitFor(() => getByTestId("svg"));
        expect(fetch).not.toHaveBeenCalled(); //cached
        expect(container).toMatchSnapshot();
    });

    it("should show data from loaded process.state", async () => {
        const scenario = { processingType: "streaming", state: processState };
        const { getByTestId, container } = render(<ProcessStateIcon scenario={scenario as any} />);
        expect(fetch).toHaveBeenLastCalledWith("/be-static/states/stopping-success.svg");
        await waitFor(() => getByTestId("svg"));
        expect(container).toMatchSnapshot();
    });

    it("should show defaults if loadedProcess is empty", async () => {
        const { getByTestId, container } = render(<ProcessStateIcon scenario={{} as any} />);
        await waitFor(() => getByTestId("svg"));
        expect(fetch).not.toHaveBeenCalled(); //cached
        expect(container).toMatchSnapshot();
    });

    it("should show loadedProcess data", async () => {
        const { getByTestId, container } = render(
            <ProcessStateIcon scenario={noDataProcessState as any} processState={processState as any} />,
        );
        await waitFor(() => getByTestId("svg"));
        expect(fetch).not.toHaveBeenCalled(); //cached
        expect(container).toMatchSnapshot();
    });
});
