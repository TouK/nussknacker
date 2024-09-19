declare global {
    // eslint-disable-next-line @typescript-eslint/no-namespace
    namespace Cypress {
        interface Chainable {
            mockWindowDate: typeof mockWindowDate;
        }
    }
}

class FakeDate extends Date {
    constructor(date: Date) {
        super(date);
        return new Date(2024, 0, 4, 12, 10, 51);
    }
}

const mockWindowDate = () => {
    // let originalDate: DateConstructor;

    cy.on("window:before:load", (win) => {
        Object.assign(win, { Date: FakeDate });
    });
};

Cypress.Commands.add("mockWindowDate", mockWindowDate);

export default {};
