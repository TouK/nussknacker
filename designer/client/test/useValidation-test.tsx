import { configureStore } from "@reduxjs/toolkit";
import { act, renderHook } from "@testing-library/react";
import { Provider } from "react-redux";
import { validateNodeData } from "../src/actions/nk/nodeDetails";
import { useValidation } from "../src/components/graph/node-modal/useNodeTypeDetailsContentLogic";
import type { Action } from "../src/actions/reduxTypes";
import { reducer as nodeDetailsReducer } from "../src/reducers/nodeDetailsState";
import type { NodeType } from "../src/types/node";
import type { NodeValidationError } from "../src/types/validation";
import React from "react";

jest.mock("../src/actions/nk/nodeDetails", () => ({
    ...jest.requireActual("../src/actions/nk/nodeDetails"),
    validateNodeData: jest.fn(),
}));

const mockValidateNodeData = validateNodeData as jest.MockedFunction<typeof validateNodeData>;

// ─── Fixtures ────────────────────────────────────────────────────────────────

const NODE_ID = "filter-node";

const makeNode = (expression = "#input.amount > 100"): NodeType =>
    ({
        type: "Filter",
        id: NODE_ID,
        name: NODE_ID,
        expression: { language: "spel", expression },
    } as unknown as NodeType);

const initialErrors: NodeValidationError[] = [
    {
        fieldName: "expression",
        message: "Invalid expression on mount",
        description: "",
        typ: "ExpressionParserCompilationError",
        errorType: "SaveNotAllowed",
    },
];

const updatedErrors: NodeValidationError[] = [
    {
        fieldName: "expression",
        message: "Error after node change",
        description: "",
        typ: "MissingRequiredProperty",
        errorType: "SaveNotAllowed",
    },
];

// ─── Helpers ─────────────────────────────────────────────────────────────────

/**
 * Returns a synchronous thunk that immediately dispatches NODE_VALIDATION_UPDATED.
 */
const makeSyncValidationThunk =
    (errors: NodeValidationError[]) =>
    (requestData: Parameters<typeof validateNodeData>[0], callback?: Parameters<typeof validateNodeData>[1]) =>
    (dispatch: (a: unknown) => void) => {
        dispatch({
            type: "NODE_VALIDATION_UPDATED",
            nodeId: requestData.nodeData.id,
            validationData: {
                validationErrors: errors,
                validationPerformed: true,
                testCasesValidationErrors: {},
            },
        });
        callback?.("allowDataUpdate");
    };

/**
 * Store that uses the real nodeDetailsReducer for nodeDetails so we can
 * observe validation state changes, while the rest of state is static/minimal.
 */
const buildStore = () => {
    const staticState = {
        graphReducer: {
            present: {
                scenario: {
                    name: "test-scenario",
                    scenarioGraph: {
                        nodes: [],
                        edges: [],
                        properties: { name: "test-scenario", additionalFields: {} },
                        testCases: { list: [] },
                    },
                    validationResult: { nodeResults: {} },
                },
                testing: {
                    testCasesResults: {},
                    activeTestCaseId: null,
                },
            },
        },
        settings: {
            processDefinitionData: { components: {}, scenarioProperties: {} },
            featuresSettings: { testCases: { multipleEnabled: false } },
            loading: false,
        },
        userSettings: {
            values: {},
            defaults: {},
        },
        nodeDetails: {} as ReturnType<typeof nodeDetailsReducer>,
    };

    return configureStore({
        reducer: (state = staticState, action) => ({
            ...staticState,
            nodeDetails: nodeDetailsReducer(state.nodeDetails, action as Action),
        }),
        devTools: false,
    });
};

const renderValidationHook = (
    store: ReturnType<typeof buildStore>,
    initialProps: { node: NodeType; edges: []; showValidation: boolean },
) => {
    const wrapper = ({ children }: { children: React.ReactNode }) => <Provider store={store}>{children}</Provider>;
    return renderHook(({ node, edges, showValidation }) => useValidation({ node, edges, showValidation }), {
        wrapper,
        initialProps,
    });
};

// ─── Tests ───────────────────────────────────────────────────────────────────

describe("useValidation", () => {
    beforeEach(() => {
        mockValidateNodeData.mockClear();
    });

    it("dispatches validation on mount with the initial node", async () => {
        mockValidateNodeData.mockImplementation(makeSyncValidationThunk(initialErrors));

        const store = buildStore();
        const node = makeNode();

        await act(async () => {
            renderValidationHook(store, { node, edges: [], showValidation: true });
        });

        expect(mockValidateNodeData).toHaveBeenCalledTimes(1);
        expect(mockValidateNodeData).toHaveBeenCalledWith(expect.objectContaining({ nodeData: node }), expect.any(Function));
    });

    it("re-dispatches validation when node changes and reports updated error messages", async () => {
        mockValidateNodeData.mockImplementationOnce(makeSyncValidationThunk(initialErrors));

        const store = buildStore();
        const node = makeNode();

        const { rerender } = await act(async () => renderValidationHook(store, { node, edges: [], showValidation: true }));

        mockValidateNodeData.mockImplementationOnce(makeSyncValidationThunk(updatedErrors));
        const changedNode = makeNode("#input.amount > 200");

        await act(async () => {
            rerender({ node: changedNode, edges: [], showValidation: true });
        });

        expect(mockValidateNodeData).toHaveBeenCalledTimes(2);
        expect(mockValidateNodeData).toHaveBeenLastCalledWith(expect.objectContaining({ nodeData: changedNode }), expect.any(Function));

        const errors = store.getState().nodeDetails[NODE_ID]?.validationErrors;
        // Each error gets a requestId injected by the reducer — use toMatchObject to ignore it
        expect(errors).toMatchObject(updatedErrors);
    });

    it("does not dispatch validation when showValidation is false", async () => {
        const store = buildStore();
        const node = makeNode();

        await act(async () => {
            renderValidationHook(store, { node, edges: [], showValidation: false });
        });

        expect(mockValidateNodeData).not.toHaveBeenCalled();
    });
});
