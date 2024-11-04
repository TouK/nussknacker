import { useSelector } from "react-redux";
import { getProcessName, getScenarioPropertiesConfig } from "./NodeDetailsContent/selectors";
import React, { useEffect, useMemo, useState } from "react";
import { debounce, isEmpty, sortBy } from "lodash";
import { set } from "lodash/fp";
import ScenarioProperty from "./ScenarioProperty";
import { DescriptionField } from "./DescriptionField";
import { FieldLabel } from "./FieldLabel";
import Field, { FieldType } from "./editors/field/Field";
import { NodeField } from "./NodeField";
import { getPropertiesErrors } from "./node/selectors";
import { NodeTable } from "./NodeDetailsContent/NodeTable";
import { css } from "@emotion/css";
import { ContentSize } from "./node/ContentSize";
import NodeUtils from "../NodeUtils";
import { getProcessUnsavedNewName, getScenario } from "../../../reducers/selectors/graph";
import HttpService from "../../../http/HttpService";
import { NodeValidationError } from "../../../types";
import { nodeInput, nodeInputWithError } from "./NodeDetailsContent/NodeTableStyled";

interface Props {
    isEditMode: boolean;
    showValidation?: boolean;
}

export function PropertiesNew({ isEditMode }: Props): JSX.Element {
    const globalPropertiesErrors = useSelector(getPropertiesErrors);
    const [errors, setErrors] = useState<NodeValidationError[]>(isEditMode ? globalPropertiesErrors : []);

    const scenarioProperties = useSelector(getScenarioPropertiesConfig);
    const scenarioPropertiesConfig = useMemo(() => scenarioProperties?.propertiesConfig ?? {}, [scenarioProperties?.propertiesConfig]);

    //fixme move this configuration to some better place?
    //we sort by name, to have predictable order of properties (should be replaced by defining order in configuration)
    const scenarioPropertiesSorted = useMemo(
        () => sortBy(Object.entries(scenarioPropertiesConfig), ([name]) => name),
        [scenarioPropertiesConfig],
    );

    const scenario = useSelector(getScenario);
    const scenarioName = useSelector(getProcessName);
    const name = useSelector(getProcessUnsavedNewName);

    const [node, setNode] = useState(() => NodeUtils.getProcessPropertiesNode(scenario, name));

    const setProperty = (label: string | number, value: string) => {
        setNode((prevState) => set<typeof node>(label, value, prevState) as unknown as typeof node);
    };

    const showSwitch = false;

    const debouncedValidateProperties = useMemo(() => {
        return debounce((scenarioName, additionalFields, id) => {
            HttpService.validateProperties(scenarioName, { additionalFields: additionalFields, name: id }).then((data) => {
                if (data) {
                    setErrors(data.validationErrors);
                }
            });
        }, 500);
    }, []);

    useEffect(() => {
        if (!isEditMode) {
            return;
        }

        debouncedValidateProperties(scenarioName, node.additionalFields, node.name);
    }, [debouncedValidateProperties, isEditMode, node.additionalFields, node.name, scenarioName]);

    return (
        <div className={css({ height: "100%", display: "grid", gridTemplateRows: "auto 1fr" })}>
            <ContentSize>
                <NodeTable>
                    <Field
                        type={FieldType.input}
                        isMarked={false}
                        showValidation
                        onChange={(newValue) => setProperty("name", newValue.toString())}
                        readOnly={!isEditMode}
                        className={isEmpty(errors) ? nodeInput : `${nodeInput} ${nodeInputWithError}`}
                        fieldErrors={errors}
                        value={node.name}
                        autoFocus
                    >
                        <FieldLabel title={"Name"} label={"Name"} />
                    </Field>
                    {scenarioPropertiesSorted.map(([propName, propConfig]) => (
                        <ScenarioProperty
                            key={propName}
                            showSwitch={showSwitch}
                            showValidation
                            propertyName={propName}
                            propertyConfig={propConfig}
                            errors={errors}
                            onChange={setProperty}
                            renderFieldLabel={() => (
                                <FieldLabel title={propConfig.label} label={propConfig.label} hintText={propConfig.hintText} />
                            )}
                            editedNode={node}
                            readOnly={!isEditMode}
                        />
                    ))}
                    <DescriptionField
                        isEditMode={isEditMode}
                        showValidation
                        node={node}
                        renderFieldLabel={(paramName) => <FieldLabel title={paramName} label={paramName} />}
                        setProperty={setProperty}
                        errors={errors}
                    />
                    <NodeField
                        isEditMode={isEditMode}
                        showValidation
                        node={node}
                        renderFieldLabel={(paramName) => <FieldLabel title={paramName} label={paramName} />}
                        setProperty={setProperty}
                        errors={errors}
                        fieldType={FieldType.checkbox}
                        fieldName={"additionalFields.showDescription"}
                        description={"Show description each time scenario is opened"}
                    />
                </NodeTable>
            </ContentSize>
        </div>
    );
}
