import { jest } from "@jest/globals";
import { NodeValidationError } from "../../src/types";
import { FieldError } from "../../src/components/graph/node-modal/editors/Validators";

export const mockFormatter = { encode: jest.fn(() => "test"), decode: jest.fn(() => "test") };
export const mockFieldError: FieldError = {
    description: "HandledErrorType.EmptyMandatoryParameter",
    message: "validation error",
};
export const mockValueChange = jest.fn();
