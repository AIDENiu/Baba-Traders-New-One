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

  // Setup user profiles in admin context
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
  await assertFails(unauthedDb.collection("customers").add({ name: "Ravi" }));
});

test("Worker can create job sheet with discount <= 200", async () => {
  const workerDb = testEnv.authenticatedContext(WORKER_UID).firestore();
  await assertSucceeds(
    workerDb.collection("job_sheets").doc("job_1").set({
      id: "job_1",
      customerId: "c1",
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
});

test("Worker CANNOT create job sheet with discount > 200", async () => {
  const workerDb = testEnv.authenticatedContext(WORKER_UID).firestore();
  await assertFails(
    workerDb.collection("job_sheets").doc("job_2").set({
      id: "job_2",
      customerId: "c1",
      machineId: "m1",
      quantity: 1,
      rateUsed: 350,
      discount: 250, // Exceeds floor 200
      deposit: 500,
      status: "active",
      handledByUid: WORKER_UID,
      isDeleted: false,
    })
  );
});

test("Worker CANNOT hard-delete job sheet", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("job_sheets").doc("job_del").set({
      id: "job_del",
      discount: 0,
      isDeleted: false,
    });
  });
  const workerDb = testEnv.authenticatedContext(WORKER_UID).firestore();
  await assertFails(workerDb.collection("job_sheets").doc("job_del").delete());
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

  // Update denied
  await assertFails(
    workerDb.collection("activity_log").doc("log_1").update({
      action: "tampered",
    })
  );

  // Delete denied
  await assertFails(workerDb.collection("activity_log").doc("log_1").delete());
});

test("Worker CANNOT modify machine base pricing", async () => {
  const workerDb = testEnv.authenticatedContext(WORKER_UID).firestore();
  // Attempting to change baseDailyRate
  await assertFails(
    workerDb.collection("machines").doc("m1").update({
      baseDailyRate: 200,
    })
  );

  // Updating stock is allowed
  await assertSucceeds(
    workerDb.collection("machines").doc("m1").update({
      availableQty: 4,
    })
  );
});
