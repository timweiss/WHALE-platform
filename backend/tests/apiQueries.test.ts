import request from 'supertest';
import { randomUUID } from 'node:crypto';
import { usePool } from '../src/config/database';
import { makeExpressApp } from '../src';
import { Config } from '../src/config';
import jwt from 'jsonwebtoken';
import { initializeRepositories } from '../src/data/repositoryHelper';
import { Observability } from '../src/o11y';

// Mock observability to avoid actual logging during tests
const mockObservability: Observability = {
  logger: {
    debug: () => {},
    info: () => {},
    warn: () => {},
    error: () => {},
  },
  tracer: {
    startSpan: () => ({
      end: () => {},
      setStatus: () => {},
      setAttribute: () => {},
      setAttributes: () => {},
      addEvent: () => {},
      recordException: () => {},
      updateName: () => {},
      isRecording: () => false,
      spanContext: () => ({
        traceId: '',
        spanId: '',
        traceFlags: 0,
      }),
    }),
    startActiveSpan: (name: string, fn: unknown) => {
      if (typeof fn === 'function') {
        return fn({
          end: () => {},
          setStatus: () => {},
          setAttribute: () => {},
          setAttributes: () => {},
          addEvent: () => {},
          recordException: () => {},
          updateName: () => {},
          isRecording: () => false,
          spanContext: () => ({
            traceId: '',
            spanId: '',
            traceFlags: 0,
          }),
        });
      }
    },
  } as never,
};

const pool = usePool(mockObservability);
const app = makeExpressApp(
  pool,
  initializeRepositories(pool, mockObservability),
  mockObservability,
);

function generateAdminToken() {
  return jwt.sign({ role: 'admin' }, Config.auth.jwtSecret, {
    expiresIn: '30d',
  });
}

beforeEach(async () => {
  await pool.query('BEGIN');
});

afterEach(async () => {
  await pool.query('ROLLBACK');
});
afterAll(async () => {
  await pool.end();
});

// study tests

const dummyStudy = {
  enrolmentKey: 'key',
  name: 'name',
  maxEnrolments: -1,
  durationDays: 10,
  allocationStrategy: 'Sequential',
  description: 'description',
  contactEmail: 'email',
};

async function initializeBetweenGroupsStudy() {
  const token = generateAdminToken();

  const study = await request(app)
    .post('/v1/study')
    .set({ Authorization: 'Bearer ' + token })
    .send(dummyStudy);

  const group1 = await request(app)
    .post(`/v1/study/${study.body.id}/group`)
    .set({ Authorization: 'Bearer ' + token })
    .send({
      internalName: 'group1',
      allocationOrder: 0,
      phases: [
        {
          internalName: 'baseline',
          fromDay: 0,
          durationDays: 3,
          interactionWidgetStrategy: 'Disabled',
        },
        {
          internalName: 'treatment',
          fromDay: 3,
          durationDays: 4,
          interactionWidgetStrategy: 'Bucketed',
        },
      ],
    });

  const group2 = await request(app)
    .post(`/v1/study/${study.body.id}/group`)
    .set({ Authorization: 'Bearer ' + token })
    .send({
      internalName: 'group2',
      allocationOrder: 1,
      phases: [
        {
          internalName: 'treatment',
          fromDay: 0,
          durationDays: 4,
          interactionWidgetStrategy: 'Bucketed',
        },
        {
          internalName: 'baseline',
          fromDay: 4,
          durationDays: 3,
          interactionWidgetStrategy: 'Disabled',
        },
      ],
    });
}

test('should fetch studies', async () => {
  const res = await request(app).get('/v1/study');
  expect(res.statusCode).toBe(200);
  expect(res.body).toBeInstanceOf(Array);
});

test('should create a study', async () => {
  const token = generateAdminToken();
  const res = await request(app)
    .post('/v1/study')
    .set({ Authorization: 'Bearer ' + token })
    .send(dummyStudy);
  expect(res.statusCode).toBe(200);
  expect(res.body).toMatchObject(dummyStudy);
});

test('should fetch a study by id', async () => {
  const token = generateAdminToken();
  const study = await request(app)
    .post('/v1/study')
    .set({ Authorization: 'Bearer ' + token })
    .send(dummyStudy);

  const res = await request(app).get(`/v1/study/${study.body.id}`);
  expect(res.statusCode).toBe(200);
  expect(res.body).toMatchObject({
    enrolmentKey: 'key',
    name: 'name',
    id: study.body.id,
  });
});

test('should fail creating a study without required fields', async () => {
  const token = generateAdminToken();
  const res = await request(app)
    .post('/v1/study')
    .set({ Authorization: 'Bearer ' + token })
    .send({});
  expect(res.statusCode).toBe(400);
  expect(res.body).toEqual({
    error: 'Missing required fields (enrolmentKey or name)',
  });
});

test('should fail creating a study with duplicate enrolment key', async () => {
  const token = generateAdminToken();
  await request(app)
    .post('/v1/study')
    .set({ Authorization: 'Bearer ' + token })
    .send(dummyStudy);

  const res = await request(app)
    .post('/v1/study')
    .set({ Authorization: 'Bearer ' + token })
    .send(dummyStudy);

  expect(res.statusCode).toBe(400);
  expect(res.body).toEqual({
    error: 'Study with enrolment key already exists',
  });
});

// enrolment tests

test('study experimental group should have phases', async () => {
  const token = generateAdminToken();
  const study = await request(app)
    .post('/v1/study')
    .set({ Authorization: 'Bearer ' + token })
    .send(dummyStudy);

  const group = await request(app)
    .post(`/v1/study/${study.body.id}/group`)
    .set({ Authorization: 'Bearer ' + token })
    .send({
      internalName: 'group1',
      allocationOrder: 0,
      phases: [
        {
          internalName: 'baseline',
          fromDay: 0,
          durationDays: 3,
          interactionWidgetStrategy: 'Disabled',
        },
        {
          internalName: 'treatment',
          fromDay: 3,
          durationDays: 4,
          interactionWidgetStrategy: 'Bucketed',
        },
      ],
    });

  expect(group.statusCode).toBe(200);
  expect(group.body.phases).toBeInstanceOf(Array);
  expect(group.body.phases).toHaveLength(2);
});

test('should enrol in study', async () => {
  await initializeBetweenGroupsStudy();

  const res = await request(app)
    .post('/v1/enrolment')
    .send({ enrolmentKey: 'key', source: null });

  expect(res.statusCode).toBe(200);
  expect(res.body).toHaveProperty('participantId');
  expect(res.body).toHaveProperty('token');
  expect(res.body).toHaveProperty('phases');
});

// enrolment failures

test.each(Array(30).fill(null))(
  'should enrol sequentially in study',
  async () => {
    await initializeBetweenGroupsStudy();

    // should start with baseline phase
    const participant1 = await request(app)
      .post('/v1/enrolment')
      .send({ enrolmentKey: 'key', source: null });

    // should start with treatment phase
    const participant2 = await request(app)
      .post('/v1/enrolment')
      .send({ enrolmentKey: 'key', source: null });

    expect(participant1.statusCode).toBe(200);
    expect(participant2.statusCode).toBe(200);
    expect(participant1.body.phases[0].interactionWidgetStrategy).toBe(
      'Disabled',
    );
    expect(participant2.body.phases[0].interactionWidgetStrategy).toBe(
      'Bucketed',
    );
  },
);

test('should fail because of missing experimental groups', async () => {
  const token = generateAdminToken();

  const study = await request(app)
    .post('/v1/study')
    .set({ Authorization: 'Bearer ' + token })
    .send(dummyStudy);

  const res = await request(app)
    .post('/v1/enrolment')
    .send({ enrolmentKey: 'key', source: null });

  expect(res.statusCode).toBe(400);
  expect(res.body.code).toBe('invalid_study_configuration');
  expect(
    res.body.error.startsWith(
      'Invalid Study Configuration: Study has no experimental groups',
    ),
  ).toBe(true);
});

// sensor reading tests

function makeReading(overrides: Record<string, unknown> = {}) {
  return {
    sensorType: 'type',
    data: 'data',
    timestamp: Date.now(),
    localId: randomUUID(),
    ...overrides,
  };
}

async function enrolParticipant(enrolmentKey = 'key') {
  const enrol = await request(app)
    .post('/v1/enrolment')
    .send({ enrolmentKey, source: null });
  expect(enrol.statusCode).toBe(200);
  return enrol.body as { token: string; studyId: number };
}

test.each(['/v1/reading', '/v1/reading/1/file'])(
  'should not expose removed endpoint %s',
  async (path) => {
    await initializeBetweenGroupsStudy();
    const { token } = await enrolParticipant();

    const res = await request(app)
      .post(path)
      .set({ Authorization: 'Bearer ' + token })
      .send(makeReading());

    expect(res.statusCode).toBe(404);
  },
);

test('should create a batch of sensor readings', async () => {
  await initializeBetweenGroupsStudy();
  const { token } = await enrolParticipant();

  // no queue is injected in tests, so readings are written synchronously
  const res = await request(app)
    .post('/v1/reading/batch')
    .set({ Authorization: 'Bearer ' + token })
    .send([makeReading(), makeReading()]);

  expect(res.statusCode).toBe(200);
  const rows = await pool.query(
    'SELECT sensor_type, data FROM sensor_readings',
  );
  expect(rows.rows).toHaveLength(2);
  expect(rows.rows[0]).toMatchObject({ sensor_type: 'type', data: 'data' });
});

test('should fetch questionnaires in a study', async () => {
  const token = generateAdminToken();
  const study = await request(app)
    .post('/v1/study')
    .set({ Authorization: 'Bearer ' + token })
    .send(dummyStudy);

  const questionnaire = await request(app)
    .post(`/v1/study/${study.body.id}/questionnaire`)
    .set({ Authorization: 'Bearer ' + token })
    .send({ name: 'Questionnaire', enabled: true });

  const res = await request(app).get(
    `/v1/study/${study.body.id}/questionnaire`,
  );

  expect(res.statusCode).toBe(200);
  expect(res.body).toBeInstanceOf(Array);
  expect(res.body).toHaveLength(1);
  expect(res.body[0]).toMatchObject({ name: 'Questionnaire' });
});

test('should create a questionnaire in a study', async () => {
  const token = generateAdminToken();
  const study = await request(app)
    .post('/v1/study')
    .set({ Authorization: 'Bearer ' + token })
    .send(dummyStudy);

  const res = await request(app)
    .post(`/v1/study/${study.body.id}/questionnaire`)
    .set({ Authorization: 'Bearer ' + token })
    .send({ name: 'Questionnaire', enabled: true });

  expect(res.statusCode).toBe(200);
  expect(res.body).toMatchObject({ name: 'Questionnaire' });
});

function makeAnswer() {
  const now = Date.now();
  return {
    pendingQuestionnaireId: randomUUID(),
    status: 'completed',
    lastOpenedPage: 1,
    createdTimestamp: now,
    lastUpdatedTimestamp: now,
    finishedTimestamp: now,
    notificationTrigger: null,
    answers: [
      { elementId: 1, elementName: 'answer1', value: 'answer1' },
      { elementId: 2, elementName: 'answer2', value: 'answer2' },
    ],
  };
}

async function createQuestionnaire(studyId: number) {
  const questionnaire = await request(app)
    .post(`/v1/study/${studyId}/questionnaire`)
    .set({ Authorization: 'Bearer ' + generateAdminToken() })
    .send({ name: 'Questionnaire', enabled: true });
  expect(questionnaire.statusCode).toBe(200);
  return questionnaire.body as { id: number };
}

test('should create answers for questionnaire', async () => {
  await initializeBetweenGroupsStudy();
  const { token, studyId } = await enrolParticipant();
  const questionnaire = await createQuestionnaire(studyId);

  const res = await request(app)
    .post(`/v1/study/${studyId}/questionnaire/${questionnaire.id}/answer`)
    .set({ Authorization: 'Bearer ' + token })
    .send(makeAnswer());

  expect(res.statusCode).toBe(200);
  expect(res.body).toMatchObject({ status: 'created' });
});

test('should add elements to a questionnaire', async () => {
  const token = generateAdminToken();
  const study = await request(app)
    .post('/v1/study')
    .set({ Authorization: 'Bearer ' + token })
    .send(dummyStudy);

  const questionnaire = await request(app)
    .post(`/v1/study/${study.body.id}/questionnaire`)
    .set({ Authorization: 'Bearer ' + token })
    .send({ name: 'Questionnaire', enabled: true });

  const element = await request(app)
    .post(
      `/v1/study/${study.body.id}/questionnaire/${questionnaire.body.id}/element`,
    )
    .set({ Authorization: 'Bearer ' + token })
    .send({
      type: 'text_view',
      configuration: { content: 'Please enter your name' },
      step: 1,
      displayGroup: 0,
      position: 1,
      name: 'text',
    });

  const res = await request(app).get(
    `/v1/study/${study.body.id}/questionnaire/${questionnaire.body.id}`,
  );

  expect(res.statusCode).toBe(200);
  expect(res.body.questionnaire).toMatchObject({ name: 'Questionnaire' });
  expect(res.body.elements).toBeInstanceOf(Array);
  expect(res.body.elements).toHaveLength(1);
  expect(res.body.elements[0]).toMatchObject({
    name: 'text',
    type: 'text_view',
    configuration: { content: 'Please enter your name' },
  });
});

test('should add a trigger to a questionnaire', async () => {
  const token = generateAdminToken();
  const study = await request(app)
    .post('/v1/study')
    .set({ Authorization: 'Bearer ' + token })
    .send(dummyStudy);

  const questionnaire = await request(app)
    .post(`/v1/study/${study.body.id}/questionnaire`)
    .set({ Authorization: 'Bearer ' + token })
    .send({ name: 'Questionnaire', enabled: true });

  const trigger = await request(app)
    .post(
      `/v1/study/${study.body.id}/questionnaire/${questionnaire.body.id}/trigger`,
    )
    .set({ Authorization: 'Bearer ' + token })
    .send({
      type: 'event',
      configuration: { eventName: 'end_logged_interaction' },
      validDuration: 0,
      enabled: true,
    });

  const res = await request(app).get(
    `/v1/study/${study.body.id}/questionnaire/${questionnaire.body.id}`,
  );

  expect(res.statusCode).toBe(200);
  expect(res.body.questionnaire).toMatchObject({ name: 'Questionnaire' });
  expect(res.body.triggers).toBeInstanceOf(Array);
  expect(res.body.triggers).toHaveLength(1);
  expect(res.body.triggers[0]).toMatchObject({
    type: 'event',
    configuration: { eventName: 'end_logged_interaction' },
  });
});

test('should update a questionnaire element', async () => {
  const token = generateAdminToken();
  const study = await request(app)
    .post('/v1/study')
    .set({ Authorization: 'Bearer ' + token })
    .send(dummyStudy);

  const questionnaire = await request(app)
    .post(`/v1/study/${study.body.id}/questionnaire`)
    .set({ Authorization: 'Bearer ' + token })
    .send({ name: 'Questionnaire', enabled: true });

  const element = await request(app)
    .post(
      `/v1/study/${study.body.id}/questionnaire/${questionnaire.body.id}/element`,
    )
    .set({ Authorization: 'Bearer ' + token })
    .send({
      type: 'text_view',
      configuration: { content: 'Please enter your name' },
      step: 1,
      displayGroup: 0,
      position: 1,
      name: 'text',
    });

  const res = await request(app)
    .put(
      `/v1/study/${study.body.id}/questionnaire/${questionnaire.body.id}/element/${element.body.id}`,
    )
    .set({ Authorization: 'Bearer ' + token })
    .send({
      type: 'text_view',
      configuration: { content: 'Please enter your age' },
      step: 1,
      displayGroup: 0,
      position: 1,
      name: 'text',
    });

  expect(res.statusCode).toBe(200);
  expect(res.body).toMatchObject({
    type: 'text_view',
    configuration: { content: 'Please enter your age' },
  });
});

test('should delete a questionnaire element', async () => {
  const token = generateAdminToken();
  const study = await request(app)
    .post('/v1/study')
    .set({ Authorization: 'Bearer ' + token })
    .send(dummyStudy);

  const questionnaire = await request(app)
    .post(`/v1/study/${study.body.id}/questionnaire`)
    .set({ Authorization: 'Bearer ' + token })
    .send({ name: 'Questionnaire', enabled: true });

  const element = await request(app)
    .post(
      `/v1/study/${study.body.id}/questionnaire/${questionnaire.body.id}/element`,
    )
    .set({ Authorization: 'Bearer ' + token })
    .send({
      type: 'text_view',
      configuration: { content: 'Please enter your name' },
      step: 1,
      displayGroup: 0,
      position: 1,
      name: 'text',
    });

  const res = await request(app)
    .delete(
      `/v1/study/${study.body.id}/questionnaire/${questionnaire.body.id}/element/${element.body.id}`,
    )
    .set({ Authorization: 'Bearer ' + token });

  expect(res.statusCode).toBe(204);
});

test('should delete a questionnaire trigger', async () => {
  const token = generateAdminToken();
  const study = await request(app)
    .post('/v1/study')
    .set({ Authorization: 'Bearer ' + token })
    .send(dummyStudy);

  const questionnaire = await request(app)
    .post(`/v1/study/${study.body.id}/questionnaire`)
    .set({ Authorization: 'Bearer ' + token })
    .send({ name: 'Questionnaire', enabled: true });

  const trigger = await request(app)
    .post(
      `/v1/study/${study.body.id}/questionnaire/${questionnaire.body.id}/trigger`,
    )
    .set({ Authorization: 'Bearer ' + token })
    .send({
      type: 'event',
      configuration: { eventName: 'end_logged_interaction' },
      validDuration: 0,
      enabled: true,
    });

  const res = await request(app)
    .delete(
      `/v1/study/${study.body.id}/questionnaire/${questionnaire.body.id}/trigger/${trigger.body.id}`,
    )
    .set({ Authorization: 'Bearer ' + token });

  expect(res.statusCode).toBe(204);
});

// completion tests

async function createStudyWithGroup(
  enrolmentKey: string,
  completionTracking: Record<string, unknown> | null,
) {
  const token = generateAdminToken();

  const study = await request(app)
    .post('/v1/study')
    .set({ Authorization: 'Bearer ' + token })
    .send({ ...dummyStudy, enrolmentKey });
  expect(study.statusCode).toBe(200);

  const group = await request(app)
    .post(`/v1/study/${study.body.id}/group`)
    .set({ Authorization: 'Bearer ' + token })
    .send({
      internalName: 'testGroup',
      allocationOrder: 0,
      phases: [
        {
          internalName: 'baseline',
          fromDay: 0,
          durationDays: 7,
          interactionWidgetStrategy: 'Disabled',
        },
      ],
    });
  expect(group.statusCode).toBe(200);

  if (completionTracking) {
    const updated = await request(app)
      .put(`/v1/study/${study.body.id}`)
      .set({ Authorization: 'Bearer ' + token })
      .send({ ...study.body, completionTracking });
    expect(updated.statusCode).toBe(200);
  }

  return study.body as { id: number };
}

async function uploadReadings(token: string, timestamps: number[]) {
  const res = await request(app)
    .post('/v1/reading/batch')
    .set({ Authorization: 'Bearer ' + token })
    .send(timestamps.map((timestamp) => makeReading({ timestamp })));
  expect(res.statusCode).toBe(200);
}

async function getCompletion(token: string) {
  return request(app)
    .get('/v1/completion')
    .set({ Authorization: 'Bearer ' + token });
}

const DAY_MS = 24 * 60 * 60 * 1000;

test('should test completion endpoint with sensor data tracking', async () => {
  await createStudyWithGroup('completion-test-key', {
    none: [{ type: 'PassiveSensingParticipationDays', value: 0 }],
    oneDay: [{ type: 'PassiveSensingParticipationDays', value: 1 }],
  });
  const { token } = await enrolParticipant('completion-test-key');

  const before = await getCompletion(token);
  expect(before.statusCode).toBe(200);
  expect(before.body).toEqual({ none: true, oneDay: false });

  await uploadReadings(token, [Date.now()]);

  const after = await getCompletion(token);
  expect(after.statusCode).toBe(200);
  expect(after.body).toEqual({ none: true, oneDay: true });
});

test('should test completion endpoint with multiple sensor readings on same day', async () => {
  await createStudyWithGroup('multi-day-test-key', {
    twoDaysOfSensorData: [
      { type: 'PassiveSensingParticipationDays', value: 2 },
    ],
  });
  const { token } = await enrolParticipant('multi-day-test-key');

  // several readings on the same day count as one day
  const now = Date.now();
  await uploadReadings(token, [now, now]);

  const afterOneDay = await getCompletion(token);
  expect(afterOneDay.statusCode).toBe(200);
  expect(afterOneDay.body.twoDaysOfSensorData).toBe(false);

  await uploadReadings(token, [now - DAY_MS]);

  const afterTwoDays = await getCompletion(token);
  expect(afterTwoDays.statusCode).toBe(200);
  expect(afterTwoDays.body.twoDaysOfSensorData).toBe(true);
});

test('should fail completion endpoint when completion tracking is not enabled', async () => {
  await createStudyWithGroup('no-completion-key', null);
  const { token } = await enrolParticipant('no-completion-key');

  const res = await getCompletion(token);

  expect(res.statusCode).toBe(400);
  expect(res.body.error).toBe(
    'Completion tracking is not enabled for this study',
  );
});
