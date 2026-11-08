const assert = require('assert');

// Mock window and AndroidBridge
global.window = {};
global.AndroidBridge = {
    sent: [],
    sendToServer(event, jsonStr) {
        this.sent.push({ event, data: JSON.parse(jsonStr) });
    }
};

// Require cef.js
require('../app/src/main/assets/cef/cef.js');

// Test 1: window.cef exists
assert.ok(global.window.cef, "window.cef should be defined");

// Test 2: Variadic emit serialization
global.window.cef.emit("player:login", "admin", "secret123");
assert.strictEqual(global.AndroidBridge.sent.length, 1);
assert.strictEqual(global.AndroidBridge.sent[0].event, "player:login");
assert.deepStrictEqual(global.AndroidBridge.sent[0].data, ["admin", "secret123"]);

// Test 3: Single object emit serialization
global.window.cef.emit("hud:ready", { version: "1.0", platform: "android" });
assert.strictEqual(global.AndroidBridge.sent.length, 2);
assert.deepStrictEqual(global.AndroidBridge.sent[1].data, { version: "1.0", platform: "android" });

// Test 4: Event listener registration and trigger
let receivedData = null;
global.window.cef.on("hud:update_stats", (payload) => {
    receivedData = payload;
});
global.window.cef._trigger("hud:update_stats", JSON.stringify({ hp: 85, armor: 40, money: 15000 }));
assert.ok(receivedData, "Listener should have been triggered");
assert.strictEqual(receivedData.hp, 85);
assert.strictEqual(receivedData.armor, 40);
assert.strictEqual(receivedData.money, 15000);

// Test 5: Fallback if raw non-JSON string is triggered
let rawStringReceived = null;
global.window.cef.on("system:message", (msg) => {
    rawStringReceived = msg;
});
global.window.cef._trigger("system:message", "Welcome to SA-MP CEF Mobile!");
assert.strictEqual(rawStringReceived, "Welcome to SA-MP CEF Mobile!");

console.log("All CEF bridge unit tests passed!");
