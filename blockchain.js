import crypto from "crypto";

class Block {
	constructor(index, data, previousHash = "0") {
		this.index = index;
		this.timestamp = new Date().toISOString();
		this.data = data;
		this.previousHash = previousHash;
		this.nonce = 0;
		this.hash = this.computeHash();
	}

	computeHash() {
		const content = JSON.stringify({
			index: this.index,
			timestamp: this.timestamp,
			data: this.data,
			previousHash: this.previousHash,
			nonce: this.nonce,
		});
		return crypto.createHash("sha256").update(content).digest("hex");
	}

	mine(difficulty) {
		const target = "0".repeat(difficulty);
		while (!this.hash.startsWith(target)) {
			this.nonce++;
			this.hash = this.computeHash();
		}
	}
}

class SoulsChain {
	constructor() {
		this.difficulty = 2;
		this.chain = [this._genesis()];
		this.registeredDevices = new Set(); // deviceKey → already has a soul
		this.registeredHashes = new Set(); // fingerprintHash → already used
		this.registeredIds = new Set(); // soulId → taken
		this.sessions = new Map(); // sessionId → session object
		this.deviceToSoul = new Map(); // deviceKey → soulId
	}

	_genesis() {
		const b = new Block(0, { type: "GENESIS", message: "Souls Chain v1" }, "0");
		b.mine(this.difficulty);
		return b;
	}

	getLatestBlock() {
		return this.chain[this.chain.length - 1];
	}

	// STEP 1 — App checks if device is already registered
	checkDevice(deviceKey) {
		if (this.registeredDevices.has(deviceKey)) {
			const soulId = this.deviceToSoul.get(deviceKey);
			return { ok: true, registered: true, soulId };
		}
		return { ok: true, registered: false, message: "Device not registered. Go near a LAMP." };
	}

	// STEP 2 — App connects to LAMP via BT and starts a session
	startSession(deviceKey, lampId) {
		if (this.registeredDevices.has(deviceKey))
			return { ok: false, error: "Device already has a Soul" };

		const sessionId = crypto.randomBytes(8).toString("hex").toUpperCase();
		this.sessions.set(sessionId, {
			sessionId,
			deviceKey,
			lampId,
			stage: "WAITING_FOR_FINGERPRINT",
			createdAt: new Date().toISOString(),
			expiresAt: new Date(Date.now() + 5 * 60 * 1000).toISOString(), // 5 min TTL
		});

		return { ok: true, sessionId, message: "Session started. Place finger on LAMP." };
	}

	// STEP 3 — LAMP app calls this after scanning fingerprint
	lampVerify(sessionId, fingerprintHash, lampId) {
		const session = this.sessions.get(sessionId);

		if (!session)
			return { ok: false, error: "Session not found" };
		if (session.lampId !== lampId)
			return { ok: false, error: "LAMP ID mismatch" };
		if (session.stage !== "WAITING_FOR_FINGERPRINT")
			return { ok: false, error: "Session already processed" };
		if (new Date() > new Date(session.expiresAt))
			return { ok: false, error: "Session expired. Start again." };
		if (this.registeredHashes.has(fingerprintHash))
			return { ok: false, error: "Fingerprint already registered — duplicate human" };

		session.fingerprintHash = fingerprintHash;
		session.stage = "FINGERPRINT_RECEIVED";
		session.verifiedAt = new Date().toISOString();

		return { ok: true, sessionId, message: "User verified by LAMP ✓ App can now finalize." };
	}

	// STEP 4 — App polls session status
	getSession(sessionId) {
		return this.sessions.get(sessionId) || null;
	}

	// STEP 5 — App finalizes and mints Soul on chain
	finalizeSoul(sessionId, deviceKey) {
		const session = this.sessions.get(sessionId);

		if (!session)
			return { ok: false, error: "Session not found" };
		if (session.deviceKey !== deviceKey)
			return { ok: false, error: "Device key mismatch" };
		if (session.stage === "WAITING_FOR_FINGERPRINT")
			return { ok: false, stage: "WAITING", message: "Waiting for LAMP to scan fingerprint..." };
		if (session.stage === "COMPLETED")
			return { ok: false, error: "Already finalized" };
		if (this.registeredDevices.has(deviceKey))
			return { ok: false, error: "Device already has a Soul" };

		const { fingerprintHash, lampId } = session;

		const soulId = "SOUL-" + crypto.randomBytes(4).toString("hex").toUpperCase();
		const soulHash = crypto.createHash("sha256")
			.update(fingerprintHash + deviceKey)
			.digest("hex");

		const block = new Block(
			this.chain.length,
			{
				type: "SOUL_REGISTRATION",
				soulId,
				soulHash,
				deviceKey,
				fingerprintHash,
				lampId,
				sessionId,
				verifiedAt: session.verifiedAt,
			},
			this.getLatestBlock().hash
		);
		block.mine(this.difficulty);

		this.chain.push(block);
		this.registeredDevices.add(deviceKey);
		this.registeredHashes.add(fingerprintHash);
		this.registeredIds.add(soulId);
		this.deviceToSoul.set(deviceKey, soulId);
		session.stage = "COMPLETED";

		return { ok: true, soulId, soulHash, blockHash: block.hash, blockIndex: block.index };
	}

	getSoul(soulId) {
		return this.chain.find(b => b.data?.soulId === soulId) || null;
	}

	getSoulByDevice(deviceKey) {
		const soulId = this.deviceToSoul.get(deviceKey);
		return soulId ? this.getSoul(soulId) : null;
	}

	isValid() {
		for (let i = 1; i < this.chain.length; i++) {
			const cur = this.chain[i];
			const prev = this.chain[i - 1];
			if (cur.hash !== cur.computeHash()) return false;
			if (cur.previousHash !== prev.hash) return false;
		}
		return true;
	}
}

export default SoulsChain;
