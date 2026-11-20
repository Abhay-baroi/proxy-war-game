const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const CHARLIE_UID = "charlie_789";
const ROOM_CODE = "X7K9";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastDate(msAgo = 2000) {
  return new Date(Date.now() - msAgo);
}

function futureDate(msAhead = 60000) {
  return new Date(Date.now() + msAhead);
}

function makeRoomPayload(hostId = ALICE_UID, extra = {}) {
  const ts = pastDate(5000);
  return {
    roomCode: ROOM_CODE,
    hostId,
    memberUids: [hostId],
    phase: "LOBBY",
    phaseEndsAtMs: 0,
    phaseDurationMs: 0,
    roundNum: 1,
    totalRounds: 3,
    openTable: true,
    createdAt: ts,
    updatedAt: ts,
    ...extra,
  };
}

function makePlayerPayload(userId, hostId = ALICE_UID, memberUids = [ALICE_UID], hostFlag = false) {
  const ts = pastDate(5000);
  return {
    userId,
    roomId: ROOM_CODE,
    hostId,
    memberUids,
    nickname: userId.slice(0, 10),
    hostFlag,
    score: 0,
    totalVoteTimeMs: 0,
    hasSubmittedAnswer: false,
    hasVoted: false,
    createdAt: ts,
    updatedAt: ts,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated users: denied across all collections and subcollections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const ts = pastDate();

  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      nickname: "Anon",
      matchesPlayed: 0,
      totalPoints: 0,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertFails(unauthDb.collection("rooms").where("openTable", "==", true).where("phase", "==", "LOBBY").get());
  await assertFails(unauthDb.collection("rooms").doc(ROOM_CODE).set(makeRoomPayload()));
  await assertFails(unauthDb.collection("rooms").doc(ROOM_CODE).collection("secrets").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("rooms").doc(ROOM_CODE).collection("public_answers").get());
});

test("Users collection: owner CRUD succeeds, cross-user & timestamp tampering fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = pastDate(5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      nickname: "Alice",
      matchesPlayed: 0,
      totalPoints: 0,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Reject extra @DocumentId 'id' field and future timestamps
  await assertFails(
    bobDb.collection("users").doc(BOB_UID).set({
      id: BOB_UID,
      userId: BOB_UID,
      nickname: "Bob",
      matchesPlayed: 0,
      totalPoints: 0,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertFails(
    bobDb.collection("users").doc(BOB_UID).set({
      userId: BOB_UID,
      nickname: "Bob",
      matchesPlayed: 0,
      totalPoints: 0,
      createdAt: futureDate(),
      updatedAt: ts,
    })
  );

  // Owner get, filtered list, partial update, delete
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());
  await assertSucceeds(aliceDb.collection("users").where("userId", "==", ALICE_UID).get());
  await assertFails(aliceDb.collection("users").get());
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      nickname: "AliceUpdated",
      matchesPlayed: 1,
      totalPoints: 250,
      updatedAt: pastDate(1000),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: pastDate(500),
      updatedAt: pastDate(500),
    })
  );

  // Cross-user isolation
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).update({ nickname: "Hijacked", updatedAt: pastDate(500) }));
  await assertFails(bobDb.collection("users").doc(ALICE_UID).delete());
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).delete());
});

test("Rooms lifecycle: create, open-table query, join, host phase updates, leave & delete", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();

  // Host creates room
  await assertSucceeds(aliceDb.collection("rooms").doc(ROOM_CODE).set(makeRoomPayload(ALICE_UID)));

  // Reject invalid room code or spoofed host
  await assertFails(
    bobDb.collection("rooms").doc("bad1").set(makeRoomPayload(BOB_UID, { roomCode: "bad1" }))
  );
  await assertFails(
    bobDb.collection("rooms").doc("Z9M2").set(makeRoomPayload(ALICE_UID, { roomCode: "Z9M2" }))
  );

  // Open Table filtered query succeeds; unfiltered query fails
  await assertSucceeds(
    bobDb.collection("rooms").where("openTable", "==", true).where("phase", "==", "LOBBY").get()
  );
  await assertFails(bobDb.collection("rooms").get());

  // Bob reads LOBBY room & joins
  await assertSucceeds(bobDb.collection("rooms").doc(ROOM_CODE).get());
  await assertSucceeds(
    bobDb.collection("rooms").doc(ROOM_CODE).update({
      memberUids: [ALICE_UID, BOB_UID],
      updatedAt: pastDate(1000),
    })
  );

  // Non-host Bob cannot advance game phase or hijack hostId
  await assertFails(
    bobDb.collection("rooms").doc(ROOM_CODE).update({
      phase: "ROLE_REVEAL",
      updatedAt: pastDate(500),
    })
  );
  await assertFails(
    bobDb.collection("rooms").doc(ROOM_CODE).update({
      hostId: BOB_UID,
      updatedAt: pastDate(500),
    })
  );

  // Host Alice advances phase to ROLE_REVEAL
  await assertSucceeds(
    aliceDb.collection("rooms").doc(ROOM_CODE).update({
      phase: "ROLE_REVEAL",
      phaseEndsAtMs: Date.now() + 15000,
      phaseDurationMs: 15000,
      roundNum: 1,
      promptId: "p1",
      promptCategory: "Campus",
      promptText: "Why did the Wi-Fi drop during finals?",
      glitchPlayerIds: [],
      updatedAt: pastDate(500),
    })
  );

  // Non-member Charlie cannot read non-LOBBY room
  await assertFails(charlieDb.collection("rooms").doc(ROOM_CODE).get());
  // Non-member Charlie cannot join once phase is not LOBBY
  await assertFails(
    charlieDb.collection("rooms").doc(ROOM_CODE).update({
      memberUids: [ALICE_UID, BOB_UID, CHARLIE_UID],
      updatedAt: pastDate(300),
    })
  );

  // Member Bob can leave; non-host cannot delete; host can delete
  await assertSucceeds(
    bobDb.collection("rooms").doc(ROOM_CODE).update({
      memberUids: [ALICE_UID],
      updatedAt: pastDate(200),
    })
  );
  await assertFails(bobDb.collection("rooms").doc(ROOM_CODE).delete());
  await assertSucceeds(aliceDb.collection("rooms").doc(ROOM_CODE).delete());
});

test("Room players: join doc creation, member queries, self flags vs host score updates", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(aliceDb.collection("rooms").doc(ROOM_CODE).set(makeRoomPayload(ALICE_UID)));
  await assertSucceeds(
    aliceDb.collection("rooms").doc(ROOM_CODE).collection("players").doc(ALICE_UID)
      .set(makePlayerPayload(ALICE_UID, ALICE_UID, [ALICE_UID], true))
  );

  await assertSucceeds(
    bobDb.collection("rooms").doc(ROOM_CODE).update({
      memberUids: [ALICE_UID, BOB_UID],
      updatedAt: pastDate(2000),
    })
  );
  await assertSucceeds(
    bobDb.collection("rooms").doc(ROOM_CODE).collection("players").doc(BOB_UID)
      .set(makePlayerPayload(BOB_UID, ALICE_UID, [ALICE_UID, BOB_UID], false))
  );

  // Filtered list query succeeds, unfiltered fails
  await assertSucceeds(
    bobDb.collection("rooms").doc(ROOM_CODE).collection("players")
      .where("memberUids", "array-contains", BOB_UID)
      .get()
  );
  await assertFails(bobDb.collection("rooms").doc(ROOM_CODE).collection("players").get());

  // Player updates own submission/vote flags; cannot tamper score
  await assertSucceeds(
    bobDb.collection("rooms").doc(ROOM_CODE).collection("players").doc(BOB_UID).update({
      hasSubmittedAnswer: true,
      mySubmittedAnswerId: `ans_r1_${BOB_UID}`,
      updatedAt: pastDate(1000),
    })
  );
  await assertFails(
    bobDb.collection("rooms").doc(ROOM_CODE).collection("players").doc(BOB_UID).update({
      score: 9999,
      updatedAt: pastDate(1000),
    })
  );

  // Host updates Bob's score & memberUids
  await assertSucceeds(
    aliceDb.collection("rooms").doc(ROOM_CODE).collection("players").doc(BOB_UID).update({
      score: 150,
      totalVoteTimeMs: 4200,
      memberUids: [ALICE_UID, BOB_UID],
      updatedAt: pastDate(500),
    })
  );
});

test("Secrets isolation: only recipient reads secret, no list, 1-time Glitch reroll", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = pastDate(5000);

  await assertSucceeds(
    aliceDb.collection("rooms").doc(ROOM_CODE).set(
      makeRoomPayload(ALICE_UID, { memberUids: [ALICE_UID] })
    )
  );
  await assertSucceeds(
    bobDb.collection("rooms").doc(ROOM_CODE).update({
      memberUids: [ALICE_UID, BOB_UID],
      updatedAt: pastDate(4000),
    })
  );

  // Host writes Alice (HUMAN) and Bob (GLITCH) secrets
  await assertSucceeds(
    aliceDb.collection("rooms").doc(ROOM_CODE).collection("secrets").doc(ALICE_UID).set({
      userId: ALICE_UID,
      roomId: ROOM_CODE,
      hostId: ALICE_UID,
      memberUids: [ALICE_UID, BOB_UID],
      roundNum: 1,
      role: "HUMAN",
      aiOptions: [],
      rerollUsed: false,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    aliceDb.collection("rooms").doc(ROOM_CODE).collection("secrets").doc(BOB_UID).set({
      userId: BOB_UID,
      roomId: ROOM_CODE,
      hostId: ALICE_UID,
      memberUids: [ALICE_UID, BOB_UID],
      roundNum: 1,
      role: "GLITCH",
      aiOptions: ["AI line 1", "AI line 2", "AI line 3"],
      rerollUsed: false,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Non-host Bob cannot create secrets
  await assertFails(
    bobDb.collection("rooms").doc(ROOM_CODE).collection("secrets").doc(CHARLIE_UID).set({
      userId: CHARLIE_UID,
      roomId: ROOM_CODE,
      hostId: BOB_UID,
      memberUids: [ALICE_UID, BOB_UID],
      roundNum: 1,
      role: "HUMAN",
      aiOptions: [],
      rerollUsed: false,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Each player reads only their own secret; even host Alice cannot read Bob's secret; list is denied
  await assertSucceeds(aliceDb.collection("rooms").doc(ROOM_CODE).collection("secrets").doc(ALICE_UID).get());
  await assertSucceeds(bobDb.collection("rooms").doc(ROOM_CODE).collection("secrets").doc(BOB_UID).get());
  await assertFails(aliceDb.collection("rooms").doc(ROOM_CODE).collection("secrets").doc(BOB_UID).get());
  await assertFails(bobDb.collection("rooms").doc(ROOM_CODE).collection("secrets").doc(ALICE_UID).get());
  await assertFails(aliceDb.collection("rooms").doc(ROOM_CODE).collection("secrets").get());

  // Add Charlie to room so we can test non-host HUMAN secret isolation
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("rooms").doc(ROOM_CODE).update({
      memberUids: [ALICE_UID, BOB_UID, CHARLIE_UID],
    });
  });
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("rooms").doc(ROOM_CODE).collection("secrets").doc(CHARLIE_UID).set({
      userId: CHARLIE_UID,
      roomId: ROOM_CODE,
      hostId: ALICE_UID,
      memberUids: [ALICE_UID, BOB_UID, CHARLIE_UID],
      roundNum: 1,
      role: "HUMAN",
      aiOptions: [],
      rerollUsed: false,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Glitch Bob allowed 1 reroll, denied on 2nd reroll; non-host Human Charlie denied reroll
  await assertSucceeds(
    bobDb.collection("rooms").doc(ROOM_CODE).collection("secrets").doc(BOB_UID).update({
      aiOptions: ["Fresh 1", "Fresh 2", "Fresh 3"],
      rerollUsed: true,
      updatedAt: pastDate(1000),
    })
  );
  await assertFails(
    bobDb.collection("rooms").doc(ROOM_CODE).collection("secrets").doc(BOB_UID).update({
      aiOptions: ["Cheat 1", "Cheat 2", "Cheat 3"],
      rerollUsed: true,
      updatedAt: pastDate(500),
    })
  );
  await assertFails(
    charlieDb.collection("rooms").doc(ROOM_CODE).collection("secrets").doc(CHARLIE_UID).update({
      aiOptions: ["Cheat"],
      rerollUsed: true,
      updatedAt: pastDate(500),
    })
  );
});

test("Drafts, votes & public_answers: host-only read/publish, member queries, reveal updates", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const ts = pastDate(5000);

  await assertSucceeds(aliceDb.collection("rooms").doc(ROOM_CODE).set(makeRoomPayload(ALICE_UID)));
  await assertSucceeds(
    bobDb.collection("rooms").doc(ROOM_CODE).update({
      memberUids: [ALICE_UID, BOB_UID],
      updatedAt: pastDate(4000),
    })
  );

  // Member Bob submits answer draft (<= 140 chars); >140 chars rejected; non-member Charlie rejected
  await assertSucceeds(
    bobDb.collection("rooms").doc(ROOM_CODE).collection("drafts").doc(BOB_UID).set({
      userId: BOB_UID,
      roomId: ROOM_CODE,
      hostId: ALICE_UID,
      roundNum: 1,
      answerText: "Too much cold brew before 8am lab",
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertFails(
    charlieDb.collection("rooms").doc(ROOM_CODE).collection("drafts").doc(CHARLIE_UID).set({
      userId: CHARLIE_UID,
      roomId: ROOM_CODE,
      hostId: ALICE_UID,
      roundNum: 1,
      answerText: "Intruder",
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertFails(
    bobDb.collection("rooms").doc(ROOM_CODE).collection("drafts").doc(BOB_UID).update({
      roundNum: 1,
      answerText: "x".repeat(141),
      updatedAt: pastDate(1000),
    })
  );

  // Host lists drafts via where("hostId", "==", ALICE_UID); Bob cannot list drafts
  await assertSucceeds(
    aliceDb.collection("rooms").doc(ROOM_CODE).collection("drafts")
      .where("hostId", "==", ALICE_UID)
      .get()
  );
  await assertFails(bobDb.collection("rooms").doc(ROOM_CODE).collection("drafts").get());
  await assertFails(
    bobDb.collection("rooms").doc(ROOM_CODE).collection("drafts").where("hostId", "==", ALICE_UID).get()
  );

  // Host publishes anonymized public_answers (no authorId/role yet)
  const ansId = `ans_r1_${BOB_UID}`;
  await assertSucceeds(
    aliceDb.collection("rooms").doc(ROOM_CODE).collection("public_answers").doc(ansId).set({
      roomId: ROOM_CODE,
      hostId: ALICE_UID,
      memberUids: [ALICE_UID, BOB_UID],
      roundNum: 1,
      label: "Response A",
      text: "Too much cold brew before 8am lab",
      revealed: false,
      voteCount: 0,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertFails(
    bobDb.collection("rooms").doc(ROOM_CODE).collection("public_answers").doc("bob_spoof").set({
      roomId: ROOM_CODE,
      hostId: ALICE_UID,
      memberUids: [ALICE_UID, BOB_UID],
      roundNum: 1,
      label: "Response B",
      text: "Spoof",
      revealed: false,
      voteCount: 0,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Member Bob reads public_answers via memberUids query; non-member Charlie denied get/unfiltered/cross-filter
  await assertSucceeds(
    bobDb.collection("rooms").doc(ROOM_CODE).collection("public_answers")
      .where("memberUids", "array-contains", BOB_UID)
      .get()
  );
  await assertFails(
    charlieDb.collection("rooms").doc(ROOM_CODE).collection("public_answers").doc(ansId).get()
  );
  await assertFails(
    charlieDb.collection("rooms").doc(ROOM_CODE).collection("public_answers").get()
  );
  await assertFails(
    charlieDb.collection("rooms").doc(ROOM_CODE).collection("public_answers")
      .where("memberUids", "array-contains", BOB_UID)
      .get()
  );

  // Bob casts vote; host lists votes
  await assertSucceeds(
    bobDb.collection("rooms").doc(ROOM_CODE).collection("votes").doc(BOB_UID).set({
      userId: BOB_UID,
      roomId: ROOM_CODE,
      hostId: ALICE_UID,
      roundNum: 1,
      targetAnswerId: ansId,
      elapsedMs: 3200,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    aliceDb.collection("rooms").doc(ROOM_CODE).collection("votes")
      .where("hostId", "==", ALICE_UID)
      .get()
  );
  await assertFails(
    bobDb.collection("rooms").doc(ROOM_CODE).collection("votes").where("hostId", "==", ALICE_UID).get()
  );

  // Host commits reveal on public_answers; non-host Bob cannot
  await assertFails(
    bobDb.collection("rooms").doc(ROOM_CODE).collection("public_answers").doc(ansId).update({
      revealed: true,
      updatedAt: pastDate(500),
    })
  );
  await assertSucceeds(
    aliceDb.collection("rooms").doc(ROOM_CODE).collection("public_answers").doc(ansId).update({
      revealed: true,
      authorId: BOB_UID,
      authorNickname: "bob_456",
      role: "GLITCH",
      voterNicknames: ["alice_123"],
      voteCount: 1,
      updatedAt: pastDate(500),
    })
  );
  await assertSucceeds(
    aliceDb.collection("rooms").doc(ROOM_CODE).collection("public_answers").doc(ansId).delete()
  );

  // Host persists round leaderboard delta & tags on RoomPlayer
  await assertSucceeds(
    aliceDb.collection("rooms").doc(ROOM_CODE).collection("players").doc(ALICE_UID)
      .set(makePlayerPayload(ALICE_UID, ALICE_UID, [ALICE_UID, BOB_UID], true))
  );
  await assertSucceeds(
    aliceDb.collection("rooms").doc(ROOM_CODE).collection("players").doc(ALICE_UID).update({
      score: 100,
      lastRoundDelta: 100,
      lastRoundTags: ["Caught Glitch +100"],
      updatedAt: pastDate(400),
    })
  );
});

test("Global leaderboard: signed-in read/orderBy, owner-only bounded writes, cross-user & tamper denial", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = pastDate(5000);

  // Unauthenticated access denied
  await assertFails(unauthDb.collection("leaderboard").orderBy("totalPoints", "desc").limit(20).get());
  await assertFails(
    unauthDb.collection("leaderboard").doc(ALICE_UID).set({
      userId: ALICE_UID,
      nickname: "Alice",
      totalPoints: 100,
      lastRoundDelta: 100,
      roundsPlayed: 1,
      glitchesCaught: 1,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Owner Alice creates initial leaderboard entry after Round 1
  await assertSucceeds(
    aliceDb.collection("leaderboard").doc(ALICE_UID).set({
      userId: ALICE_UID,
      nickname: "Alice",
      totalPoints: 150,
      lastRoundDelta: 150,
      roundsPlayed: 1,
      glitchesCaught: 1,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Reject inflated initial score (> 1000) or spoofed userId
  await assertFails(
    bobDb.collection("leaderboard").doc(BOB_UID).set({
      userId: BOB_UID,
      nickname: "Bob",
      totalPoints: 5000,
      lastRoundDelta: 100,
      roundsPlayed: 1,
      glitchesCaught: 0,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertFails(
    bobDb.collection("leaderboard").doc(ALICE_UID).set({
      userId: ALICE_UID,
      nickname: "FakeAlice",
      totalPoints: 100,
      lastRoundDelta: 100,
      roundsPlayed: 1,
      glitchesCaught: 0,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Valid Round 2 incremental update by Alice (+100 pts, +1 round)
  await assertSucceeds(
    aliceDb.collection("leaderboard").doc(ALICE_UID).update({
      nickname: "Alice",
      totalPoints: 250,
      lastRoundDelta: 100,
      roundsPlayed: 2,
      glitchesCaught: 2,
      updatedAt: pastDate(1000),
    })
  );

  // Reject score inflation > +1000 in a single round or tampering createdAt
  await assertFails(
    aliceDb.collection("leaderboard").doc(ALICE_UID).update({
      totalPoints: 2000,
      lastRoundDelta: 100,
      roundsPlayed: 3,
      updatedAt: pastDate(500),
    })
  );
  await assertFails(
    aliceDb.collection("leaderboard").doc(ALICE_UID).update({
      createdAt: pastDate(200),
      updatedAt: pastDate(200),
    })
  );

  // Signed-in Bob can query global leaderboard ordered by totalPoints desc, but cannot modify/delete Alice
  await assertSucceeds(
    bobDb.collection("leaderboard").orderBy("totalPoints", "desc").limit(20).get()
  );
  await assertFails(
    bobDb.collection("leaderboard").doc(ALICE_UID).update({
      totalPoints: 300,
      updatedAt: pastDate(500),
    })
  );
  await assertFails(bobDb.collection("leaderboard").doc(ALICE_UID).delete());
});

test("Room chat messages: member read/write, length & auth validation, host memberUids sync, tamper denial", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const ts = pastDate(5000);

  await assertSucceeds(aliceDb.collection("rooms").doc(ROOM_CODE).set(makeRoomPayload(ALICE_UID)));
  await assertSucceeds(
    bobDb.collection("rooms").doc(ROOM_CODE).update({
      memberUids: [ALICE_UID, BOB_UID],
      updatedAt: pastDate(4000),
    })
  );

  // Unauthenticated & non-member Charlie cannot create or list messages
  await assertFails(
    unauthDb.collection("rooms").doc(ROOM_CODE).collection("messages").doc("m0").set({
      userId: ALICE_UID,
      roomId: ROOM_CODE,
      hostId: ALICE_UID,
      memberUids: [ALICE_UID, BOB_UID],
      senderNickname: "Alice",
      text: "Hello",
      roundNum: 1,
      phase: "DEBATE",
      clientSentAtMs: Date.now(),
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertFails(
    charlieDb.collection("rooms").doc(ROOM_CODE).collection("messages").doc("m1").set({
      userId: CHARLIE_UID,
      roomId: ROOM_CODE,
      hostId: ALICE_UID,
      memberUids: [ALICE_UID, BOB_UID, CHARLIE_UID],
      senderNickname: "Charlie",
      text: "Sneaking in",
      roundNum: 1,
      phase: "DEBATE",
      clientSentAtMs: Date.now(),
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Member Bob sends a valid chat message
  await assertSucceeds(
    bobDb.collection("rooms").doc(ROOM_CODE).collection("messages").doc("msg_bob_1").set({
      userId: BOB_UID,
      roomId: ROOM_CODE,
      hostId: ALICE_UID,
      memberUids: [ALICE_UID, BOB_UID],
      senderNickname: "Bob",
      text: "Option B reads like a textbook AI.",
      roundNum: 1,
      phase: "DEBATE",
      clientSentAtMs: Date.now(),
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Reject blank text, >200 chars, or spoofed userId
  await assertFails(
    bobDb.collection("rooms").doc(ROOM_CODE).collection("messages").doc("msg_blank").set({
      userId: BOB_UID,
      roomId: ROOM_CODE,
      hostId: ALICE_UID,
      memberUids: [ALICE_UID, BOB_UID],
      senderNickname: "Bob",
      text: "",
      roundNum: 1,
      phase: "DEBATE",
      clientSentAtMs: Date.now(),
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertFails(
    bobDb.collection("rooms").doc(ROOM_CODE).collection("messages").doc("msg_long").set({
      userId: BOB_UID,
      roomId: ROOM_CODE,
      hostId: ALICE_UID,
      memberUids: [ALICE_UID, BOB_UID],
      senderNickname: "Bob",
      text: "x".repeat(201),
      roundNum: 1,
      phase: "DEBATE",
      clientSentAtMs: Date.now(),
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertFails(
    bobDb.collection("rooms").doc(ROOM_CODE).collection("messages").doc("msg_spoof").set({
      userId: ALICE_UID,
      roomId: ROOM_CODE,
      hostId: ALICE_UID,
      memberUids: [ALICE_UID, BOB_UID],
      senderNickname: "Alice",
      text: "I am the Glitch!",
      roundNum: 1,
      phase: "DEBATE",
      clientSentAtMs: Date.now(),
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Member Alice reads messages via memberUids filter; unfiltered & non-member cross-filter fail
  await assertSucceeds(
    aliceDb.collection("rooms").doc(ROOM_CODE).collection("messages")
      .where("memberUids", "array-contains", ALICE_UID)
      .get()
  );
  await assertFails(aliceDb.collection("rooms").doc(ROOM_CODE).collection("messages").get());
  await assertFails(
    charlieDb.collection("rooms").doc(ROOM_CODE).collection("messages")
      .where("memberUids", "array-contains", ALICE_UID)
      .get()
  );
  await assertFails(
    charlieDb.collection("rooms").doc(ROOM_CODE).collection("messages").doc("msg_bob_1").get()
  );

  // Host can sync memberUids; neither host nor non-host can tamper message text; sender can delete own
  await assertSucceeds(
    aliceDb.collection("rooms").doc(ROOM_CODE).collection("messages").doc("msg_bob_1").update({
      memberUids: [ALICE_UID, BOB_UID, CHARLIE_UID],
      updatedAt: pastDate(500),
    })
  );
  await assertFails(
    aliceDb.collection("rooms").doc(ROOM_CODE).collection("messages").doc("msg_bob_1").update({
      text: "Tampered text",
      updatedAt: pastDate(300),
    })
  );
  await assertFails(
    charlieDb.collection("rooms").doc(ROOM_CODE).collection("messages").doc("msg_bob_1").delete()
  );
  await assertSucceeds(
    bobDb.collection("rooms").doc(ROOM_CODE).collection("messages").doc("msg_bob_1").delete()
  );
});


