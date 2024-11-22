const { repeat, padEnd, padStart, throttle } = require("lodash");
const chalk = require("chalk");

let interval;
let startTime = Date.now();

const options = {
    barLength: 40,
    longTime: 40,
    almostDone: 0.8,
};

function getElapsed() {
    const { longTime } = options;
    const elapsed = Math.round((Date.now() - startTime) / 1000);
    const elapsedColor = elapsed < longTime / 2 ? chalk.green : elapsed < longTime ? chalk.yellow : chalk.red;
    return elapsedColor(padStart(`${elapsed}s`, 4));
}

function getBar(percentage) {
    const { barLength, almostDone } = options;
    const bar = padEnd(repeat("◼", Math.ceil(percentage * barLength)), barLength, "_");
    const barColor = percentage > almostDone ? chalk.green : percentage > (almostDone * 2) / 3 ? chalk.yellow : chalk.red;
    return barColor(bar);
}

const getLine = (percentage, massage = "", ...args) =>
    chalk.cyan(
        [`${padStart(Math.ceil(percentage * 100), 3)}%`, getBar(percentage), getElapsed(), massage, chalk.dim(args.filter(Boolean))].join(
            " ",
        ),
    );

const isCi = process.env.CI === "true" || !process.stdout.isTTY;

const renderCi = throttle((...args) => console.log(getLine(...args)), 5000, { leading: true, trailing: true });

const renderTTY = (...args) => {
    process.stdout.cursorTo(0);
    process.stdout.clearLine();
    process.stdout.write(getLine(...args));
    process.stdout.cursorTo(0);
};

const render = isCi ? renderCi : renderTTY;

module.exports = throttle((percentage, ...args) => {
    if (percentage <= 0) {
        startTime = Date.now();
    }

    clearInterval(interval);
    if (percentage < 1) {
        interval = setInterval(() => render(percentage, ...args), 500);
        render(percentage, ...args);
    } else {
        if (!isCi) {
            process.stdout.clearLine();
        }
    }
}, 500);
