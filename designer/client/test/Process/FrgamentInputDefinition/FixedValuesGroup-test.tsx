import React from "react";

import { fireEvent, render, screen } from "@testing-library/react";
import { jest } from "@jest/globals";
import { NuThemeProvider } from "../../../src/containers/theme/nuThemeProvider";
import { FixedValuesGroup } from "../../../src/components/graph/node-modal/fragment-input-definition/settings/variants/fields/FixedValuesGroup";
import { FixedValuesType } from "../../../src/components/graph/node-modal/fragment-input-definition/item";
import { ReturnedType } from "../../../src/types";

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
                    fixedValuesType={FixedValuesType.ValueInputWithDictEditor}
                    readOnly={false}
                    item={{
                        uuid: "fc92fce2-5e63-40fd-808f-4942468ab995",
                        name: "",
                        required: false,
                        hintText: "",
                        initialValue: null,
                        valueEditor: null,
                        valueCompileTimeValidation: null,
                        typ: {
                            refClazzName: "java.lang.String",
                        } as ReturnedType,
                    }}
                />
            </NuThemeProvider>,
        );

        fireEvent.click(screen.getByRole("radio", { name: "fragment.settings.userDefinedList" }));

        expect(mockOnChange).toHaveBeenNthCalledWith(1, "test.initialValue", null);
        expect(mockOnChange).toHaveBeenNthCalledWith(2, "test.valueEditor.type", "ValueInputWithFixedValuesProvided");
    });
});
