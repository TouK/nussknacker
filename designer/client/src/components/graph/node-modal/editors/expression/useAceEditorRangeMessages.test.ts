import { renderHook } from "@testing-library/react";

import { useAppSelector } from "../../../../../store/storeHelpers";
import type { NodeValidationError } from "../../../../../types/validation";
import type { FieldError } from "../Validators";
import { useAceEditorRangeMessages } from "./useAceEditorRangeMessages";

jest.mock("../../../../../store/storeHelpers", () => ({
    useAppSelector: jest.fn(),
}));

const mockError = (error: Partial<NodeValidationError>): FieldError => ({
    message: "",
    description: "",
    ...error,
});

describe("useAceEditorRangeMessages", () => {
    beforeEach(() => {
        (useAppSelector as unknown as jest.Mock).mockReturnValue({
            "editor.showRangeMessages": true,
        });
    });

    afterEach(() => {
        jest.clearAllMocks();
    });

    it("should return empty annotations and markers when showRangeMessages is false", () => {
        (useAppSelector as unknown as jest.Mock).mockReturnValue({
            "editor.showRangeMessages": false,
        });
        const { result } = renderHook(() => useAceEditorRangeMessages([], true));
        expect(result.current.annotations).toEqual([]);
        expect(result.current.markers).toEqual([]);
    });

    it("should correctly process errors with CoordinatesBasedTextRange", () => {
        const fieldErrors: FieldError[] = [
            mockError({
                message: "Error message 1",
                details: {
                    type: "CoordinatesBasedTextRange",
                    start: { row: 1, column: 5 },
                    end: { row: 1, column: 10 },
                },
            }),
        ];
        const { result } = renderHook(() => useAceEditorRangeMessages(fieldErrors, true));

        expect(result.current.annotations).toHaveLength(1);
        expect(result.current.annotations[0]).toMatchObject({
            row: 1,
            column: 5,
            type: "error",
            text: "Error message 1",
        });

        expect(result.current.markers).toHaveLength(1);
        expect(result.current.markers[0]).toMatchObject({
            startRow: 1,
            startCol: 5,
            endRow: 1,
            endCol: 10,
            className: "ace-error-marker",
            type: "text",
            inFront: false,
        });
    });

    it("should correctly process errors with '[character X line Y]' format", () => {
        const fieldErrors = [
            mockError({
                message: "Error at parsing: Expected a ',' or '}' at 805 [character 16 line 37].",
            }),
        ];
        const { result } = renderHook(() => useAceEditorRangeMessages(fieldErrors, true));

        expect(result.current.annotations).toHaveLength(1);
        expect(result.current.annotations[0]).toMatchObject({
            row: 36, // 37 - 1
            column: 15, // 16 - 1
            type: "error",
            text: "Error at parsing: Expected a ',' or '}' at 805 [character 16 line 37].",
        });

        expect(result.current.markers).toHaveLength(1);
        expect(result.current.markers[0]).toMatchObject({
            startRow: 36, // 37 - 1
            startCol: 15, // 16 - 1
            endRow: 36, // 37 - 1
            endCol: 16, // 16 (startCol + 1)
            className: "ace-error-marker",
            type: "text",
            inFront: false,
        });
    });

    it("should correctly process errors with '(line Y, column X)' format", () => {
        const fieldErrors = [
            mockError({
                message: "expected json value got 'a[\"ava...' (line 37, column 15)",
            }),
        ];
        const { result } = renderHook(() => useAceEditorRangeMessages(fieldErrors, true));

        expect(result.current.annotations).toHaveLength(1);
        expect(result.current.annotations[0]).toMatchObject({
            row: 36, // 37 - 1
            column: 14, // 15 - 1
            type: "error",
            text: "expected json value got 'a[\"ava...' (line 37, column 15)",
        });

        expect(result.current.markers).toHaveLength(1);
        expect(result.current.markers[0]).toMatchObject({
            startRow: 36, // 37 - 1
            startCol: 14, // 15 - 1
            endRow: 36, // 37 - 1
            endCol: 15, // 15 (startCol + 1)
            className: "ace-error-marker",
            type: "text",
        });
    });

    it("should ignore errors that do not match any known format", () => {
        const fieldErrors = [
            mockError({
                message: "Generic error message without coordinates.",
            }),
        ];
        const { result } = renderHook(() => useAceEditorRangeMessages(fieldErrors, true));

        expect(result.current.annotations).toEqual([]);
        expect(result.current.markers).toEqual([]);
    });

    it("should handle a mix of error types", () => {
        const fieldErrors = [
            mockError({
                message: "Error message 1",
                details: {
                    type: "CoordinatesBasedTextRange",
                    start: { row: 0, column: 0 },
                    end: { row: 0, column: 5 },
                },
            }),
            mockError({
                message: "Error message 2 [character 1 line 1]",
            }),
            mockError({
                message: "Error message 3 (line 2, column 2)",
            }),
            mockError({
                message: "Error message 4 - unparseable",
            }),
        ];

        const { result } = renderHook(() => useAceEditorRangeMessages(fieldErrors, true));

        expect(result.current.annotations).toHaveLength(3);
        expect(result.current.markers).toHaveLength(3);

        expect(result.current.annotations[0]).toMatchObject({ row: 0, column: 0 });
        expect(result.current.annotations[1]).toMatchObject({ row: 0, column: 0 }); // [character 1 line 1] => (0,0)
        expect(result.current.annotations[2]).toMatchObject({ row: 1, column: 1 }); // (line 2, column 2) => (1,1)

        expect(result.current.markers[0]).toMatchObject({ startRow: 0, startCol: 0, endRow: 0, endCol: 5 });
        expect(result.current.markers[1]).toMatchObject({ startRow: 0, startCol: 0, endRow: 0, endCol: 1 });
        expect(result.current.markers[2]).toMatchObject({ startRow: 1, startCol: 1, endRow: 1, endCol: 2 });
    });
});
