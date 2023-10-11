/* eslint-disable i18next/no-literal-string */
import React, { useCallback, useMemo } from "react";
import { useSelector } from "react-redux";
import ProcessUtils from "../../../../common/ProcessUtils";
import { getProcessDefinitionData } from "../../../../reducers/selectors/settings";
import { Parameter } from "../../../../types";
import { MapVariableProps } from "../MapVariable";
import { NodeCommonDetailsDefinition } from "../NodeCommonDetailsDefinition";
import FieldsSelect from "./FieldsSelect";
import { orderBy, find, head } from "lodash";

interface Props extends Omit<MapVariableProps<Parameter>, "readOnly"> {
    isEditMode?: boolean;
}

export default function FragmentInputDefinition(props: Props): JSX.Element {
    const { addElement, removeElement, variableTypes, ...passProps } = props;
    const { node, isEditMode, setProperty, showValidation } = passProps;

    const readOnly = !isEditMode;
    const definitionData = useSelector(getProcessDefinitionData);
    const typeOptions = useMemo(
        () =>
            definitionData?.processDefinition?.typesInformation?.map((type) => ({
                value: type.clazzName.refClazzName,
                label: ProcessUtils.humanReadableType(type.clazzName),
            })),
        [definitionData?.processDefinition?.typesInformation],
    );

    const orderedTypeOptions = useMemo(() => orderBy(typeOptions, (item) => [item.label, item.value], ["asc"]), [typeOptions]);

    const defaultTypeOption = useMemo(() => find(typeOptions, { label: "String" }) || head(typeOptions), [typeOptions]);

    const addField = useCallback(() => {
        addElement("parameters", { name: "", typ: { refClazzName: defaultTypeOption.value } } as Parameter);
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
            />
        </NodeCommonDetailsDefinition>
    );
}
