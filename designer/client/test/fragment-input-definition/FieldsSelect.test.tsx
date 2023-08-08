import * as React from "react";

import FieldsSelect from "../../src/components/graph/node-modal/fragment-input-definition/FieldsSelect";
import { render, screen, within } from "@testing-library/react";
import { jest } from "@jest/globals";

jest.mock("../../src/containers/theme", () => ({
    useNkTheme: () => ({
        withFocus: true,
        theme: {
            colors: {
                accent: "red",
            },
        },
    }),
}));

describe(FieldsSelect.name, () => {
    it("should display validation error when the field is required", () => {
        render(
            <FieldsSelect
                fields={[
                    {
                        name: "",
                        typ: {
                            refClazzName: "java.lang.String",
                            params: [],
                            display: "",
                            type: "",
                        },
                        uuid: "1",
                    },
                    {
                        name: "test",
                        typ: {
                            refClazzName: "java.lang.String",
                            params: [],
                            display: "",
                            type: "",
                        },
                        uuid: "2",
                    },
                ]}
                addField={jest.fn()}
                removeField={jest.fn()}
                onChange={jest.fn()}
                label={"field"}
                showValidation
                namespace={"field"}
                options={[]}
            />,
        );

        expect(within(screen.getByTestId("fieldsRow:0")).getByRole("textbox")).toHaveClass("node-input-with-error");
    });
});
