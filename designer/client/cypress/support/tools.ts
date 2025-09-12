declare global {
    // eslint-disable-next-line @typescript-eslint/no-namespace
    namespace Cypress {
        interface Chainable {
            dndTo: (target: string, options?: { x?: number; y?: number }) => Cypress.Chainable<JQuery<HTMLElement>>;
            matchQuery: typeof matchQuery;
        }
    }
}

function dndTo(subject, target: string, options?: { x?: number; y?: number }) {
    const { x: x1 = 0, y: y1 = 0 } = options || {};
    cy.wrap(subject).trigger("mousedown", { button: 0 }).trigger("mousemove", { button: 0, x: 10, y: 10 });
    cy.get(target)
        .as("target")
        .then(($target) => {
            const width = $target.width();
            const x = width + x1 - 10;
            const y = y1 + 10;
            cy.wrap($target).trigger("mousemove", x, y, { button: 0, x, y, force: true, moveThreshold: 5 });
            cy.wait(50);
            cy.wrap($target).trigger("mouseup", { force: true, bubbles: true, x, y });
        });
    cy.wait(250);
}

function matchQuery(query: string): void {
    cy.location("search").should("equal", query);
}

Cypress.Commands.add("dndTo", { prevSubject: true }, dndTo);
Cypress.Commands.add("matchQuery", matchQuery);

export default {};
