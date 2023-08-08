import { jest } from "@jest/globals";
import { NodeValidationError } from "../../src/types";

export const mockFormatter = { encode: jest.fn(() => "test"), decode: jest.fn(() => "test") };
export const mockErrors: NodeValidationError[] = [
    {
        description: "HandledErrorType.EmptyMandatoryParameter",
        message: "validation error",
        typ: "EmptyMandatoryParameter",
        errorType: "SaveAllowed",
        fieldName: "name",
    },
];
export const mockValueChange = jest.fn();
