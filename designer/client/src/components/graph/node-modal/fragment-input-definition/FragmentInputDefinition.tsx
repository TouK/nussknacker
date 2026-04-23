/* eslint-disable i18next/no-literal-string */
import { find, head } from "lodash";
import React, { useCallback, useMemo } from "react";

import ProcessUtils from "../../../../common/ProcessUtils";
import { getProcessDefinitionData } from "../../../../reducers/selectors/getProcessDefinitionData";
import { useAppSelector } from "../../../../store/storeHelpers";
import type { MapVariableProps } from "../MapVariable";
import { NodeCommonDetailsDefinition } from "../NodeCommonDetailsDefinition";
import { FieldsSelect } from "./FieldsSelect";
import type { FragmentInputParameter } from "./item/types";
import { getDefaultFields } from "./item/utils";

interface Props extends Omit<MapVariableProps<FragmentInputParameter>, "readOnly"> {
    isEditMode?: boolean;
}

export function useFragmentInputDefinitionTypeOptions() {
    const definitionData = useAppSelector(getProcessDefinitionData);

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

export default function FragmentInputDefinition(props: Props): React.JSX.Element {
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
                isValidating={passProps.isValidating}
            />
        </NodeCommonDetailsDefinition>
    );
}
