import React from "react";

import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, jest } from "@jest/globals";
import { NuThemeProvider } from "../../src/containers/theme/nuThemeProvider";
import { FixedValuesSetting } from "../../src/components/graph/node-modal/fragment-input-definition/settings/variants/fields/FixedValuesSetting";

const DOWN_ARROW = { keyCode: 40 };
const ENTER = { keyCode: 13 };

const processPropertyMock = {
    name: "a",
    typ: {
        refClazzName: "java.lang.String",
    },
    required: true,
    initialValue: null,
    hintText: "Sample hint text",
    validationExpression: "#Date.test",
    validationErrorMessage: "Sample error message",
    inputMode: "FixedList",
    fixedValuesList: [
        { label: "fixedValuesList_Field_a", expression: "#test1" },
        { label: "fixedValuesList_Field_b", expression: "#test" },
    ],
    fixedValuesListPresetId: "presetBoolean",
    fixedValuesPresets: {
        presetBoolean: [
            {
                expression: "true",
                label: "ON",
            },
            {
                expression: "false",
                label: "OFF",
            },
        ],
        presetString: [
            {
                expression: "'someOtherString'",
                label: "string1",
            },
            {
                expression: "'yetAnotherString'",
                label: "string2",
            },
        ],
    },
};

jest.mock("react-i18next", () => ({
    useTranslation: () => ({
        t: (key) => key,
        i18n: {
            changeLanguage: () => {},
        },
    }),
}));

describe(FixedValuesSetting.name, () => {
    it("should clear initialValue on Preset selection change", async () => {
        const mockOnChange = jest.fn();

        render(
            <NuThemeProvider>
                <FixedValuesSetting
                    onChange={mockOnChange}
                    fixedValuesPresets={processPropertyMock.fixedValuesPresets}
                    fixedValuesList={processPropertyMock.fixedValuesList}
                    fixedValuesListPresetId={processPropertyMock.fixedValuesListPresetId}
                    // @ts-ignore
                    fixedValuesType={"Preset"}
                    path={"test"}
                    presetSelection={"test1"}
                    readOnly={false}
                />
            </NuThemeProvider>,
        );

        fireEvent.keyDown(screen.getByText("presetBoolean"), DOWN_ARROW);

        fireEvent.click(await screen.findByText("presetString"));

        expect(mockOnChange).toHaveBeenNthCalledWith(1, "test.fixedValuesListPresetId", "presetString");
        expect(mockOnChange).toHaveBeenNthCalledWith(2, "test.initialValue", null);
    });

    it("should add new list item when the item is provided and enter clicks", async () => {
        const mockOnChange = jest.fn();

        render(
            <NuThemeProvider>
                <FixedValuesSetting
                    onChange={mockOnChange}
                    fixedValuesPresets={processPropertyMock.fixedValuesPresets}
                    fixedValuesList={processPropertyMock.fixedValuesList}
                    fixedValuesListPresetId={processPropertyMock.fixedValuesListPresetId}
                    // @ts-ignore
                    fixedValuesType={"UserDefinedList"}
                    path={"test"}
                    presetSelection={"test1"}
                    readOnly={false}
                />
            </NuThemeProvider>,
        );

        fireEvent.change(await screen.findByTestId("add-list-item"), { target: { value: "test" } });
        fireEvent.keyUp(await screen.findByTestId("add-list-item"), ENTER);

        expect(mockOnChange).toHaveBeenCalledWith("test.inputConfig.fixedValuesList", [
            { expression: "#test1", label: "fixedValuesList_Field_a" },
            { expression: "#test", label: "fixedValuesList_Field_b" },
            { expression: "test", label: "test" },
        ]);
    });

    it("should prevent adding new list items when a new item is already on the list", async () => {
        const mockOnChange = jest.fn();

        render(
            <NuThemeProvider>
                <FixedValuesSetting
                    onChange={mockOnChange}
                    fixedValuesPresets={processPropertyMock.fixedValuesPresets}
                    fixedValuesList={processPropertyMock.fixedValuesList}
                    fixedValuesListPresetId={processPropertyMock.fixedValuesListPresetId}
                    // @ts-ignore
                    fixedValuesType={"UserDefinedList"}
                    path={"test"}
                    presetSelection={"test1"}
                    readOnly={false}
                />
            </NuThemeProvider>,
        );

        fireEvent.change(await screen.findByTestId("add-list-item"), { target: { value: "fixedValuesList_Field_a" } });
        fireEvent.keyUp(await screen.findByTestId("add-list-item"), ENTER);

        expect(mockOnChange).not.toHaveBeenCalled();
    });

    it("should prevent adding new list items when a new item is an empty value", async () => {
        const mockOnChange = jest.fn();

        render(
            <NuThemeProvider>
                <FixedValuesSetting
                    onChange={mockOnChange}
                    fixedValuesPresets={processPropertyMock.fixedValuesPresets}
                    fixedValuesList={processPropertyMock.fixedValuesList}
                    fixedValuesListPresetId={processPropertyMock.fixedValuesListPresetId}
                    // @ts-ignore
                    fixedValuesType={"UserDefinedList"}
                    path={"test"}
                    presetSelection={"test1"}
                    readOnly={false}
                />
            </NuThemeProvider>,
        );

        fireEvent.change(await screen.findByTestId("add-list-item"), { target: { value: "" } });
        fireEvent.keyUp(await screen.findByTestId("add-list-item"), ENTER);

        expect(mockOnChange).not.toHaveBeenCalled();
    });
});
