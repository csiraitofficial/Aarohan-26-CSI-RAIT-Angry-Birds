import express from "express";
import SoulsChain from "./blockchain.js";

const app = express();
const chain = new SoulsChain();
app.use(express.json());

// ─────────────────────────────────────────────────────────────────────────────
//  USER APP ROUTES
// ─────────────────────────────────────────────────────────────────────────────

// STEP 1 — App checks if device already has a Soul
// Called on every app launch
// GET /device/:deviceKey
app.get("/device/:deviceKey", (req, res) => {
	const result = chain.checkDevice(req.params.deviceKey);
	res.json(result);
});

// STEP 2 — App connects to LAMP via BT and starts a session
// Called after BT connection is established
// POST /session/start  { deviceKey, lampId }
app.post("/session/start", (req, res) => {
	const { deviceKey, lampId } = req.body;
	if (!deviceKey || !lampId)
		return res.status(400).json({ ok: false, error: "Missing deviceKey or lampId" });

	const result = chain.startSession(deviceKey, lampId);
	res.status(result.ok ? 201 : 409).json(result);
});

// STEP 4 — App polls to check if LAMP has verified the fingerprint
// GET /session/:sessionId
app.get("/session/:sessionId", (req, res) => {
	const session = chain.getSession(req.params.sessionId);
	if (!session) return res.status(404).json({ ok: false, error: "Session not found" });

	// Don't expose raw fingerprint hash to polling
	res.json({
		ok: true,
		sessionId: session.sessionId,
		stage: session.stage,        // WAITING_FOR_FINGERPRINT | FINGERPRINT_RECEIVED | COMPLETED
		lampId: session.lampId,
		createdAt: session.createdAt,
		expiresAt: session.expiresAt,
		verifiedAt: session.verifiedAt || null,
	});
});

// STEP 5 — App finalizes and mints the Soul on chain
// Called once session stage = FINGERPRINT_RECEIVED
// POST /soul/finalize  { sessionId, deviceKey }
app.post("/soul/finalize", (req, res) => {
	const { sessionId, deviceKey } = req.body;
	if (!sessionId || !deviceKey)
		return res.status(400).json({ ok: false, error: "Missing sessionId or deviceKey" });

	const result = chain.finalizeSoul(sessionId, deviceKey);
	res.status(result.ok ? 201 : 409).json(result);
});

// Lookup a Soul by ID
// GET /soul/:soulId
app.get("/soul/:soulId", (req, res) => {
	const block = chain.getSoul(req.params.soulId);
	if (!block) return res.status(404).json({ ok: false, error: "Soul not found" });
	res.json({ ok: true, block });
});

// Lookup Soul by device
// GET /soul/by-device/:deviceKey
app.get("/soul/by-device/:deviceKey", (req, res) => {
	const block = chain.getSoulByDevice(req.params.deviceKey);
	if (!block) return res.status(404).json({ ok: false, error: "No Soul for this device" });
	res.json({ ok: true, block });
});

// ─────────────────────────────────────────────────────────────────────────────
//  LAMP APP ROUTES
// ─────────────────────────────────────────────────────────────────────────────

// STEP 3 — LAMP app POSTs after scanning user's fingerprint
// This is what the LAMP phone app calls
// POST /lamp/verify  { sessionId, lampId, fingerprintHash }
app.post("/lamp/verify", (req, res) => {
	const { sessionId, lampId, fingerprintHash } = req.body;
	if (!sessionId || !lampId || !fingerprintHash)
		return res.status(400).json({ ok: false, error: "Missing sessionId, lampId, or fingerprintHash" });

	const result = chain.lampVerify(sessionId, fingerprintHash, lampId);
	res.status(result.ok ? 200 : 409).json(result);
});

// ─────────────────────────────────────────────────────────────────────────────
//  CHAIN ROUTES
// ─────────────────────────────────────────────────────────────────────────────

// View entire blockchain
app.get("/chain", (req, res) => {
	res.json({ length: chain.chain.length, valid: chain.isValid(), blocks: chain.chain });
});

// Validate chain integrity
app.get("/validate", (req, res) => {
	const valid = chain.isValid();
	res.json({ valid, message: valid ? "Chain is intact ✓" : "⚠ Chain tampered!" });
});

// ─────────────────────────────────────────────────────────────────────────────
const PORT = process.env.PORT || 3000;
app.listen(PORT, () => {
	console.log(`\n⛓  Souls Blockchain API  →  http://localhost:${PORT}\n`);
	console.log("  USER APP");
	console.log("  GET  /device/:deviceKey          — check if device has a soul");
	console.log("  POST /session/start              — start BT session with LAMP");
	console.log("  GET  /session/:sessionId         — poll session status");
	console.log("  POST /soul/finalize              — mint Soul after LAMP verifies");
	console.log("  GET  /soul/:soulId               — lookup soul");
	console.log("  GET  /soul/by-device/:deviceKey  — lookup soul by device\n");
	console.log("  LAMP APP");
	console.log("  POST /lamp/verify                — LAMP pushes fingerprint result\n");
	console.log("  CHAIN");
	console.log("  GET  /chain                      — view full blockchain");
	console.log("  GET  /validate                   — verify integrity\n");
});
