import sqlite3InitModule from '@sqlite.org/sqlite-wasm';

let sqlite3 = null;

const databases = new Map();
const statements = new Map();
let nextDatabaseId = 0;
let nextStatementId = 0;

function openRequest(id, requestData) {
    try {
        const databaseId = nextDatabaseId++;
        const database = new sqlite3.oo1.OpfsDb(requestData.fileName);
        databases.set(databaseId, database);
        postMessage({ id, data: { databaseId } });
    } catch (error) {
        postMessage({ id, error: error.message });
    }
}

function prepareRequest(id, requestData) {
    try {
        const database = databases.get(requestData.databaseId);
        if (!database) {
            postMessage({ id, error: `Invalid database ID: ${requestData.databaseId}` });
            return;
        }

        const statementId = nextStatementId++;
        const statement = database.prepare(requestData.sql);
        statements.set(statementId, statement);
        postMessage({
            id,
            data: {
                statementId,
                parameterCount: sqlite3.capi.sqlite3_bind_parameter_count(statement),
                columnNames: Array.from(
                    { length: statement.columnCount },
                    (_, index) => sqlite3.capi.sqlite3_column_name(statement, index)
                )
            }
        });
    } catch (error) {
        postMessage({ id, error: error.message });
    }
}

function stepRequest(id, requestData) {
    const statement = statements.get(requestData.statementId);
    if (!statement) {
        postMessage({ id, error: `Invalid statement ID: ${requestData.statementId}` });
        return;
    }

    try {
        const rows = [];
        const columnTypes = [];
        statement.reset();
        statement.clearBindings();
        requestData.bindings.forEach((binding, index) => statement.bind(index + 1, binding));

        while (statement.step()) {
            if (columnTypes.length === 0) {
                for (let index = 0; index < statement.columnCount; index++) {
                    columnTypes.push(sqlite3.capi.sqlite3_column_type(statement, index));
                }
            }
            rows.push(statement.get([]));
        }
        postMessage({ id, data: { rows, columnTypes } });
    } catch (error) {
        postMessage({ id, error: error.message });
    }
}

function closeRequest(id, requestData) {
    if (requestData.statementId != null) {
        const statement = statements.get(requestData.statementId);
        if (!statement) {
            postMessage({ id, error: `Invalid statement ID: ${requestData.statementId}` });
            return;
        }
        try {
            statement.finalize();
            statements.delete(requestData.statementId);
        } catch (error) {
            postMessage({ id, error: error.message });
        }
    }

    if (requestData.databaseId != null) {
        const database = databases.get(requestData.databaseId);
        if (!database) {
            postMessage({ id, error: `Invalid database ID: ${requestData.databaseId}` });
            return;
        }
        try {
            database.close();
            databases.delete(requestData.databaseId);
        } catch (error) {
            postMessage({ id, error: error.message });
        }
    }
}

const commandMap = {
    open: openRequest,
    prepare: prepareRequest,
    step: stepRequest,
    close: closeRequest
};

function handleMessage(event) {
    const request = event.data;
    if (!request.data || !request.data.cmd) {
        postMessage({ id: request.id, error: "Invalid request, missing 'data' or 'cmd'." });
        return;
    }

    const handler = commandMap[request.data.cmd];
    if (handler) {
        handler(request.id, request.data);
    } else {
        postMessage({ id: request.id, error: `Invalid request, unknown command: '${request.data.cmd}'.` });
    }
}

const messageQueue = [];
onmessage = (event) => {
    if (sqlite3) {
        handleMessage(event);
    } else {
        messageQueue.push(event);
    }
};

sqlite3InitModule().then((instance) => {
    sqlite3 = instance;
    while (messageQueue.length > 0) {
        handleMessage(messageQueue.shift());
    }
});
