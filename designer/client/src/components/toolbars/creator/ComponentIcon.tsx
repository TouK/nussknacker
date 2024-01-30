import { isString, memoize } from "lodash";
import React from "react";
import { useSelector } from "react-redux";
import ProcessUtils from "../../../common/ProcessUtils";
import { getProcessDefinitionData } from "../../../reducers/selectors/settings";
import { NodeType, ProcessDefinitionData } from "../../../types";
import PropertiesSvg from "../../../assets/img/properties.svg";
import ReactDOM from "react-dom";
import { InlineSvg } from "../../SvgDiv";

let preloadedIndex = 0;
const preloadBeImage = memoize((src: string): string | null => {
    if (!src) {
        return null;
    }

    const id = `svg${++preloadedIndex}`;
    const div = document.createElement("div");
    ReactDOM.render(<InlineSvg src={src} id={id} style={{ display: "none" }} />, div);
    document.body.appendChild(div);
    return `#${id}`;
});

function getIconFromDef(nodeOrPath: NodeType, processDefinitionData: ProcessDefinitionData): string | null {
    // missing type means that node is the fake properties component
    // TODO we should split properties node logic and normal components logic
    if (nodeOrPath.type) {
        return (
            ProcessUtils.extractComponentDefinition(nodeOrPath, processDefinitionData.components)?.icon ||
            `/assets/components/${nodeOrPath.type}.svg`
        );
    } else {
        return null;
    }
}

export const getComponentIconSrc: {
    (path: string): string;
    (node: NodeType, processDefinitionData: ProcessDefinitionData): string | null;
} = (nodeOrPath, processDefinitionData?) => {
    if (nodeOrPath) {
        const icon = isString(nodeOrPath) ? nodeOrPath : getIconFromDef(nodeOrPath, processDefinitionData);
        return preloadBeImage(icon);
    }
    return null;
};

interface Created extends ComponentIconProps {
    processDefinition: ProcessDefinitionData;
}

class Icon extends React.Component<Created> {
    private icon: string;

    constructor(props) {
        super(props);
        this.icon = getComponentIconSrc(props.node, props.processDefinition);
    }

    componentDidUpdate() {
        this.icon = getComponentIconSrc(this.props.node, this.props.processDefinition);
    }

    render(): JSX.Element {
        const {
            icon,
            props: { className },
        } = this;

        if (!icon) {
            return <PropertiesSvg className={className} />;
        }

        return (
            <svg className={className}>
                <use href={icon} />
            </svg>
        );
    }
}

export interface ComponentIconProps {
    node: NodeType;
    className?: string;
}

export const ComponentIcon = (props: ComponentIconProps) => {
    const processDefinitionData = useSelector(getProcessDefinitionData);
    return <Icon {...props} processDefinition={processDefinitionData} />;
};
