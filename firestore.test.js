const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = "demo-no-project";
const OWNER_UID = "owner_naveen";
const OWNER_EMAIL = "rupak2472@gmail.com";
const WORKER_UID = "worker_suresh";
const RANDOM_UID = "unauthorized_user";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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

beforeEach(async () => {
  await testEnv.clearFirestore();

  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await db.collection("users").doc(OWNER_UID).set({
      uid: OWNER_UID,
      name: "Naveen",
      role: "owner",
      active: true,
    });
    await db.collection("users").doc(WORKER_UID).set({
      uid: WORKER_UID,
      name: "Suresh",
      role: "worker",
      active: true,
      maxDiscountAmount: 200,
    });
    await db.collection("machines").doc("m1").set({
      id: "m1",
      name: "Hammer Drill",
      categoryId: "c1",
      baseDailyRate: 350,
      dailyRate: 350,
      availableQty: 5,
      totalQty: 5,
    });
    await db.collection("payments").doc("pay_1").set({
      id: "pay_1",
      jobSheetId: "job_1",
      amount: 500,
      method: "cash",
      type: "advance",
      handledBy: WORKER_UID,
    });
    await db.collection("expenses").doc("exp_1").set({
      id: "exp_1",
      date: "07/11/2026",
      expenseType: "Diesel",
      amount: 300,
    });
    await db.collection("daily_cash").doc("2026-11-07").set({
      date: "2026-11-07",
      openingCash: 1000,
      closingCash: 2500,
    });
    await db.collection("reports").doc("monthly_2026_11").set({
      totalSales: 15000,
    });
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

test("Unauthenticated access is denied", async () => {
  const unauthedDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthedDb.collection("job_sheets").get());
  await assertFails(unauthedDb.collection("machines").get());
  await assertFails(unauthedDb.collection("customers").get());
  await assertFails(unauthedDb.collection("customers").add({ name: "Ravi" }));
});

test("RULE DENIAL 1: Staff setting their own role to owner or setting appSessionLinked is DENIED", async () => {
  const workerDb = testEnv.authenticatedContext(WORKER_UID).firestore();
  await assertFails(
    workerDb.collection("users").doc(WORKER_UID).update({
      role: "owner",
    })
  );
  await assertFails(
    workerDb.collection("users").doc(WORKER_UID).update({
      appSessionLinked: true,
    })
  );
  await assertFails(
    workerDb.collection("users").doc(WORKER_UID).update({
      maxDiscountAmount: 1000,
    })
  );
});

test("RULE DENIAL 2: Staff (and even Owner) changing a payment amount or deleting a payment is DENIED", async () => {
  const workerDb = testEnv.authenticatedContext(WORKER_UID).firestore();
  const ownerDb = testEnv.authenticatedContext(OWNER_UID, { email: OWNER_EMAIL }).firestore();

  // Staff changing payment amount -> DENIED
  await assertFails(
    workerDb.collection("payments").doc("pay_1").update({
      amount: 50,
    })
  );
  await assertFails(workerDb.collection("payments").doc("pay_1").delete());

  // Even Owner changing payment amount -> DENIED (append-only ledger)
  await assertFails(
    ownerDb.collection("payments").doc("pay_1").update({
      amount: 50,
    })
  );
});

test("RULE DENIAL 3: Staff reading report data (reports, expenses, daily_cash, activity_log) is DENIED, while Owner succeeds", async () => {
  const workerDb = testEnv.authenticatedContext(WORKER_UID).firestore();
  const ownerDb = testEnv.authenticatedContext(OWNER_UID, { email: OWNER_EMAIL }).firestore();

  // Staff reading reports, expenses, daily_cash, activity_log -> DENIED
  await assertFails(workerDb.collection("reports").doc("monthly_2026_11").get());
  await assertFails(workerDb.collection("reports").get());
  await assertFails(workerDb.collection("expenses").doc("exp_1").get());
  await assertFails(workerDb.collection("expenses").get());
  await assertFails(workerDb.collection("daily_cash").doc("2026-11-07").get());
  await assertFails(workerDb.collection("daily_cash").get());
  await assertFails(workerDb.collection("activity_log").get());

  // Owner reading reports, expenses, daily_cash, activity_log -> SUCCEEDS
  await assertSucceeds(ownerDb.collection("reports").doc("monthly_2026_11").get());
  await assertSucceeds(ownerDb.collection("expenses").doc("exp_1").get());
  await assertSucceeds(ownerDb.collection("daily_cash").doc("2026-11-07").get());
  await assertSucceeds(ownerDb.collection("activity_log").get());
});

test("Pre-authorized staff email created by Owner can safely link their UID as worker (case-insensitive) without appSessionLinked", async () => {
  const ownerDb = testEnv.authenticatedContext(OWNER_UID, { email: "Rupak2472@Gmail.com" }).firestore();
  await assertSucceeds(
    ownerDb.collection("users").doc("ramesh@gmail.com").set({
      uid: "user_worker_ramesh",
      name: "Ramesh",
      phoneOrUsername: "ramesh@gmail.com",
      role: "worker",
      active: true,
      maxDiscountAmount: 150,
    })
  );

  const rameshDb = testEnv.authenticatedContext("uid_ramesh_google", { email: "Ramesh@Gmail.com" }).firestore();
  await assertFails(
    rameshDb.collection("users").doc("uid_ramesh_google").set({
      uid: "uid_ramesh_google",
      name: "Ramesh",
      role: "owner",
      active: true,
    })
  );
  await assertFails(
    rameshDb.collection("users").doc("uid_ramesh_google").set({
      uid: "uid_ramesh_google",
      name: "Ramesh",
      role: "worker",
      active: true,
      appSessionLinked: true,
    })
  );
  await assertSucceeds(rameshDb.collection("users").doc("ramesh@gmail.com").get());
  await assertSucceeds(
    rameshDb.collection("users").doc("uid_ramesh_google").set({
      uid: "uid_ramesh_google",
      name: "Ramesh",
      role: "worker",
      active: true,
    })
  );
  await assertSucceeds(rameshDb.collection("machines").get());
});

test("Worker can create/update job sheet with discount <= 200, but discount > 200 on create or update is DENIED", async () => {
  const workerDb = testEnv.authenticatedContext(WORKER_UID).firestore();
  await assertSucceeds(
    workerDb.collection("job_sheets").doc("job_1").set({
      id: "job_1",
      customerId: "c1",
      customerName: "Ravi",
      customerPhone: "9876543210",
      machineId: "m1",
      quantity: 1,
      rateUsed: 350,
      discount: 150,
      deposit: 500,
      status: "active",
      handledByUid: WORKER_UID,
      isDeleted: false,
    })
  );

  // Worker updating discount to 250 on return -> DENIED
  await assertFails(
    workerDb.collection("job_sheets").doc("job_1").update({
      discount: 250,
      status: "returned",
    })
  );

  // Worker updating discount to 200 on return -> SUCCEEDS
  await assertSucceeds(
    workerDb.collection("job_sheets").doc("job_1").update({
      discount: 200,
      status: "returned",
    })
  );

  // Worker creating job sheet with discount > 200 -> DENIED
  await assertFails(
    workerDb.collection("job_sheets").doc("job_2").set({
      id: "job_2",
      customerId: "c1",
      machineId: "m1",
      quantity: 1,
      rateUsed: 350,
      discount: 250,
      deposit: 500,
      status: "active",
      handledByUid: WORKER_UID,
      isDeleted: false,
    })
  );
});

test("Neither Worker nor Owner can delete a job sheet or edit core billing fields", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("job_sheets").doc("job_immut").set({
      id: "job_immut",
      customerId: "c1",
      customerName: "Ravi",
      customerPhone: "9876543210",
      machineId: "m1",
      rateUsed: 350,
      discount: 0,
      isDeleted: false,
    });
  });
  const workerDb = testEnv.authenticatedContext(WORKER_UID).firestore();
  const ownerDb = testEnv.authenticatedContext(OWNER_UID, { email: OWNER_EMAIL }).firestore();

  await assertFails(workerDb.collection("job_sheets").doc("job_immut").delete());
  await assertFails(ownerDb.collection("job_sheets").doc("job_immut").delete());
  await assertFails(ownerDb.collection("job_sheets").doc("job_immut").update({ isDeleted: true }));
  await assertFails(ownerDb.collection("job_sheets").doc("job_immut").update({ rateUsed: 100 }));
  await assertFails(ownerDb.collection("job_sheets").doc("job_immut").update({ customerName: "Tampered" }));
});

test("Activity log is write-once (create allowed, update and delete denied)", async () => {
  const workerDb = testEnv.authenticatedContext(WORKER_UID).firestore();
  await assertSucceeds(
    workerDb.collection("activity_log").doc("log_1").set({
      id: "log_1",
      jobSheetId: "job_1",
      action: "created",
      handledByUid: WORKER_UID,
    })
  );

  await assertFails(
    workerDb.collection("activity_log").doc("log_1").update({
      action: "tampered",
    })
  );
  await assertFails(workerDb.collection("activity_log").doc("log_1").delete());
});
