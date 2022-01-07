export const jsonToBlob = (data: any) => {
  return new Blob([JSON.stringify(data)], {type: "application/json"})
}

declare global {
  // eslint-disable-next-line @typescript-eslint/no-namespace
  namespace Cypress {
    interface Chainable<Subject = any> {
      dndTo: typeof dndTo,
      matchQuery: typeof matchQuery,
    }
  }
}

function dndTo(subject, target: string, options?: { x?: number, y?: number }): Cypress.Chainable<JQuery<HTMLElement>> {
  const {x: x1 = 0, y: y1 = 0} = options || {}

  cy.wrap(subject)
    .trigger("mousedown", {button: 0})
    .trigger("mousemove", {button: 0, x: 10, y: 10})

  return cy.get(target).as("target").then($target => {
    const width = $target.width()
    const x = width + x1 - 10
    const y = y1 + 10
    cy.wrap($target).trigger("mousemove", {button: 0, x, y, force: true})
    return cy.get("@target").trigger("mouseup", {button: 0})
  })
}

function matchQuery(query: string): void {
  cy.window().should(({location}) => {
    expect(location.search).to.equal(query)
  })
}

Cypress.Commands.add("dndTo", {prevSubject: true}, dndTo)
Cypress.Commands.add("matchQuery", matchQuery)
