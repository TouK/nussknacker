import React from "react";

import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, jest } from "@jest/globals";
import { NuThemeProvider } from "../../src/containers/theme/nuThemeProvider";
import PresetTypesSetting from "../../src/components/graph/node-modal/fragment-input-definition/settings/variants/fields/PresetTypesSetting";

const DOWN_ARROW = { keyCode: 40 };

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

describe(PresetTypesSetting.name, () => {
    it("should clear initialValue on Preset selection change", async () => {
        const mockOnChange = jest.fn();

        render(
            <NuThemeProvider>
                <PresetTypesSetting
                    onChange={mockOnChange}
                    fixedValuesPresets={processPropertyMock.fixedValuesPresets}
                    fixedValuesList={processPropertyMock.fixedValuesList}
                    fixedValuesListPresetId={processPropertyMock.fixedValuesListPresetId}
                    // @ts-ignore
                    presetType={"Preset"}
                    path={"test"}
                    presetSelection={"test1"}
                />
            </NuThemeProvider>,
        );

        fireEvent.keyDown(screen.getByText("presetBoolean"), DOWN_ARROW);

        fireEvent.click(await screen.findByText("presetString"));

        expect(mockOnChange).toHaveBeenNthCalledWith(1, "test.fixedValuesListPresetId", "presetString");
        expect(mockOnChange).toHaveBeenNthCalledWith(2, "test.initialValue", "");
    });
});
