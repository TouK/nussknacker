import { FragmentInputParameter } from ".";
import { ReturnedType } from "../../../../../types";
import { v4 as uuid4 } from "uuid";
import { ExpressionLang } from "../../editors/expression/types";

export const getDefaultFields = (refClazzName: string): FragmentInputParameter => {
    return {
        uuid: uuid4(),
        name: "",
        required: false,
        hintText: "",
        initialValue: null,
        // fixedValuesType: FixedValuesType.UserDefinedList,
        valueEditor: null,
        // fixedValuesListPresetId: "",
        // validationExpression: "test",
        // presetSelection: "",
        // validationExpression: "",
        // validationErrorMessage: "",
        // validation: true,
        validationExpression: {
            validation: true,
            expression: {
                expression: "",
                language: ExpressionLang.SpEL,
            },
            failedMessage: "There was a problem with expression validation",
        },
        typ: { refClazzName } as ReturnedType,
    };
};
