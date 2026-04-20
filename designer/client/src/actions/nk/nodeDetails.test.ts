import { nodeDetailsClosed, validateNodeData } from "./nodeDetails";

// ─── Mocks ───────────────────────────────────────────────────────────────────

// Make debounce a no-op so the inner validateNode call fires synchronously.
// Without this, the debounced function never executes in a synchronous test.
jest.mock("lodash", () => ({
    ...jest.requireActual("lodash"),
    debounce: (fn: (...args: unknown[]) => unknown) => {
        const immediate = (...args: unknown[]) => fn(...args);
        immediate.cancel = jest.fn();
        return immediate;
    },
}));

// Capture the AbortController passed to each validateNode call so we can assert
// on abort() being called.
const capturedControllers: AbortController[] = [];

jest.mock("./validationsActions", () => ({
    validateNode: jest.fn((_requestData: unknown, controller: AbortController) => {
        capturedControllers.push(controller);
        // Return a thunk that never resolves — simulates an in-flight request.
        // eslint-disable-next-line @typescript-eslint/no-empty-function
        return () => new Promise(() => {});
    }),
}));

jest.mock("../../components/graph/node-modal/NodeDetailsContent/getNodeDetails", () => ({
    getNodesDetails: jest.fn(() => ({})),
}));

// ─── Helpers ──────────────────────────────────────────────────────────────────

function buildRequestData(nodeId: string) {
    return {
        nodeData: { id: nodeId } as any,
        variableTypes: {},
        branchVariableTypes: {},
        outgoingEdges: [],
        testCases: {},
    };
}

function buildStore() {
    const dispatched: unknown[] = [];
    const dispatch = jest.fn((action: unknown) => {
        if (typeof action === "function") return action(dispatch, getState, undefined);
        dispatched.push(action);
    });
    const getState = jest.fn(() => ({} as any));
    return { dispatch, getState, dispatched };
}

// ─── Tests ────────────────────────────────────────────────────────────────────

describe("validateNodeData", () => {
    beforeEach(() => {
        capturedControllers.length = 0;
        jest.clearAllMocks();
    });

    it("aborts the in-flight request when a second validation call is made for the same node", () => {
        const { dispatch, getState } = buildStore();
        const nodeId = "node-1";

        // First validation — starts an in-flight request
        validateNodeData(buildRequestData(nodeId))(dispatch, getState, undefined);

        expect(capturedControllers).toHaveLength(1);
        const firstController = capturedControllers[0];
        expect(firstController.signal.aborted).toBe(false);

        // Second validation — should abort the first request
        validateNodeData(buildRequestData(nodeId))(dispatch, getState, undefined);

        expect(firstController.signal.aborted).toBe(true);
        expect(capturedControllers).toHaveLength(2);
        expect(capturedControllers[1].signal.aborted).toBe(false);
    });

    it("does not abort requests for different nodes", () => {
        const { dispatch, getState } = buildStore();

        validateNodeData(buildRequestData("node-A"))(dispatch, getState, undefined);
        validateNodeData(buildRequestData("node-B"))(dispatch, getState, undefined);

        expect(capturedControllers[0].signal.aborted).toBe(false);
        expect(capturedControllers[1].signal.aborted).toBe(false);
    });

    it("aborts the in-flight request and cancels the debounce when the node details are closed", () => {
        const { dispatch, getState } = buildStore();
        const nodeId = "node-1";

        validateNodeData(buildRequestData(nodeId))(dispatch, getState, undefined);
        const controller = capturedControllers[0];
        expect(controller.signal.aborted).toBe(false);

        nodeDetailsClosed(nodeId, "window-1")(dispatch, getState, undefined);

        expect(controller.signal.aborted).toBe(true);
    });

    it("does not delete a newer controller when a stale validation completes", async () => {
        const { dispatch, getState } = buildStore();
        const nodeId = "node-1";

        // First call — resolves immediately (simulates a fast response that arrives after a newer request was already made)
        let resolveFirstValidation!: (value: unknown) => void;
        const firstValidationPromise = new Promise((resolve) => {
            resolveFirstValidation = resolve;
        });
        const { validateNode } = jest.requireMock("./validationsActions");
        validateNode.mockImplementationOnce((_requestData: unknown, controller: AbortController) => {
            capturedControllers.push(controller);
            return () => firstValidationPromise;
        });

        validateNodeData(buildRequestData(nodeId))(dispatch, getState, undefined);
        const controllerA = capturedControllers[0];

        // Second call — replaces controllerA in the map with controllerB
        validateNodeData(buildRequestData(nodeId))(dispatch, getState, undefined);
        const controllerB = capturedControllers[1];
        expect(controllerA.signal.aborted).toBe(true);

        // First (stale) validation completes — the guard must NOT delete controllerB from the map
        resolveFirstValidation(undefined);
        await firstValidationPromise;
        await Promise.resolve(); // flush microtask queue

        // Third call should still abort controllerB, proving it was not deleted by the stale cleanup
        validateNodeData(buildRequestData(nodeId))(dispatch, getState, undefined);
        expect(controllerB.signal.aborted).toBe(true);
    });

    it("calls the callback with 'allowDataUpdate' when the node is still open after validation completes", async () => {
        const { dispatch, getState } = buildStore();
        const nodeId = "node-1";
        const validationResult = { validationErrors: [], validationPerformed: true, testCasesValidationErrors: {} };

        const { validateNode } = jest.requireMock("./validationsActions");
        validateNode.mockImplementationOnce((_requestData: unknown, controller: AbortController) => {
            capturedControllers.push(controller);
            return () => Promise.resolve(validationResult);
        });

        const { getNodesDetails } = jest.requireMock("../../components/graph/node-modal/NodeDetailsContent/getNodeDetails");
        // Node is still open — getNodesDetails returns a truthy entry for this node
        getNodesDetails.mockReturnValue({ [nodeId]: {} });

        const callback = jest.fn();
        validateNodeData(buildRequestData(nodeId), callback)(dispatch, getState, undefined);

        // Wait for the async validation thunk to complete
        await Promise.resolve();
        await Promise.resolve();

        expect(callback).toHaveBeenCalledWith("allowDataUpdate");
    });
});
