import { Item } from "../../../src/components/graph/node-modal/fragment-input-definition/item";
import React from "react";
import { NodeRowFieldsProvider } from "../../../src/components/graph/node-modal/node-row-fields-provider";
import { NuThemeProvider } from "../../../src/containers/theme/nuThemeProvider";
import { jest } from "@jest/globals";
import { fireEvent, render, screen } from "@testing-library/react";
import { ReturnedType } from "../../../src/types";

const DOWN_ARROW = { keyCode: 40 };

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

describe(Item.name, () => {
    it("should set inputMode to a default value when parameter type change", async () => {
        const mockOnChange = jest.fn();
        render(
            <NuThemeProvider>
                <NodeRowFieldsProvider label={"test"} path={""}>
                    <Item
                        index={0}
                        item={{
                            uuid: "fc92fce2-5e63-40fd-808f-4942468ab995",
                            name: "",
                            required: false,
                            hintText: "",
                            initialValue: null,
                            valueEditor: null,
                            typ: {
                                refClazzName: "java.lang.String",
                            } as ReturnedType,
                        }}
                        errors={[]}
                        variableTypes={{}}
                        onChange={mockOnChange}
                        options={[
                            {
                                value: "java.lang.Boolean",
                                label: "Boolean",
                            },
                            {
                                value: "java.lang.String",
                                label: "String",
                            },
                        ]}
                        namespace={"test"}
                    />
                </NodeRowFieldsProvider>
            </NuThemeProvider>,
        );

        fireEvent.keyDown(screen.getByText("String"), DOWN_ARROW);

        fireEvent.click(await screen.findByText("Boolean"));

        expect(mockOnChange).toHaveBeenNthCalledWith(1, "test[0].typ.refClazzName", "java.lang.Boolean");
        expect(mockOnChange).toHaveBeenNthCalledWith(2, "test[0].valueEditor", null);
    });
});
