import request from 'supertest';
import { randomUUID } from 'node:crypto';
import { usePool } from '../src/config/database';
import { makeExpressApp } from '../src';
import { Config } from '../src/config';
import jwt from 'jsonwebtoken';
import { initializeRepositories } from '../src/data/repositoryHelper';
import { Observability } from '../src/o11y';
import { DatabaseError } from '../src/config/errors';
import { tokenLifetimeDays } from '../src/controllers/enrolment';
import { Queue } from 'bullmq';
import { SensorReadingJobData } from '../src/queues/sensorReadingQueue';

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

test('should not list studies', async () => {
  const res = await request(app).get('/v1/study');
  expect(res.statusCode).toBe(404);
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
  // the enrolment key is not revealed through the (enumerable) id
  expect(res.body).toMatchObject({
    enrolmentKey: '',
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
  return enrol.body as {
    token: string;
    studyId: number;
    phases: { interactionWidgetStrategy: string }[];
  };
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

// input validation and error handling

test.each([
  '/v1/study/abc/questionnaire',
  '/v1/study/1/questionnaire/99999999999',
  '/v1/study/1/questionnaire/abc',
  '/v1/study/99999999999',
])('should reject invalid ids in %s', async (path) => {
  const res = await request(app).get(path);

  expect(res.statusCode).toBe(400);
});

test('should fetch a study by enrolment key', async () => {
  await initializeBetweenGroupsStudy();

  const res = await request(app).get('/v1/study/key');

  expect(res.statusCode).toBe(200);
  expect(res.body).toMatchObject({ name: 'name' });
});

test('should reject malformed JSON with a JSON error', async () => {
  const res = await request(app)
    .post('/v1/enrolment')
    .set('Content-Type', 'application/json')
    .send('{"enrolmentKey": ');

  expect(res.statusCode).toBe(400);
  expect(res.body).toHaveProperty('error');
});

test('should answer internal errors with 500 without leaking details', async () => {
  const repositories = initializeRepositories(pool, mockObservability);
  const failingStudyRepository = Object.create(repositories.study);
  failingStudyRepository.getStudyById = async () => {
    throw new DatabaseError('secret connection detail');
  };
  const failingApp = makeExpressApp(
    pool,
    { ...repositories, study: failingStudyRepository },
    mockObservability,
  );

  const res = await request(failingApp).get('/v1/study/1');

  expect(res.statusCode).toBe(500);
  expect(res.body).toEqual({ error: 'Internal server error' });
});

// token lifetime

const DAY_SECONDS = 24 * 60 * 60;

test('should issue participant tokens valid for twice the study duration', async () => {
  await initializeBetweenGroupsStudy();
  const { token } = await enrolParticipant();

  const decoded = jwt.decode(token) as jwt.JwtPayload;

  expect(decoded.iat).toBeLessThanOrEqual(Math.ceil(Date.now() / 1000));
  expect(decoded.exp! - decoded.iat!).toBe(
    dummyStudy.durationDays * 2 * DAY_SECONDS,
  );
});

test('should fall back to 60 days for studies without a duration', () => {
  expect(tokenLifetimeDays({ durationDays: 0 })).toBe(60);
  expect(tokenLifetimeDays({ durationDays: 14 })).toBe(28);
});

test('should reject legacy tokens with a millisecond iat', async () => {
  await initializeBetweenGroupsStudy();
  await enrolParticipant();

  // how tokens were issued before wave 2
  const legacyToken = jwt.sign(
    { role: 'participant', enrolmentId: 1, iat: Date.now() },
    Config.auth.jwtSecret,
    { expiresIn: '30d' },
  );

  const res = await request(app)
    .get('/v2/enrolment')
    .set({ Authorization: 'Bearer ' + legacyToken });

  expect(res.statusCode).toBe(401);
});

test('should reject tokens signed with another algorithm', async () => {
  const token = jwt.sign({ role: 'admin' }, Config.auth.jwtSecret, {
    algorithm: 'HS512',
    expiresIn: '1d',
  });

  const res = await request(app)
    .post('/v1/study')
    .set({ Authorization: 'Bearer ' + token })
    .send(dummyStudy);

  expect(res.statusCode).toBe(401);
});

// rate limiting

async function withRateLimits(
  limits: Partial<typeof Config.rateLimit>,
  fn: () => Promise<void>,
) {
  const original = { ...Config.rateLimit };
  Object.assign(Config.rateLimit, limits);
  try {
    await fn();
  } finally {
    Object.assign(Config.rateLimit, original);
  }
}

test('should rate limit enrolments across API versions', async () => {
  await withRateLimits({ enrolmentsPerHour: 2 }, async () => {
    const limitedApp = makeExpressApp(
      pool,
      initializeRepositories(pool, mockObservability),
      mockObservability,
    );
    await initializeBetweenGroupsStudy();

    const enrol = (version: string) =>
      request(limitedApp)
        .post(`/${version}/enrolment`)
        .send({ enrolmentKey: 'key', source: null });

    expect((await enrol('v1')).statusCode).toBe(200);
    expect((await enrol('v2')).statusCode).toBe(200);

    const limited = await enrol('v1');
    expect(limited.statusCode).toBe(429);
    expect(limited.body).toEqual({
      error: 'Too many requests',
      code: 'rate_limited',
    });
  });
});

test('should rate limit study lookups by key but not by id', async () => {
  await withRateLimits({ studyKeyLookupsPerHour: 1 }, async () => {
    const limitedApp = makeExpressApp(
      pool,
      initializeRepositories(pool, mockObservability),
      mockObservability,
    );

    expect((await request(limitedApp).get('/v1/study/key')).statusCode).toBe(
      404,
    );
    expect((await request(limitedApp).get('/v1/study/other')).statusCode).toBe(
      429,
    );
    expect((await request(limitedApp).get('/v1/study/1')).statusCode).toBe(404);
    expect((await request(limitedApp).get('/v1/study/2')).statusCode).toBe(404);
  });
});

test('should rate limit by the client IP forwarded by a local proxy', async () => {
  await withRateLimits({ studyKeyLookupsPerHour: 1 }, async () => {
    const limitedApp = makeExpressApp(
      pool,
      initializeRepositories(pool, mockObservability),
      mockObservability,
    );

    // requests from supertest arrive from loopback, like from nginx
    const lookup = (clientIp: string) =>
      request(limitedApp).get('/v1/study/key').set('X-Forwarded-For', clientIp);

    expect((await lookup('203.0.113.1')).statusCode).toBe(404);
    expect((await lookup('203.0.113.1')).statusCode).toBe(429);
    expect((await lookup('203.0.113.2')).statusCode).toBe(404);
  });
});

// queued sensor reading ingestion

function makeAppWithQueue(add: (...args: unknown[]) => Promise<unknown>) {
  const queue = { add } as unknown as Queue<SensorReadingJobData>;
  return makeExpressApp(
    pool,
    initializeRepositories(pool, mockObservability),
    mockObservability,
    queue,
  );
}

test('should queue every batch under its own job id', async () => {
  const add = jest.fn(
    async (name: unknown, data: unknown, opts: { jobId: string }) => ({
      id: opts.jobId,
    }),
  );
  const queuedApp = makeAppWithQueue(add as never);
  await initializeBetweenGroupsStudy();
  const { token } = await enrolParticipant();

  const upload = () =>
    request(queuedApp)
      .post('/v1/reading/batch')
      .set({ Authorization: 'Bearer ' + token })
      .send([makeReading()]);
  // two batches arriving within the same millisecond
  const now = jest.spyOn(Date, 'now').mockReturnValue(1_700_000_000_000);
  const [first, second] = await Promise.all([upload(), upload()]);
  now.mockRestore();

  expect(first.statusCode).toBe(202);
  expect(second.statusCode).toBe(202);
  expect(first.body.jobId).not.toBe(second.body.jobId);
  expect(add).toHaveBeenCalledTimes(2);
});

test('should answer 503 when the queue rejects a batch', async () => {
  const queuedApp = makeAppWithQueue(async () => {
    throw new Error('OOM command not allowed when used memory > maxmemory');
  });
  await initializeBetweenGroupsStudy();
  const { token } = await enrolParticipant();

  const res = await request(queuedApp)
    .post('/v1/reading/batch')
    .set({ Authorization: 'Bearer ' + token })
    .send([makeReading()]);

  expect(res.statusCode).toBe(503);
});

test('should reject request bodies over the size limit with 413', async () => {
  await initializeBetweenGroupsStudy();
  const { token } = await enrolParticipant();

  const res = await request(app)
    .post('/v1/reading/batch')
    .set({ Authorization: 'Bearer ' + token })
    .send([makeReading({ data: 'x'.repeat(3 * 1024 * 1024) })]);

  expect(res.statusCode).toBe(413);
  expect(res.body).toHaveProperty('error');
});

test('should reject batches with more than 1000 readings', async () => {
  await initializeBetweenGroupsStudy();
  const { token } = await enrolParticipant();

  const res = await request(app)
    .post('/v1/reading/batch')
    .set({ Authorization: 'Bearer ' + token })
    .send(Array.from({ length: 1001 }, () => makeReading()));

  expect(res.statusCode).toBe(400);
});

// answers are bound to the participant's study

test("should reject answers to another study's questionnaire", async () => {
  await initializeBetweenGroupsStudy();
  const { token } = await enrolParticipant();
  const otherStudy = await createStudyWithGroup('other-key', null);
  const otherQuestionnaire = await createQuestionnaire(otherStudy.id);

  const res = await request(app)
    .post(
      `/v1/study/${otherStudy.id}/questionnaire/${otherQuestionnaire.id}/answer`,
    )
    .set({ Authorization: 'Bearer ' + token })
    .send(makeAnswer());

  expect(res.statusCode).toBe(403);
  const answers = await pool.query('SELECT id FROM esm_answers');
  expect(answers.rows).toHaveLength(0);
});

test('should reject answers from tokens without an enrolment', async () => {
  await initializeBetweenGroupsStudy();
  const { studyId } = await enrolParticipant();
  const questionnaire = await createQuestionnaire(studyId);

  const res = await request(app)
    .post(`/v1/study/${studyId}/questionnaire/${questionnaire.id}/answer`)
    .set({ Authorization: 'Bearer ' + generateAdminToken() })
    .send(makeAnswer());

  expect(res.statusCode).toBe(403);
});

// group allocation

test('should allocate groups by allocation order, not creation order', async () => {
  const token = generateAdminToken();
  const study = await request(app)
    .post('/v1/study')
    .set({ Authorization: 'Bearer ' + token })
    .send(dummyStudy);

  const createGroup = (allocationOrder: number, strategy: string) =>
    request(app)
      .post(`/v1/study/${study.body.id}/group`)
      .set({ Authorization: 'Bearer ' + token })
      .send({
        internalName: `group${allocationOrder}`,
        allocationOrder,
        phases: [
          {
            internalName: 'phase',
            fromDay: 0,
            durationDays: 7,
            interactionWidgetStrategy: strategy,
          },
        ],
      });

  // created in reverse allocation order
  await createGroup(1, 'Bucketed');
  await createGroup(0, 'Disabled');

  const first = await enrolParticipant();
  const second = await enrolParticipant();

  expect(first.phases[0].interactionWidgetStrategy).toBe('Disabled');
  expect(second.phases[0].interactionWidgetStrategy).toBe('Bucketed');
});
