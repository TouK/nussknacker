import PropTypes from "prop-types";
import React from "react";

const Input = (props) => {
    const {label, path, value, onChange, isMarked, readOnly, shouldRenderValidationLabel: isValid = () => false} = props;

    return (
        <div className="node-row">
            <div className="node-label" title={label}>{label}:</div>
            <div className={"node-value" + (isMarked ? " marked" : "")}>
                <input
                    type="text"
                    className="node-input"
                    value={value}
                    onChange={(event) => onChange(path, event.target.value)}
                    readOnly={readOnly}
                />
                {
                    isValid(value) ?
                        <label className='node-details-validation-label'>{label + " can not be empty"}</label> : null
                }
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