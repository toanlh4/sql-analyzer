(function () {
    var modalDb = document.getElementById("modal-db");
    var modalSql = document.getElementById("modal-sql");
    var formDb = document.getElementById("form-db");
    var formSql = document.getElementById("form-sql");

    document.getElementById("btn-open-db").addEventListener("click", function () {
        modalDb.showModal();
    });
    document.getElementById("btn-open-sql").addEventListener("click", function () {
        modalSql.showModal();
    });

    // Cancel buttons close their dialog without validating the form.
    document.querySelectorAll(".modal [data-close]").forEach(function (btn) {
        btn.addEventListener("click", function () {
            btn.closest("dialog").close();
        });
    });

    // Clicking the backdrop closes the dialog.
    [modalDb, modalSql].forEach(function (dlg) {
        dlg.addEventListener("click", function (e) {
            if (e.target === dlg)
                dlg.close();
        });
    });

    // just closes the dialog, values stay in the inputs.
    formDb.addEventListener("submit", function () {
        collectUserInputs();
        console.log("Database connection saved");
    });

    formSql.addEventListener("submit", function () {
        submitAnalysis(collectUserInputs());
    });

    // Mockup: gather everything the user entered in both modals.
    function collectUserInputs() {
        var db = document.getElementById("form-db").elements;
        var sql = document.getElementById("form-sql").elements;
        return {
            connectionString: db.connectionString.value.trim(),
            username: db.username.value.trim(),
            password: db.password.value,
            schema: db.schema.value.trim(),
            sql: sql.sql.value.trim()
        };
    }

    // POST the user inputs as JSON and log the JSON response.
    function submitAnalysis(payload) {
        return fetch("/api/analyze-sql/column-lineage", {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
                "Accept": "application/json"
            },
            body: JSON.stringify(payload)
        }).then(function (res) {
            if (!res.ok) {
                throw new Error("HTTP " + res.status + " " + res.statusText);
            }
            return res.json();
        }).then(function (data) {
            var mappings = toMappings(data);
            console.log("Mappings:", mappings);
            buildTables(data);
            window.renderColumnLineage(mappings);
            return data;
        }).catch(function (err) {
            console.error("submitAnalysis failed:", err);
        });
    }

    // Row ids used in `data-row` attributes.
    // Source: "src-<schema>.<table>.<column>", target: "tgt-<column>".
    function sourceRowId(origin) {
        return "src-" + origin.qualifiedNames.concat(origin.column).join(".");
    }
    function targetRowId(targetColumn) {
        return "tgt-" + targetColumn;
    }

    // Flatten the API response into one mapping per (source column -> target column).
    // Derived origins (expressions over several columns) are drawn dashed.
    function toMappings(response) {
        var mappings = [];
        (response.columnLineages || []).forEach(function (lineage) {
            (lineage.columnOrigins || []).forEach(function (origin) {
                mappings.push({
                    from: sourceRowId(origin),
                    to: targetRowId(lineage.targetColumn),
                    label: origin.isDerived ? "derived" : "direct",
                    dashed: origin.isDerived
                });
            });
        });
        return mappings;
    }

    // Rebuild the table cards inside #canvas from the API response:
    // one card per source table (left column) and one target card (right column).
    function buildTables(response) {
        var lineages = (response.columnLineages || []).slice().sort(function (a, b) {
            return a.targetColumnOrdinalPosition - b.targetColumnOrdinalPosition;
        });

        // Group source columns by qualified table name. A column referenced by several
        // origins keeps its lowest ordinalPosition (and that origin's dataType).
        var sources = {};
        var sourceOrder = [];
        lineages.forEach(function (lineage) {
            (lineage.columnOrigins || []).forEach(function (origin) {
                var key = origin.qualifiedNames.join(".");
                if (!sources[key]) {
                    sources[key] = { qualifiedNames: origin.qualifiedNames, columns: {} };
                    sourceOrder.push(key);
                }
                var existing = sources[key].columns[origin.column];
                if (!existing || origin.ordinalPosition < existing.ordinalPosition) {
                    sources[key].columns[origin.column] = {
                        column: origin.column,
                        dataType: origin.dataType,
                        ordinalPosition: origin.ordinalPosition
                    };
                }
            });
        });

        function byOrdinal(a, b) {
            return a.ordinalPosition - b.ordinalPosition;
        }
        function sortedColumns(src) {
            return Object.keys(src.columns).map(function (name) {
                return src.columns[name];
            }).sort(byOrdinal);
        }

        // Order source tables by their lowest column ordinalPosition.
        sourceOrder.sort(function (a, b) {
            return byOrdinal(sortedColumns(sources[a])[0], sortedColumns(sources[b])[0]);
        });

        var sourceCol = el("div", "table-col sources");
        sourceOrder.forEach(function (key) {
            var src = sources[key];
            var names = src.qualifiedNames;
            var schema = names.slice(0, -1).join(".");
            var card = tableCard("source", schema ? "source: " + schema : "source", names[names.length - 1]);
            sortedColumns(src).forEach(function (col) {
                card.appendChild(row(sourceRowId({ qualifiedNames: names, column: col.column }),
                    col.column, col.dataType));
            });
            sourceCol.appendChild(card);
        });

        var targetCol = el("div", "table-col targets");
        var targetCard = tableCard("target", "target", "query result");
        lineages.forEach(function (lineage) {
            targetCard.appendChild(row(targetRowId(lineage.targetColumn), lineage.targetColumn,
                "#" + lineage.targetColumnOrdinalPosition));
        });
        targetCol.appendChild(targetCard);

        var canvas = document.getElementById("canvas");
        canvas.innerHTML = "";
        canvas.appendChild(sourceCol);
        canvas.appendChild(targetCol);
    }

    function tableCard(side, kind, name) {
        var card = el("div", "table " + side);
        var head = el("div", "table-head");
        head.appendChild(el("div", "kind", kind));
        head.appendChild(el("div", "name", name));
        card.appendChild(head);
        return card;
    }

    function row(rowId, colName, colType) {
        var r = el("div", "row");
        r.setAttribute("data-row", rowId);
        r.appendChild(el("span", "col-name", colName));
        if (colType) r.appendChild(el("span", "col-type", colType));
        return r;
    }

    // textContent (not innerHTML) so column names from the SQL can't inject markup.
    function el(tag, className, text) {
        var node = document.createElement(tag);
        node.className = className;
        if (text != null) node.textContent = text;
        return node;
    }
})();
