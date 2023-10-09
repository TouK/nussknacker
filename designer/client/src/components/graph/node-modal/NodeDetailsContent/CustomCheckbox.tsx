export const customCheckbox = (checkboxWidth) => `
    input[type='checkbox'] {
        text-rendering: optimizeSpeed;
        width: ${checkboxWidth};
        height: ${checkboxWidth};
        margin: 0;
        margin-right: 1px;
        display: block;
        position: relative;
        cursor: pointer;
        -moz-appearance: none;
        border: none;
    }

    input[type='checkbox']:after {
        content: "";
        vertical-align: middle;
        text-align: center;
        line-height: ${checkboxWidth};
        position: absolute;
        cursor: pointer;
        height: ${checkboxWidth};
        width: ${checkboxWidth};
        font-size: calc(${checkboxWidth} - 6);
        box-shadow: inset 0px 1px 1px #000, 0px 1px 0px #444;
        background: #333;
    }
    input[type='checkbox']:checked::after {
        background: #333;
        content: '\\2714';
        color: #fff;
    }

    input[type='checkbox']:disabled {
        opacity: 0.3;
        cursor: auto;
    }
 `;
