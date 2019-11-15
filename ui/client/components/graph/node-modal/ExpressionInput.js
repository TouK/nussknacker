import ExpressionSuggest from "../ExpressionSuggest";
import PropTypes from "prop-types";
import React from "react";

const ExpressionInput = (props) => {
    const {label, name, path, value, onChange, isMarked, readOnly, showValidation, rows, cols, validators} = props;

    return (
        <div className="node-row">
            <div className="node-label" title={label}>{label}:</div>
            <div className={"node-value"}>
                <ExpressionSuggest
                    fieldName={name}
                    inputProps={{
                        className: "node-input",
                        value: value.expression,
                        language: value.language,
                        onValueChange: ((value) => onChange(path, value)),
                        readOnly,
                        rows,
                        cols}}
                    validators={validators}
                    isMarked={isMarked}
                    showValidation={showValidation}
                />
            </div>
        </div>
    );
};

ExpressionInput.propTypes = {
    isMarked: PropTypes.bool,
    readOnly: PropTypes.bool,
    rows: PropTypes.number,
    cols: PropTypes.number,
    name: PropTypes.string.isRequired,
    label: PropTypes.string.isRequired,
    value: PropTypes.object.isRequired,
    path: PropTypes.string.isRequired,
    onChange: PropTypes.func.isRequired,
    showValidation: PropTypes.bool.isRequired
};

ExpressionInput.defaultProps = {
    rows: 1,
    cols: 50,
    isMarked: false,
    readOnly: false,
};

export default ExpressionInput;
