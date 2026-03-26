import { padStart } from "lodash";

import type { Setting } from "../../src/reducers/userSettings";

import Chainable = Cypress.Chainable;

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
            addLabelsToNewProcess: typeof addLabelsToNewProcess;
            postFormData: typeof postFormData;
            visitProcess: typeof visitProcess;
            getNode: typeof getNode;
            toggleUserFlag: typeof toggleUserFlag;
            openNodeWindow: typeof openNodeWindow;
            applyNodeChanges: typeof applyNodeChanges;
            dragNode: typeof dragNode;
            layoutScenario: typeof layoutScenario;
            deployScenario: typeof deployScenario;
            cancelScenario: typeof cancelScenario;
            createKafkaTopic: typeof createKafkaTopic;
            removeKafkaTopic: typeof removeKafkaTopic;
            createSchema: typeof createSchema;
            removeSchema: typeof removeSchema;
            getTestProcessName: typeof getTestProcessName;
            archiveProcess: typeof archiveProcess;
            unarchiveProcess: typeof unarchiveProcess;
            migrateProcess: typeof migrateProcess;
            verifySaveIndicator: typeof verifySaveIndicator;
            openNodeDetailsTestingTab: typeof openNodeDetailsTestingTab;
        }
    }
}

const processIndexes = {};

function getTestProcessName(name: string, index: string) {
    return cy.wrap(`${Cypress.env("processNamePrefix")}-${index}-${name}-test-process`, { log: false });
}

function createTestProcessName(name?: string) {
    processIndexes[name] = ++processIndexes[name] || 1;
    const index = padStart(processIndexes[name].toString(), 3, "0");
    return getTestProcessName(name, index);
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

        Cypress.log({
            name: "createProcess",
            message: processName,
        });

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
            log: false,
        })
            .its("status", { log: false })
            .should("equal", 201);
        return fixture ? cy.importTestProcess(processName, fixture) : cy.wrap(processName, { log: false });
    });
}

const createTestProcess = (name?: string, fixture?: string, category = "Category1", processingMode?: string, engineSetupName?: string) =>
    createProcess(name, fixture, category, false, processingMode, engineSetupName);

const createTestFragment = (name?: string, fixture?: string, category = "Category1", processingMode?: string, engineSetupName?: string) =>
    createProcess(name, fixture, category, true, processingMode, engineSetupName);

function visitProcess(nameOrAlias: string, query?: Record<string, unknown>) {
    cy.intercept("POST", "/api/processValidation/*", { log: false }).as("fetch");
    return getWrappedName(nameOrAlias).then((name) => {
        cy.visit(`/visualization/${name}`, { qs: query });
        cy.wait("@fetch", { timeout: 20000 }).its("response.statusCode").should("eq", 200);
        // lazy loaded panel moves other toolbars/button just before click
        cy.contains(/we are happy/i).should("be.visible");
        return cy.wrap(name);
    });
}

function visitNewProcess(name?: string, fixture?: string, category?: string, query?: Record<string, unknown>) {
    cy.createTestProcess(name, fixture, category).as("processName", { type: "static" });
    return cy.visitProcess("@processName", query);
}

function visitNewFragment(name?: string, fixture?: string, category?: string, query?: Record<string, unknown>) {
    cy.createTestFragment(name, fixture, category).as("processName", { type: "static" });
    return cy.visitProcess("@processName", query);
}

function addLabelsToNewProcess(name?: string, labels?: string[]) {
    return cy.visitProcess(name).then((processName) => {
        cy.intercept("PUT", "/api/processes/*").as("save");
        cy.intercept("POST", "/api/scenarioLabels/validation").as("labelValidation");
        cy.get("[data-testid=AddLabel]").should("be.visible").click();
        cy.get("[data-testid=LabelInput]").should("be.visible").click().as("labelInput");

        labels.forEach((label) => {
            cy.get("@labelInput").type(label);
            cy.wait("@labelValidation");
            cy.get(".MuiAutocomplete-loading").should("not.exist");
            cy.get('.MuiAutocomplete-popper li[data-option-index="0"]').contains(label).click();
        });

        cy.contains(/^save/i).should("be.enabled").click();
        cy.contains(/^ok$/i).should("be.enabled").click();
        cy.wait("@save").its("response.statusCode").should("eq", 200);
        return cy.wrap(processName);
    });
}

function archiveProcess(processName: string) {
    return cy.request({
        method: "POST",
        url: `/api/archive/${processName}`,
        failOnStatusCode: false,
    });
}

function unarchiveProcess(processName: string) {
    return cy.request({
        method: "POST",
        url: `/api/unarchive/${processName}`,
        failOnStatusCode: false,
    });
}

function migrateProcess(processName: string, processVersionId: number) {
    return cy.request({
        method: "POST",
        url: `/api/remoteEnvironment/${processName}/${processVersionId}/migrate`,
        failOnStatusCode: false,
    });
}

function deleteTestProcess(processName: string, force?: boolean) {
    const url = `/api/processes/${processName}`;

    function archiveThenDeleteProcess() {
        return cy.archiveProcess(processName).then(() =>
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
        .then((response) =>
            force && response.status === 409 ? cancelProcess().then(archiveThenDeleteProcess) : cy.wrap(response, { log: false }),
        )
        .its("status", { log: false })
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
        { log: false },
    );
}

function importTestProcess(name: string, fixture = "testProcess") {
    Cypress.log({
        message: fixture,
    });

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
            cy.request({
                method: "PUT",
                url: `/api/processes/${name}`,
                body: {
                    comment: "import test data",
                    scenarioGraph: response.scenarioGraph,
                    scenarioLabels: [],
                },
                log: false,
            });
            return cy.wrap(name, { log: false });
        });
}

function getTestProcesses(filter?: string) {
    const url = `/api/processes`;
    return cy.request({ url }).then(({ body }) => {
        const filtered = body.filter(({ name }) => {
            if (!name.startsWith(Cypress.env("processNamePrefix"))) return false;
            if (filter?.length) return name.includes(filter);
            return true;
        });
        return filtered.map(({ name }) => name);
    });
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

function getWrappedName(nameOrAlias: string) {
    return nameOrAlias.startsWith("@") ? cy.get<string>(nameOrAlias, { log: false }) : cy.wrap(nameOrAlias, { log: false });
}

function getNode(nameOrAlias: string) {
    return getWrappedName(nameOrAlias).then((name) => {
        let selector = "";
        const match = name.match(/(.*)\*\*(.*)/);
        if (!match) {
            selector = `[data-node-name="${name}"]`;
        } else {
            if (match[1]) {
                selector = `[data-node-name^="${match[1]}"]`;
            }
            if (match[2]) {
                selector += `[data-node-name$="${match[2]}"]`;
            }
        }
        return cy.get(selector, { timeout: 30000, log: false }).should("be.visible");
    });
}

function toggleUserFlag(flag: Setting, value?: boolean | undefined) {
    return cy
        .window()
        .its("$toggleUserFlag")
        .should("exist")
        .then((toggleUserFlag) => {
            toggleUserFlag(flag, value);
        });
}

function openNodeWindow(nameOrAlias: string, options?: { waitForAdditionalInfo?: boolean }) {
    const { waitForAdditionalInfo = true } = options || {};
    if (waitForAdditionalInfo) {
        // in Request (rr) "properties" data is used
        cy.intercept("POST", "/api/*/*/additionalInfo").as("additionalInfo");
    }
    cy.intercept("POST", "/api/nodes/*/validation").as("nodeValidation");

    cy.getNode(nameOrAlias).should("be.visible").dblclick();

    if (waitForAdditionalInfo) {
        cy.wait(["@additionalInfo", "@nodeValidation"], { timeout: 10000 }).each((res) => {
            cy.wrap(res).its("response.statusCode").should("eq", 200);
        });
    } else {
        cy.wait("@nodeValidation", { timeout: 10000 }).its("response.statusCode").should("eq", 200);
    }

    cy.get("[data-testid=window]").should("be.visible").as("nodeWindow");
    cy.get("[data-testid=window]").find('button[name="close"]').should("be.visible");

    return cy.get("[data-testid=window]");
}

function applyNodeChanges() {
    cy.get("[data-testid=window]")
        .contains(/^apply/i)
        .should("be.enabled")
        .wait(550)
        .click({ force: true });
    cy.get("[data-testid=window]").should("not.exist");
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
    cy.getNode(name)
        .should("be.visible")
        .trigger("mousedown", "center")
        // add some user-like noise
        .trigger("mousemove", x - 10, y - 10, {
            moveThreshold: 5,
            force: true,
            clientX: x - 10,
            clientY: y - 10,
        })
        .trigger("mousemove", x + 10, y + 10, {
            moveThreshold: 5,
            force: true,
            clientX: x + 10,
            clientY: y + 10,
        })
        .trigger("mousemove", x, y, {
            moveThreshold: 5,
            force: true,
            clientX: x,
            clientY: y,
        });
    cy.get("body").trigger("mouseup");
    cy.wait(500);
    return cy.getNode(name);
}

function layoutScenario(waitTime = 600) {
    // prevents random clicks on metrics
    // lazy loaded panel moves layout button just before click
    // Cypress keep focus on element and display tooltip forever, let's remove it via blur()
    cy.contains(/^layout$/)
        .click()
        .blur();
    //wait for graph view (zoom, pan) to settle
    cy.wait(waitTime);
}

function deployScenario(comment = "issues/123", withScreenshot?: boolean) {
    cy.contains(/^deploy$/i).click();
    cy.intercept("POST", "/api/processManagement/deploy/*").as("deploy");
    cy.intercept("GET", "/api/processes/*/activity/activities").as("activities");
    if (withScreenshot) {
        cy.get("[data-testid=window]").matchImage();
    }
    cy.get("[data-testid=window] textarea").click().type(comment);
    cy.contains(/^ok$/i).should("be.enabled").click();
    cy.wait(["@deploy", "@activities"], {
        timeout: 20000,
        log: true,
    }).each((res) => {
        cy.wrap(res).its("response.statusCode").should("eq", 200);
    });
}

function cancelScenario(comment = "issues/123") {
    cy.contains("button", /^cancel$/i).click();
    cy.get("[data-testid=window] textarea").click().type(comment);
    cy.contains(/^ok$/i).should("be.enabled").click();
}

function verifySaveIndicator() {
    cy.contains(/^save$/i)
        .find('[data-testid="toolbarButton-label"]')
        .then(($el) => {
            const afterContent = window.getComputedStyle($el[0], ":after").getPropertyValue("content");
            expect(afterContent.includes("*")).to.be.true;
        });
}

function openNodeDetailsTestingTab() {
    cy.get("[role=tab]")
        .contains(/testing/i)
        .should("be.visible")
        .click();
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
Cypress.Commands.add("addLabelsToNewProcess", addLabelsToNewProcess);
Cypress.Commands.add("postFormData", postFormData);
Cypress.Commands.add("visitProcess", visitProcess);
Cypress.Commands.add("getNode", getNode);
Cypress.Commands.add("toggleUserFlag", toggleUserFlag);
Cypress.Commands.add("openNodeWindow", openNodeWindow);
Cypress.Commands.add("applyNodeChanges", applyNodeChanges);
Cypress.Commands.add("dragNode", dragNode);
Cypress.Commands.add("layoutScenario", layoutScenario);
Cypress.Commands.add("deployScenario", deployScenario);
Cypress.Commands.add("cancelScenario", cancelScenario);
Cypress.Commands.add("createKafkaTopic", createKafkaTopic);
Cypress.Commands.add("removeKafkaTopic", removeKafkaTopic);
Cypress.Commands.add("createSchema", createSchema);
Cypress.Commands.add("removeSchema", removeSchema);
Cypress.Commands.add("getTestProcessName", getTestProcessName);
Cypress.Commands.add("archiveProcess", archiveProcess);
Cypress.Commands.add("unarchiveProcess", unarchiveProcess);
Cypress.Commands.add("migrateProcess", migrateProcess);
Cypress.Commands.add("verifySaveIndicator", verifySaveIndicator);
Cypress.Commands.add("openNodeDetailsTestingTab", openNodeDetailsTestingTab);
export default {};
