import Chainable = Cypress.Chainable;
import { padStart } from "lodash";

declare global {
    // eslint-disable-next-line @typescript-eslint/no-namespace
    namespace Cypress {
        interface Chainable {
            createTestProcess: typeof createTestProcess;
            deleteTestProcess: typeof deleteTestProcess;
            getTestProcesses: typeof getTestProcesses;
            deleteAllTestProcesses: typeof deleteAllTestProcesses;
            createTestProcessName: typeof createTestProcessName;
            createTestFragment: typeof createTestFragment;
            importTestProcess: typeof importTestProcess;
            visitNewProcess: typeof visitNewProcess;
            visitNewFragment: typeof visitNewFragment;
            postFormData: typeof postFormData;
            visitProcess: typeof visitProcess;
            getNode: typeof getNode;
            dragNode: typeof dragNode;
            layoutScenario: typeof layoutScenario;
            deployScenario: typeof deployScenario;
            cancelScenario: typeof cancelScenario;
            createKafkaTopic: typeof createKafkaTopic;
            removeKafkaTopic: typeof removeKafkaTopic;
            createSchema: typeof createSchema;
            removeSchema: typeof removeSchema;
        }
    }
}

const processIndexes = {};
function createTestProcessName(name?: string) {
    processIndexes[name] = ++processIndexes[name] || 1;
    const index = padStart(processIndexes[name].toString(), 3, "0");
    return cy.wrap(`${Cypress.env("processNamePrefix")}-${index}-${name}-test-process`);
}
function createProcess(
    name?: string,
    fixture?: string,
    category = "Category1",
    isFragment = false,
    processingMode?: string,
    engineSetupName?: string,
) {
    return cy.createTestProcessName(name).then((processName) => {
        const url = `/api/processes`;

        cy.request({
            method: "POST",
            url,
            body: {
                name: processName,
                category,
                isFragment,
                processingMode: processingMode,
                engineSetupName,
            },
        })
            .its("status")
            .should("equal", 201);
        return fixture ? cy.importTestProcess(processName, fixture) : cy.wrap(processName);
    });
}

const createTestProcess = (name?: string, fixture?: string, category = "Category1", processingMode?: string, engineSetupName?: string) =>
    createProcess(name, fixture, category, false, processingMode, engineSetupName);

const createTestFragment = (name?: string, fixture?: string, category = "Category1", processingMode?: string, engineSetupName?: string) =>
    createProcess(name, fixture, category, true, processingMode, engineSetupName);

function visitProcess(processName: string) {
    cy.visit(`/visualization/${processName}`);
    cy.wait("@fetch").its("response.statusCode").should("eq", 200);
    return cy.wrap(processName);
}

function visitNewProcess(name?: string, fixture?: string, category?: string) {
    cy.intercept("GET", "/api/processes/*").as("fetch");
    return cy.createTestProcess(name, fixture, category).then((processName) => {
        return cy.visitProcess(processName);
    });
}

function visitNewFragment(name?: string, fixture?: string, category?: string) {
    cy.intercept("GET", "/api/processes/*").as("fetch");
    return cy.createTestFragment(name, fixture, category).then((processName) => {
        return cy.visitProcess(processName);
    });
}

function deleteTestProcess(processName: string, force?: boolean) {
    const url = `/api/processes/${processName}`;

    function archiveProcess() {
        return cy.request({
            method: "POST",
            url: `/api/archive/${processName}`,
            failOnStatusCode: false,
        });
    }

    function archiveThenDeleteProcess() {
        return archiveProcess().then(() =>
            cy.request({
                method: "DELETE",
                url,
                failOnStatusCode: false,
            }),
        );
    }

    function cancelProcess() {
        return cy.request({
            method: "POST",
            url: `/api/processManagement/cancel/${processName}`,
            failOnStatusCode: false,
            body: "issues/123",
        });
    }

    return archiveThenDeleteProcess()
        .then((response) => (force && response.status === 409 ? cancelProcess().then(archiveThenDeleteProcess) : cy.wrap(response)))
        .its("status")
        .should("be.oneOf", [200, 404]);
}

function postFormData(
    url: string,
    auth: {
        username: string;
        password: string;
    },
    body?: FormData,
): Chainable {
    const { password, username } = auth;
    const authorization = `Basic ${btoa(`${username}:${password}`)}`;
    return cy.wrap(
        new Cypress.Promise((resolve, reject) => {
            fetch(url, {
                method: "POST",
                headers: { authorization },
                body,
            })
                .then((res) => res.json())
                .then(resolve, reject);
        }),
    );
}

function importTestProcess(name: string, fixture = "testProcess") {
    return cy
        .fixture(fixture, null)
        .then((json) => {
            const formData = new FormData();
            formData.set("process", Cypress.Blob.arrayBufferToBlob(json, "application/json"), "data.json");
            const auth = {
                username: Cypress.env("testUserUsername"),
                password: Cypress.env("testUserPassword"),
            };
            return cy.postFormData(`/api/processes/import/${name}`, auth, formData);
        })
        .then((response) => {
            cy.request("PUT", `/api/processes/${name}`, {
                comment: "import test data",
                scenarioGraph: response.scenarioGraph,
            });
            return cy.wrap(name);
        });
}

function getTestProcesses(filter?: string) {
    const url = `/api/processes`;
    return cy
        .request({ url })
        .then(({ body }) => body.filter(({ name }) => name.includes(filter || Cypress.env("processName"))).map(({ name }) => name));
}

function deleteAllTestProcesses({ filter, force }: { filter?: string; force?: boolean }) {
    return cy.getTestProcesses(filter).each((name: string) => {
        cy.deleteTestProcess(name, force);
    });
}

function createKafkaTopic(topic: string) {
    const redpandaContainerName = Cypress.env("REDPANDA_CONTAINER") || "cypress_e2e_redpanda";
    return cy.exec(`docker exec ${redpandaContainerName} rpk topic create ${topic}`);
}

function removeKafkaTopic(topic: string) {
    const redpandaContainerName = Cypress.env("REDPANDA_CONTAINER") || "cypress_e2e_redpanda";
    return cy.exec(`docker exec ${redpandaContainerName} rpk topic delete ${topic}`);
}

function createSchema(subject: string, schemaFileName: string) {
    const schemaRegistryUrl = Cypress.env("SCHEMA_REGISTRY_ADDRESS") || "http://localhost:3082";
    return cy.fixture(schemaFileName).then((schemaContent) => {
        cy.request({
            method: "POST",
            url: `${schemaRegistryUrl}/subjects/${subject}/versions`,
            body: { schema: JSON.stringify(schemaContent) },
            headers: {
                "Content-Type": "application/vnd.schemaregistry.v1+json",
            },
        }).then((response) => {
            expect(response.status).to.eq(200); // Check for a successful response
            cy.log("Schema ID:", response.body.id);
        });
    });
}

function removeSchema(subject: string) {
    const schemaRegistryUrl = Cypress.env("SCHEMA_REGISTRY_ADDRESS") || "http://localhost:3082";
    cy.request({
        method: "DELETE",
        url: `${schemaRegistryUrl}/subjects/${subject}?permanent=true`,
        headers: {
            "Content-Type": "application/vnd.schemaregistry.v1+json",
        },
        failOnStatusCode: false,
    }).then((response) => {
        expect(response.status).to.be.oneOf([200, 204, 404]); // Successful deletion should return 200 or 204
        cy.log("Force deleted all versions of schema subject:", subject);
    });
}

function getNode(name: string, end?: boolean) {
    return cy.get(`[model-id${end ? "$=" : "="}"${name}"]`, { timeout: 30000 });
}

function dragNode(
    name: string,
    {
        x,
        y,
    }: {
        x: number;
        y: number;
    },
) {
    cy.getNode(name).should("be.visible").trigger("mousedown", "center").trigger("mousemove", x, y, {
        moveThreshold: 5,
        force: true,
        clientX: x,
        clientY: y,
    });
    cy.get("body").trigger("mouseup");
    return cy.getNode(name);
}

function layoutScenario(waitTime = 400) {
    cy.contains(/^layout$/).click();
    cy.wait(waitTime); //wait for graph view (zoom, pan) to settle
}

function deployScenario(comment = "issues/123", withScreenshot?: boolean) {
    cy.contains(/^deploy$/i).click();
    cy.intercept("POST", "/api/processManagement/deploy/*").as("deploy");
    if (withScreenshot) {
        cy.get("[data-testid=window]").matchImage();
    }
    cy.get("[data-testid=window] textarea").click().type(comment);
    cy.contains(/^ok$/i).should("be.enabled").click();
    cy.wait(["@deploy", "@fetch"], {
        timeout: 20000,
        log: true,
    }).each((res) => {
        cy.wrap(res).its("response.statusCode").should("eq", 200);
    });
}

function cancelScenario(comment = "issues/123") {
    cy.contains(/^cancel$/i).click();
    cy.get("[data-testid=window] textarea").click().type(comment);
    cy.contains(/^ok$/i).should("be.enabled").click();
}

Cypress.Commands.add("createTestProcess", createTestProcess);
Cypress.Commands.add("deleteTestProcess", deleteTestProcess);
Cypress.Commands.add("getTestProcesses", getTestProcesses);
Cypress.Commands.add("deleteAllTestProcesses", deleteAllTestProcesses);
Cypress.Commands.add("createTestProcessName", createTestProcessName);
Cypress.Commands.add("createTestFragment", createTestFragment);
Cypress.Commands.add("importTestProcess", importTestProcess);
Cypress.Commands.add("visitNewProcess", visitNewProcess);
Cypress.Commands.add("visitNewFragment", visitNewFragment);
Cypress.Commands.add("postFormData", postFormData);
Cypress.Commands.add("visitProcess", visitProcess);
Cypress.Commands.add("getNode", getNode);
Cypress.Commands.add("dragNode", dragNode);
Cypress.Commands.add("layoutScenario", layoutScenario);
Cypress.Commands.add("deployScenario", deployScenario);
Cypress.Commands.add("cancelScenario", cancelScenario);
Cypress.Commands.add("createKafkaTopic", createKafkaTopic);
Cypress.Commands.add("removeKafkaTopic", removeKafkaTopic);
Cypress.Commands.add("createSchema", createSchema);
Cypress.Commands.add("removeSchema", removeSchema);
export default {};
