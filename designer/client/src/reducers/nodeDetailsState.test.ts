import type { NodeValidationError } from "../types/validation";
import { reducer } from "./nodeDetailsState";

const existingError: NodeValidationError = {
    fieldName: "expression",
    message: "Bad expression",
    description: "",
    typ: "",
    errorType: "SaveAllowed",
};

const nodeId = "node-1";

const stateWithErrors = {
    [nodeId]: {
        parameters: [],
        validationErrors: [existingError],
        validationPerformed: true,
        changingDynamicParameters: [],
        testCasesValidationErrors: {},
    },
};

describe("nodeDetailsState reducer", () => {
    describe("VALIDATE_NODE", () => {
        it("does not clear existing validation errors while new validation is in-flight", () => {
            const result = reducer(stateWithErrors, { type: "VALIDATE_NODE", node: { id: nodeId } as any });
            expect(result?.[nodeId]?.validationErrors).toEqual([existingError]);
        });

        it("preserves all other node state", () => {
            const result = reducer(stateWithErrors, { type: "VALIDATE_NODE", node: { id: nodeId } as any });
            expect(result?.[nodeId]?.validationPerformed).toBe(true);
            expect(result?.[nodeId]?.parameters).toEqual([]);
        });
    });

    describe("NODE_VALIDATION_UPDATED", () => {
        it("replaces errors atomically when the validation response arrives", () => {
            const newError: NodeValidationError = {
                fieldName: "output",
                message: "New error",
                description: "",
                typ: "",
                errorType: "SaveAllowed",
            };

            const result = reducer(stateWithErrors, {
                type: "NODE_VALIDATION_UPDATED",
                nodeId,
                validationData: {
                    validationErrors: [newError],
                    validationPerformed: true,
                    testCasesValidationErrors: {},
                },
            });

            expect(result?.[nodeId]?.validationErrors).toEqual([newError]);
        });

        it("clears errors when validation response contains no errors", () => {
            const result = reducer(stateWithErrors, {
                type: "NODE_VALIDATION_UPDATED",
                nodeId,
                validationData: {
                    validationErrors: [],
                    validationPerformed: true,
                    testCasesValidationErrors: {},
                },
            });

            expect(result?.[nodeId]?.validationErrors).toEqual([]);
        });
    });

    describe("NODE_DETAILS_OPENED", () => {
        it("initialises state with empty errors for a newly opened node", () => {
            const result = reducer({}, { type: "NODE_DETAILS_OPENED", nodeId, windowId: "w1" });
            expect(result?.[nodeId]?.validationErrors).toEqual([]);
            expect(result?.[nodeId]?.validationPerformed).toBe(false);
        });
    });
});
