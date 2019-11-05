import PropTypes from "prop-types";
import React from "react";
import ValidationLabels from "../../modals/ValidationLabels";
import {notEmptyValidator} from "../../../common/Validators";

const Input = (props) => {
    const {label, path, value, onChange, isMarked, readOnly, validators} = props;

    return (
        <div className="node-row">
            <div className="node-label" title={label}>{label}:</div>
            <div className={"node-value" + (isMarked ? " marked" : "")}>
                <input
                    key={label}
                    type="text"
                    className="node-input"
                    value={value}
                    onChange={(event) => onChange(path, event.target.value)}
                    readOnly={readOnly}
                />
                <ValidationLabels validators={[notEmptyValidator]} values={[value]}/>
            </div>
        </div>
    );
};

Input.propTypes = {
    isMarked: PropTypes.bool,
    readOnly: PropTypes.bool,
    label: PropTypes.string.isRequired,
    value: PropTypes.string.isRequired,
    path: PropTypes.string.isRequired,
    onChange: PropTypes.func.isRequired
};

Input.defaultProps = {
    isMarked: false,
    readOnly: false,
};

export default Input;