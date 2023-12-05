import React from "react";

import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, jest } from "@jest/globals";
import { NuThemeProvider } from "../../../src/containers/theme/nuThemeProvider";
import { FixedValuesGroup } from "../../../src/components/graph/node-modal/fragment-input-definition/settings/variants/fields/FixedValuesGroup";
import { FixedValuesType } from "../../../src/components/graph/node-modal/fragment-input-definition/item";

jest.mock("../../../src/brace/theme/nussknacker.js", () => ({}));

jest.mock("react-cron-generator", () => (props) => <div {...props} />);

jest.mock("react-i18next", () => ({
    useTranslation: () => ({
        t: (key) => key,
        i18n: {
            changeLanguage: () => {},
        },
    }),
}));

describe(FixedValuesGroup.name, () => {
    it("should clear initialValue on Preset type change", async () => {
        const mockOnChange = jest.fn();

        render(
            <NuThemeProvider>
                <FixedValuesGroup
                    onChange={mockOnChange}
                    path={"test"}
                    fixedValuesType={FixedValuesType.ValueInputWithFixedValuesPreset}
                    readOnly={false}
                />
            </NuThemeProvider>,
        );

        fireEvent.click(screen.getByRole("radio", { name: "fragment.settings.userDefinedList" }));

        expect(mockOnChange).toHaveBeenNthCalledWith(1, "test.initialValue", null);
        expect(mockOnChange).toHaveBeenNthCalledWith(2, "test.valueEditor.type", "ValueInputWithFixedValuesProvided");
    });
});
