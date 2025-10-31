import React from "react";
import { render, screen } from "@testing-library/react";
import ValidationLabels from "../src/components/modals/ValidationLabels";
import { TestProviders } from "./Editors/TestProviders";

describe(ValidationLabels.name, () => {
    it("should display validation error when error available", () => {
        //given
        const fieldErrors = [
            {
                message: "error message",
                description: "error description",
            },
        ];

        //when
        render(
            <TestProviders>
                <ValidationLabels fieldErrors={fieldErrors} />
            </TestProviders>,
        );

        //then
        expect(screen.getByText(fieldErrors[0].message)).toBeInTheDocument();
    });

    it("should display validation error when validationLabelInfo provided AND validation error available", () => {
        //given
        const fieldErrors = [
            {
                message: "error message",
                description: "error description",
            },
        ];
        const validationLabelInfo = "validation label info visible";

        //when
        render(
            <TestProviders>
                <ValidationLabels fieldErrors={fieldErrors} validationLabelInfo={validationLabelInfo} />
            </TestProviders>,
        );

        //then
        expect(screen.getByText(fieldErrors[0].message)).toBeInTheDocument();
        expect(screen.queryByText(validationLabelInfo)).not.toBeInTheDocument();
    });
    it("should display multiple validation errors when multiple validation errors provided", () => {
        //given
        const fieldErrors = [
            {
                message: "error message",
                description: "error description",
            },
            {
                message: "error message next",
                description: "error description next",
            },
        ];
        const validationLabelInfo = "validation label info visible";

        //when
        render(
            <TestProviders>
                <ValidationLabels fieldErrors={fieldErrors} validationLabelInfo={validationLabelInfo} />
            </TestProviders>,
        );

        //then
        expect(screen.getByText(fieldErrors[0].message)).toBeInTheDocument();
        expect(screen.getByText(fieldErrors[1].message)).toBeInTheDocument();
    });
});
