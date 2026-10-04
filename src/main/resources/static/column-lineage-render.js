(function () {
    function ready(fn) {
        if (window.jsPlumb) fn();
        else setTimeout(function () { ready(fn); }, 30);
    }

  // Exposed entry point: call this with the `mappings` array (e.g. fetched
  // from an API) to draw the column-lineage diagram.
  var instance = null;

  function repaint() { if (instance) instance.repaintEverything(); }
  window.addEventListener("resize", repaint);
  document.getElementById("canvas-wrap").addEventListener("scroll", repaint);

  window.renderColumnLineage = function (mappings) {
    ready(function () {
      jsPlumb.ready(function () {
        // Drop connections/endpoints from a previous render before drawing again.
        if (instance) instance.reset();
        instance = jsPlumb.getInstance({
          Container: "canvas",
          Connector: ["Bezier", { curviness: 60 }],
          Endpoint: ["Dot", { radius: 4 }],
          EndpointStyle: { fill: "#b08968", stroke: "none" },
          PaintStyle: { stroke: "#b08968", strokeWidth: 1.6 },
          HoverPaintStyle: { stroke: "#2f5d50", strokeWidth: 2.2 },
          Anchors: ["Right", "Left"]
        });

        var connections = [];

        function rowSourceAnchor(rowEl) {
          return [1, 0.5, 1, 0];
        }
        function rowTargetAnchor(rowEl) {
          return [0, 0.5, -1, 0];
        }

        mappings.forEach(function (m) {
          var fromEl = document.querySelector('[data-row="' + m.from + '"]');
          var toEl = document.querySelector('[data-row="' + m.to + '"]');
          if (!fromEl || !toEl) return;

          var conn = instance.connect({
            source: fromEl,
            target: toEl,
            anchors: [rowSourceAnchor(fromEl), rowTargetAnchor(toEl)],
            connector: ["Bezier", { curviness: 60 }],
            paintStyle: {
              stroke: "#b08968",
              strokeWidth: 1.6,
              dashstyle: m.dashed ? "4 3" : "0"
            },
            hoverPaintStyle: { stroke: "#2f5d50", strokeWidth: 2.4 },
            endpoint: ["Dot", { radius: 3.5 }],
            endpointStyle: { fill: "#b08968", stroke: "none" },
            overlays: [
              ["Label", { label: m.label, location: 0.5, cssClass: "jtk-overlay" }]
            ]
          });

          conn._fromRow = fromEl;
          conn._toRow = toEl;
          connections.push(conn);

          [fromEl, toEl].forEach(function (rowEl) {
            rowEl.addEventListener("mouseenter", function () {
              fromEl.classList.add("row-highlight");
              toEl.classList.add("row-highlight");
              conn.setPaintStyle({ stroke: "#2f5d50", strokeWidth: 2.6, dashstyle: m.dashed ? "4 3" : "0" });
            });
            rowEl.addEventListener("mouseleave", function () {
              fromEl.classList.remove("row-highlight");
              toEl.classList.remove("row-highlight");
              conn.setPaintStyle({ stroke: "#b08968", strokeWidth: 1.6, dashstyle: m.dashed ? "4 3" : "0" });
            });
          });
        });

        setTimeout(repaint, 50);
      });
    });
  };
  
})();
