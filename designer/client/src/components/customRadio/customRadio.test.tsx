import React from "react";
import { render, fireEvent, screen } from "@testing-library/react";
import { CustomRadio } from "./CustomRadio";
import { NuThemeProvider } from "../../containers/theme/nuThemeProvider";
import { FormGroup } from "@mui/material";

describe("CustomRadio component", () => {
    const defaultProps = {
        label: "Test Label",
        value: "test-value",
        // eslint-disable-next-line i18next/no-literal-string
        Icon: () => <div>Icon</div>,
        disabled: false,
        active: false,
    };

    test("renders correctly with default props", () => {
        render(
            <NuThemeProvider>
                <CustomRadio {...defaultProps} />
            </NuThemeProvider>,
        );

        expect(screen.getByRole("menuitem", { name: /Icon Test Label/i })).toBeInTheDocument();
    });

    test("renders correctly when disabled", () => {
        render(
            <NuThemeProvider>
                <CustomRadio {...defaultProps} disabled />
            </NuThemeProvider>,
        );

        expect(screen.getByDisplayValue("test-value")).toBeDisabled();
    });

    test("renders correctly when active", () => {
        render(
            <NuThemeProvider>
                <CustomRadio {...defaultProps} active />
            </NuThemeProvider>,
        );

        expect(screen.getByDisplayValue("test-value")).toBeChecked();
    });

    test("fires onChange callback when clicked", () => {
        const onChangeMock = jest.fn();
        render(
            <NuThemeProvider>
                <FormGroup row sx={(theme) => ({ flexWrap: "nowrap", gap: theme.spacing(1.5) })} onChange={onChangeMock}>
                    <CustomRadio {...defaultProps} />
                </FormGroup>
            </NuThemeProvider>,
        );

        fireEvent.click(screen.getByDisplayValue("test-value"));

        expect(onChangeMock).toHaveBeenCalledWith(expect.objectContaining({ target: expect.objectContaining({ value: "test-value" }) }));
    });
});
