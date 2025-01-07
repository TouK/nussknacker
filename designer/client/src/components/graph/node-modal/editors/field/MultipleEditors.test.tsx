import { render, screen } from "@testing-library/react";
import { MultipleEditors } from "./MultipleEditors";
import { EditorMode, ExpressionLang } from "../expression/types";
import * as React from "react";
import { ParamType } from "../types";
import { EditorType } from "../expression/Editor";
import configureMockStore from "redux-mock-store/lib";
import { NuThemeProvider } from "../../../../../containers/theme/nuThemeProvider";
import { Provider } from "react-redux";

const mockStore = configureMockStore();

const store = mockStore({
    settings: {
        processDefinitionData: {
            componentGroups: [],
            processDefinition: {},
            componentsConfig: {},
            additionalPropertiesConfig: {},
            edgesForNodes: [],
            defaultAsyncInterpretation: true,
        },
    },
    graphReducer: { history: { present: { scenario: { scenarioGraph: {} } } } },
});

const ComponentWrapper = ({ children }) => (
    <NuThemeProvider>
        <Provider store={store}>{children}</Provider>
    </NuThemeProvider>
);

const fieldErrors = [];
const variableTypes = {
    test: {
        display: "String",
        type: "TypedClass",
        refClazzName: "java.lang.String",
        params: [],
    },
};
const setParams = (editors: ParamType["editors"]) => ({
    name: "stateTimeout",
    typ: {
        display: "Duration",
        type: "TypedClass",
        refClazzName: "java.time.Duration",
        params: [],
    },
    defaultValue: {
        language: EditorMode.SpEL,
        expression: "",
    },
    additionalVariables: {},
    variablesToHide: [],
    branchParam: false,
    hintText: null,
    label: "stateTimeout",
    requiredParam: true,
    editors,
});

describe("MultipleEditors", () => {
    it.todo("Should set selected editor based on default value");
    it("Should set selected editor when expression exists and each editors have different languages", () => {
        const onValueChangeMock = jest.fn();

        const editors: ParamType["editors"] = [
            { type: EditorType.DICT_PARAMETER_EDITOR, language: ExpressionLang.DictKeyWithLabel },
            { type: EditorType.SPEL_TEMPLATE_PARAMETER_EDITOR, language: ExpressionLang.SpELTemplate },
        ];

        const expressionObj = { language: ExpressionLang.SpELTemplate, expression: "works" };

        render(
            <ComponentWrapper>
                <MultipleEditors
                    onValueChange={onValueChangeMock}
                    expressionObj={expressionObj}
                    fieldErrors={fieldErrors}
                    variableTypes={variableTypes}
                    param={setParams(editors)}
                />
            </ComponentWrapper>,
        );

        expect(screen.getByText("SpEL Template")).toBeInTheDocument();
    });
    it("Should set selected editor as first available when expression exists and each editors have the same language", () => {
        const onValueChangeMock = jest.fn();

        const editors: ParamType["editors"] = [
            { type: EditorType.RAW_PARAMETER_EDITOR, language: ExpressionLang.SpEL },
            { type: EditorType.DURATION_EDITOR, language: ExpressionLang.SpEL },
        ];

        const expressionObj = { language: ExpressionLang.SpEL, expression: "works" };

        render(
            <ComponentWrapper>
                <MultipleEditors
                    onValueChange={onValueChangeMock}
                    expressionObj={expressionObj}
                    fieldErrors={fieldErrors}
                    variableTypes={variableTypes}
                    param={setParams(editors)}
                />
            </ComponentWrapper>,
        );

        expect(screen.getByText("SpEL")).toBeInTheDocument();
    });
});
