import React from "react";

import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, jest } from "@jest/globals";
import { NuThemeProvider } from "../../src/containers/theme/nuThemeProvider";
import PresetTypeGroup from "../../src/components/graph/node-modal/fragment-input-definition/settings/variants/fields/PresetTypeGroup";

jest.mock("../../src/brace/theme/nussknacker.js", () => ({}));

jest.mock("react-cron-generator", () => (props) => <div {...props} />);

jest.mock("react-i18next", () => ({
    useTranslation: () => ({
        t: (key) => key,
        i18n: {
            changeLanguage: () => {},
        },
    }),
}));

describe(PresetTypeGroup.name, () => {
    it("should clear initialValue on Preset type change", async () => {
        const mockOnChange = jest.fn();
        const mockSetPresetType = jest.fn();

        render(
            <NuThemeProvider>
                <PresetTypeGroup
                    onChange={mockOnChange}
                    path={"test"}
                    // @ts-ignore
                    presetType={"Preset"}
                    setPresetType={mockSetPresetType}
                />
            </NuThemeProvider>,
        );

        fireEvent.click(screen.getByRole("radio", { name: "fragment.settings.userDefinedList" }));

        expect(mockOnChange).toHaveBeenCalledWith("test.initialValue", "");
    });
});
