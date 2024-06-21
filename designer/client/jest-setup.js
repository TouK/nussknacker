import "@testing-library/jest-dom/extend-expect";
import "@testing-library/jest-dom";
import { jest } from "@jest/globals";

Element.prototype.scrollIntoView = jest.fn();

const { TextEncoder, TextDecoder } = require("util");
global.TextEncoder = TextEncoder;
global.TextDecoder = TextDecoder;
