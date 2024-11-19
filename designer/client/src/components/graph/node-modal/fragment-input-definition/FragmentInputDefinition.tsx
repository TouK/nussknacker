/* eslint-disable i18next/no-literal-string */
import React, { useCallback, useMemo } from "react";
import { useSelector } from "react-redux";
import ProcessUtils from "../../../../common/ProcessUtils";
import { getProcessDefinitionData } from "../../../../reducers/selectors/settings";
import { MapVariableProps } from "../MapVariable";
import { NodeCommonDetailsDefinition } from "../NodeCommonDetailsDefinition";
import { FieldsSelect } from "./FieldsSelect";
import { find, head, orderBy } from "lodash";
import { getDefaultFields } from "./item/utils";
import { FragmentInputParameter } from "./item";

interface Props extends Omit<MapVariableProps<FragmentInputParameter>, "readOnly"> {
    isEditMode?: boolean;
}

export function useTypeOptions<Value = string>(useDisplay = false) {
    const definitionData = useSelector(getProcessDefinitionData);

    const typeOptions = useMemo(
        () =>
            definitionData?.classes?.map((type) => ({
                value: useDisplay ? type.display : (type.refClazzName as Value),
                label: ProcessUtils.humanReadableType(type),
            })),
        [definitionData?.classes],
    );

    const orderedTypeOptions = useMemo(() => orderBy(typeOptions, (item) => [item.label, item.value], ["asc"]), [typeOptions]);

    const defaultTypeOption = useMemo(() => find(typeOptions, { label: "String" }) || head(typeOptions), [typeOptions]);
    return {
        orderedTypeOptions,
        defaultTypeOption,
    };
}

export default function FragmentInputDefinition(props: Props): JSX.Element {
    const { removeElement, addElement, variableTypes, ...passProps } = props;
    const { node, setProperty, isEditMode, showValidation } = passProps;

    const readOnly = !isEditMode;
    const { orderedTypeOptions, defaultTypeOption } = useTypeOptions(true);

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
                options={orderedTypeOptions}
                showValidation={showValidation}
                readOnly={readOnly}
                variableTypes={variableTypes}
                errors={passProps.errors}
            />
        </NodeCommonDetailsDefinition>
    );
}
