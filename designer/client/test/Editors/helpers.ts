import { jest } from "@jest/globals";
import { HandledErrorType } from "../../src/components/graph/node-modal/editors/Validators";

export const mockFormatter = { encode: jest.fn(() => "test"), decode: jest.fn(() => "test") };
export const mockValidators = [
    {
        description: () => "HandledErrorType.EmptyMandatoryParameter",
        handledErrorType: HandledErrorType.EmptyMandatoryParameter,
        validatorType: 0,
        isValid: () => false,
        message: () => "validation error",
    },
];
export const mockValueChange = jest.fn();
