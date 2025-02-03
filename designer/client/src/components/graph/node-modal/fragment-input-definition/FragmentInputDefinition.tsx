/* eslint-disable i18next/no-literal-string */
import React, { useCallback, useMemo } from "react";
import { useSelector } from "react-redux";
import ProcessUtils from "../../../../common/ProcessUtils";
import { getProcessDefinitionData } from "../../../../reducers/selectors/settings";
import { MapVariableProps } from "../MapVariable";
import { NodeCommonDetailsDefinition } from "../NodeCommonDetailsDefinition";
import { FieldsSelect } from "./FieldsSelect";
import { find, head } from "lodash";
import { getDefaultFields } from "./item/utils";
import { FragmentInputParameter } from "./item";

interface Props extends Omit<MapVariableProps<FragmentInputParameter>, "readOnly"> {
    isEditMode?: boolean;
}

export function useFragmentInputDefinitionTypeOptions() {
    const definitionData = useSelector(getProcessDefinitionData);

    const typeOptions = useMemo(
        () =>
            definitionData?.classes?.map((type) => ({
                value: type.display as string,
                label: ProcessUtils.humanReadableType(type),
            })),
        [definitionData?.classes],
    );

    const defaultTypeOption = useMemo(() => find(typeOptions, { label: "String" }) || head(typeOptions), [typeOptions]);
    return {
        typeOptions,
        defaultTypeOption,
    };
}

export default function FragmentInputDefinition(props: Props): JSX.Element {
    const { removeElement, addElement, variableTypes, ...passProps } = props;
    const { node, setProperty, isEditMode, showValidation } = passProps;

    const readOnly = !isEditMode;
    const { typeOptions, defaultTypeOption } = useFragmentInputDefinitionTypeOptions();

    const addField = useCallback(() => {
        addElement("parameters", getDefaultFields(defaultTypeOption.value));
    }, [addElement, defaultTypeOption.value]);

    const fields = useMemo(() => node.parameters || [], [node.parameters]);

    return (
        <NodeCommonDetailsDefinition {...passProps}>
            <FieldsSelect
                label="Parameters"
                onChange={setProperty}
                addField={addField}
                removeField={removeElement}
                namespace={"parameters"}
                fields={fields}
                options={typeOptions}
                showValidation={showValidation}
                readOnly={readOnly}
                variableTypes={variableTypes}
                errors={passProps.errors}
            />
        </NodeCommonDetailsDefinition>
    );
}
