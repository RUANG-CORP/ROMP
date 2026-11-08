/**
 * CEF Mobile Bridge & Unified API
 * Compatible with desktop samp-cef (cef.emit & cef.on)
 */
(function() {
    var root = typeof window !== 'undefined' ? window : global;
    root.cef = root.cef || {};
    root.cef._listeners = root.cef._listeners || {};

    /**
     * Emits an event from Web UI to SA-MP/OMP server via JNI AndroidBridge.
     * Supports variadic arguments:
     *   cef.emit("login", username, password) -> sends JSON array
     *   cef.emit("hud:ready", { client: "android" }) -> sends JSON object
     */
    root.cef.emit = function(event) {
        var args = Array.prototype.slice.call(arguments, 1);
        var payload;
        if (args.length === 1 && typeof args[0] === 'object' && args[0] !== null) {
            payload = JSON.stringify(args[0]);
        } else if (args.length === 1 && typeof args[0] === 'string') {
            payload = JSON.stringify(args[0]);
        } else {
            payload = JSON.stringify(args);
        }

        if (typeof AndroidBridge !== 'undefined' && AndroidBridge.sendToServer) {
            AndroidBridge.sendToServer(event, payload);
        } else {
            console.warn("[CEF Bridge] AndroidBridge is not available. Event dropped:", event, payload);
        }
    };

    /**
     * Subscribes to events emitted from the SA-MP/OMP server.
     */
    root.cef.on = function(event, callback) {
        if (!root.cef._listeners[event]) {
            root.cef._listeners[event] = [];
        }
        root.cef._listeners[event].push(callback);
    };

    /**
     * Internal trigger function called from Android CefManager evaluateJavascript.
     */
    root.cef._trigger = function(event, rawData) {
        var listeners = root.cef._listeners[event];
        if (!listeners || listeners.length === 0) {
            return;
        }

        var parsedData;
        if (typeof rawData === 'string') {
            try {
                parsedData = JSON.parse(rawData);
            } catch (e) {
                parsedData = rawData;
            }
        } else {
            parsedData = rawData;
        }

        for (var i = 0; i < listeners.length; i++) {
            try {
                listeners[i](parsedData);
            } catch (err) {
                console.error("[CEF Bridge] Error in listener for event:", event, err);
            }
        }
    };

    // UI Helper Methods for HUD & Dialogs
    root.cef.ui = {
        updateHUD: function(stats) {
            if (typeof document === 'undefined') return;
            if (stats.hp !== undefined) {
                var hpBar = document.getElementById('hp-fill');
                if (hpBar) hpBar.style.width = Math.max(0, Math.min(100, stats.hp)) + '%';
            }
            if (stats.armor !== undefined) {
                var armorBar = document.getElementById('armor-fill');
                if (armorBar) armorBar.style.width = Math.max(0, Math.min(100, stats.armor)) + '%';
            }
            if (stats.money !== undefined) {
                var moneyText = document.getElementById('money-val');
                if (moneyText) moneyText.innerText = '$' + Number(stats.money).toLocaleString();
            }
            if (stats.speed !== undefined) {
                var speedVal = document.getElementById('speed-val');
                if (speedVal) speedVal.innerText = stats.speed;
            }
        },
        showDialog: function(dialog) {
            if (typeof document === 'undefined') return;
            var modal = document.getElementById('cef-modal');
            var titleEl = document.getElementById('modal-title');
            var msgEl = document.getElementById('modal-message');
            var inputEl = document.getElementById('modal-input');
            if (titleEl) titleEl.innerText = dialog.title || 'Dialog';
            if (msgEl) msgEl.innerText = dialog.message || '';
            if (inputEl) {
                inputEl.style.display = dialog.hasInput ? 'block' : 'none';
                inputEl.value = '';
            }
            if (modal) modal.style.display = 'flex';
        },
        hideDialog: function() {
            if (typeof document === 'undefined') return;
            var modal = document.getElementById('cef-modal');
            if (modal) modal.style.display = 'none';
        }
    };

    // Auto-bind default listeners if in browser DOM
    if (typeof window !== 'undefined') {
        root.cef.on('hud:update', function(stats) {
            root.cef.ui.updateHUD(stats);
        });
        root.cef.on('dialog:show', function(data) {
            root.cef.ui.showDialog(data);
        });
        root.cef.on('dialog:hide', function() {
            root.cef.ui.hideDialog();
        });
    }
})();
