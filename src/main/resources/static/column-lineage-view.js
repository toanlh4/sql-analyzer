// View controls for the lineage canvas: pan & zoom (Panzoom library), a minimap,
// and a right-click menu on column rows to copy their names.
(function () {
    var SVG_NS = "http://www.w3.org/2000/svg";
    var viewport = document.getElementById("viewport");
    var wrap = document.getElementById("canvas-wrap");
    var canvas = document.getElementById("canvas");
    var mini = document.getElementById("minimap");
    var zoomLabel = document.getElementById("btn-zoom-reset");
    var jsp = null; // current jsPlumb instance, handed over by attach()

    // ---- pan & zoom -------------------------------------------------------

    var panzoom = Panzoom(canvas, {
        canvas: true, // drag anywhere inside #canvas-wrap, not only on the tables
        cursor: "grab",
        minScale: 0.2,
        maxScale: 2,
        step: 0.2
    });
    wrap.addEventListener("wheel", panzoom.zoomWithWheel, { passive: false });

    // Panzoom pans on any mouse button; leave right/middle clicks to the browser
    // so the column context menu still opens.
    viewport.addEventListener("pointerdown", function (e) {
        if (e.button !== 0) e.stopPropagation();
    }, true);

    canvas.addEventListener("panzoomchange", function (e) {
        // jsPlumb must know the scale so its geometry matches the transformed canvas.
        if (jsp) jsp.setZoom(e.detail.scale);
        zoomLabel.textContent = Math.round(e.detail.scale * 100) + "%";
        drawViewRect();
        hideMenu();
    });

    document.getElementById("btn-zoom-in").addEventListener("click", function () { panzoom.zoomIn(); });
    document.getElementById("btn-zoom-out").addEventListener("click", function () { panzoom.zoomOut(); });
    zoomLabel.addEventListener("click", function () { panzoom.reset(); });
    document.getElementById("btn-zoom-fit").addEventListener("click", fit);

    // Panzoom scales around the canvas centre C, so a canvas point p lands at
    // C + s * (p - C + t) in #canvas-wrap, where s is the scale and t the pan.
    function centre() {
        return { x: canvas.offsetWidth / 2, y: canvas.offsetHeight / 2 };
    }

    // Pan so canvas point (x, y) sits in the middle of the view.
    function centreOn(x, y, animate) {
        var s = panzoom.getScale(), c = centre();
        panzoom.pan((wrap.clientWidth / 2 - c.x) / s - x + c.x,
            (wrap.clientHeight / 2 - c.y) / s - y + c.y, { animate: animate });
    }

    function fit() {
        panzoom.zoom(Math.min(wrap.clientWidth / canvas.offsetWidth,
            wrap.clientHeight / canvas.offsetHeight, 1), { animate: true });
        var c = centre();
        centreOn(c.x, c.y, true);
    }

    // The part of the canvas currently visible, in canvas coordinates.
    function visibleRect() {
        var s = panzoom.getScale(), t = panzoom.getPan(), c = centre();
        return {
            x: c.x - c.x / s - t.x,
            y: c.y - c.y / s - t.y,
            w: wrap.clientWidth / s,
            h: wrap.clientHeight / s
        };
    }

    // ---- minimap ----------------------------------------------------------

    var viewRect = null;
    var miniDragging = false;

    function svgEl(tag, attrs) {
        var node = document.createElementNS(SVG_NS, tag);
        Object.keys(attrs).forEach(function (k) { node.setAttribute(k, attrs[k]); });
        return node;
    }

    // Untransformed box of an element relative to #canvas.
    function boxOf(node) {
        var x = 0, y = 0;
        for (var n = node; n && n !== canvas; n = n.offsetParent) {
            x += n.offsetLeft;
            y += n.offsetTop;
        }
        return { x: x, y: y, w: node.offsetWidth, h: node.offsetHeight };
    }

    // Tables, wires and the view rectangle are all drawn in canvas coordinates;
    // drawViewRect() sets the viewBox to cover both the canvas and the view.
    function drawMinimap() {
        mini.textContent = "";
        canvas.querySelectorAll(".table:not(.table-hidden)").forEach(function (table) {
            var b = boxOf(table);
            mini.appendChild(svgEl("rect", {
                x: b.x, y: b.y, width: b.w, height: b.h,
                "class": "mm-table " + (table.classList.contains("target") ? "mm-target" : "mm-source")
            }));
        });
        if (jsp) jsp.getAllConnections().forEach(function (conn) {
            if (!conn.isVisible()) return;
            var a = boxOf(conn.source), b = boxOf(conn.target);
            var x1 = a.x + a.w, y1 = a.y + a.h / 2, x2 = b.x, y2 = b.y + b.h / 2, dx = (x2 - x1) / 2;
            mini.appendChild(svgEl("path", {
                d: "M" + x1 + " " + y1 + " C" + (x1 + dx) + " " + y1 + " " + (x2 - dx) + " " + y2 + " " + x2 + " " + y2,
                "class": "mm-wire"
            }));
        });
        viewRect = svgEl("rect", { "class": "mm-view" });
        mini.appendChild(viewRect);
        drawViewRect();
    }

    function drawViewRect() {
        if (!viewRect) return;
        var r = visibleRect();
        viewRect.setAttribute("x", r.x);
        viewRect.setAttribute("y", r.y);
        viewRect.setAttribute("width", r.w);
        viewRect.setAttribute("height", r.h);
        // Hold the viewBox still while dragging on the minimap, otherwise the
        // point under the pointer shifts and the view drifts away.
        if (miniDragging) return;
        var x = Math.min(0, r.x), y = Math.min(0, r.y);
        mini.setAttribute("viewBox", [x, y,
            Math.max(canvas.offsetWidth, r.x + r.w) - x,
            Math.max(canvas.offsetHeight, r.y + r.h) - y].join(" "));
    }

    // Click or drag on the minimap to centre the main view on that spot.
    function miniPoint(e) {
        var pt = mini.createSVGPoint();
        pt.x = e.clientX;
        pt.y = e.clientY;
        return pt.matrixTransform(mini.getScreenCTM().inverse());
    }
    function endMiniDrag() {
        miniDragging = false;
        drawViewRect();
    }
    mini.addEventListener("pointerdown", function (e) {
        if (e.button !== 0) return;
        miniDragging = true;
        mini.setPointerCapture(e.pointerId);
        var p = miniPoint(e);
        centreOn(p.x, p.y, false);
    });
    mini.addEventListener("pointermove", function (e) {
        if (!miniDragging) return;
        var p = miniPoint(e);
        centreOn(p.x, p.y, false);
    });
    mini.addEventListener("pointerup", endMiniDrag);
    mini.addEventListener("pointercancel", endMiniDrag);

    window.addEventListener("resize", drawMinimap);

    // ---- column context menu ----------------------------------------------

    var menu = document.getElementById("ctx-menu");
    var qualifiedItem = menu.querySelector('[data-copy="qualified"]');
    var showAllItem = menu.querySelector('[data-action="show-all"]');
    var toast = document.getElementById("toast");
    var menuRow = null;
    var toastTimer = null;

    function columnName(row) {
        return row.querySelector(".col-name").textContent;
    }
    // Source rows carry "src-<schema>.<table>.<column>"; target rows have no table.
    function qualifiedName(row) {
        var id = row.getAttribute("data-row") || "";
        return id.indexOf("src-") === 0 ? id.slice(4) : null;
    }

    canvas.addEventListener("contextmenu", function (e) {
        var row = e.target.closest(".row");
        if (!row) return;
        e.preventDefault();
        menuRow = row;
        qualifiedItem.hidden = !qualifiedName(row);
        showAllItem.hidden = !focusedRow;
        menu.hidden = false;
        // Keep the menu inside the window near the right/bottom edges.
        menu.style.left = Math.min(e.clientX, window.innerWidth - menu.offsetWidth - 4) + "px";
        menu.style.top = Math.min(e.clientY, window.innerHeight - menu.offsetHeight - 4) + "px";
        menu.querySelector("button").focus();
    });

    menu.addEventListener("click", function (e) {
        var item = e.target.closest("[data-copy], [data-action]");
        if (!item || !menuRow) return;
        var row = menuRow;
        hideMenu();
        var action = item.getAttribute("data-action");
        if (action === "focus") return focusRow(row);
        if (action === "show-all") return showAll();
        var text = item.getAttribute("data-copy") === "qualified" ? qualifiedName(row) : columnName(row);
        copyText(text).then(function () {
            showToast("copied " + text);
        }, function (err) {
            console.error("copy failed:", err);
            showToast("could not copy to clipboard");
        });
    });

    function hideMenu() {
        menu.hidden = true;
        menuRow = null;
    }
    document.addEventListener("pointerdown", function (e) {
        if (!menu.hidden && !menu.contains(e.target)) hideMenu();
    });
    document.addEventListener("keydown", function (e) {
        if (e.key === "Escape") hideMenu();
    });
    window.addEventListener("blur", hideMenu);
    window.addEventListener("resize", hideMenu);

    // The async Clipboard API needs a secure context (https, localhost or file);
    // fall back to the legacy copy command elsewhere or if it is refused.
    function copyText(text) {
        if (navigator.clipboard && window.isSecureContext) {
            return navigator.clipboard.writeText(text).catch(function () { return legacyCopy(text); });
        }
        return legacyCopy(text);
    }
    function legacyCopy(text) {
        return new Promise(function (resolve, reject) {
            var ta = document.createElement("textarea");
            ta.value = text;
            ta.setAttribute("readonly", "");
            ta.style.position = "fixed";
            ta.style.opacity = "0";
            document.body.appendChild(ta);
            ta.select();
            var ok = document.execCommand("copy");
            ta.remove();
            if (ok) resolve();
            else reject(new Error("copy command was rejected"));
        });
    }

    function showToast(message) {
        toast.textContent = message;
        toast.hidden = false;
        clearTimeout(toastTimer);
        toastTimer = setTimeout(function () { toast.hidden = true; }, 1600);
    }

    // ---- show only one column's lineage -----------------------------------

    var btnShowAll = document.getElementById("btn-show-all");
    var focusedRow = null;
    btnShowAll.addEventListener("click", showAll);

    // Keep `row` and the rows wired to it (its sources for a target column,
    // its targets for a source column); hide every other row.
    function focusRow(row) {
        var keep = [row];
        if (jsp) jsp.getAllConnections().forEach(function (conn) {
            if (conn.source === row) keep.push(conn.target);
            if (conn.target === row) keep.push(conn.source);
        });
        canvas.querySelectorAll(".row").forEach(function (r) {
            r.classList.toggle("row-hidden", keep.indexOf(r) < 0);
            r.classList.toggle("row-focus", r === row);
        });
        focusedRow = row;
        applyVisibility();
    }

    function showAll() {
        canvas.querySelectorAll(".row-hidden, .row-focus").forEach(function (r) {
            r.classList.remove("row-hidden", "row-focus");
        });
        focusedRow = null;
        applyVisibility();
    }

    // Hide tables left without rows and connections that end on a hidden row,
    // then let jsPlumb move the remaining lines to the rows' new positions.
    function applyVisibility() {
        canvas.querySelectorAll(".table").forEach(function (table) {
            table.classList.toggle("table-hidden", !table.querySelector(".row:not(.row-hidden)"));
        });
        if (jsp) {
            jsp.getAllConnections().forEach(function (conn) {
                var visible = !conn.source.classList.contains("row-hidden") &&
                    !conn.target.classList.contains("row-hidden");
                conn.setVisible(visible);
                conn.endpoints.forEach(function (ep) { ep.setVisible(visible); });
            });
            jsp.repaintEverything();
        }
        btnShowAll.hidden = !focusedRow;
        drawMinimap();
        fit();
    }

    // ---- hooks for column-lineage-render.js -------------------------------

    window.lineageView = {
        // A new diagram is about to be drawn with this jsPlumb instance.
        attach: function (instance) {
            jsp = instance;
            focusedRow = null; // the new tables are built unfiltered
            btnShowAll.hidden = true;
            jsp.setZoom(1);
            panzoom.reset({ animate: false });
        },
        // Tables and connections are in place; redraw the minimap.
        refresh: drawMinimap
    };

    drawMinimap();
})();
