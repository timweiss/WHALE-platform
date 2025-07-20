import request from 'supertest';
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
    .send({ enrolmentKey: 'key' });

  expect(res.statusCode).toBe(200);
  expect(res.body).toHaveProperty('participantId');
  expect(res.body).toHaveProperty('token');
  expect(res.body).toHaveProperty('phases');
});

test('should enrol in study with participant id', async () => {
  await initializeBetweenGroupsStudy();

  const enrol = await request(app)
    .post('/v1/enrolment')
    .send({ enrolmentKey: 'key' });

  const res = await request(app).post(
    `/v1/enrolment/${enrol.body.participantId}`,
  );

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
      .send({ enrolmentKey: 'key' });

    // should start with treatment phase
    const participant2 = await request(app)
      .post('/v1/enrolment')
      .send({ enrolmentKey: 'key' });

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
    .send({ enrolmentKey: 'key' });

  expect(res.statusCode).toBe(400);
  expect(res.body.code).toBe('invalid_study_configuration');
  expect(
    res.body.error.startsWith(
      'Invalid Study Configuration: Study has no experimental groups',
    ),
  ).toBe(true);
});

// sensor reading tests

function getTimestampInSeconds() {
  return Math.floor(Date.now() / 1000);
}

test('should create a sensor reading', async () => {
  await initializeBetweenGroupsStudy();

  const enrol = await request(app)
    .post('/v1/enrolment')
    .send({ enrolmentKey: 'key' });

  const res = await request(app)
    .post('/v1/reading')
    .set({ Authorization: 'Bearer ' + enrol.body.token })
    .send({
      sensorType: 'type',
      data: 'data',
      timestamp: getTimestampInSeconds().toString(),
    });

  expect(res.statusCode).toBe(200);
  expect(res.body).toMatchObject({ sensorType: 'type', data: 'data' });
});

test('should create a batch of sensor readings', async () => {
  await initializeBetweenGroupsStudy();

  const enrol = await request(app)
    .post('/v1/enrolment')
    .send({ enrolmentKey: 'key' });

  const res = await request(app)
    .post('/v1/reading/batch')
    .set({ Authorization: 'Bearer ' + enrol.body.token })
    .send([
      {
        sensorType: 'type',
        data: 'data',
        timestamp: getTimestampInSeconds().toString(),
      },
      {
        sensorType: 'type',
        data: 'data',
        timestamp: getTimestampInSeconds().toString(),
      },
    ]);

  expect(res.statusCode).toBe(200);
  expect(res.body).toBeInstanceOf(Array);
  expect(res.body).toHaveLength(2);
  expect(res.body[0]).toMatchObject({ sensorType: 'type', data: 'data' });
  expect(res.body[1]).toMatchObject({ sensorType: 'type', data: 'data' });
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

test('should create answers for questionnaire', async () => {
  const token = generateAdminToken();
  const study = await request(app)
    .post('/v1/study')
    .set({ Authorization: 'Bearer ' + token })
    .send(dummyStudy);

  const questionnaire = await request(app)
    .post(`/v1/study/${study.body.id}/questionnaire`)
    .set({ Authorization: 'Bearer ' + token })
    .send({ name: 'Questionnaire', enabled: true });

  const res = await request(app)
    .post(
      `/v1/study/${study.body.id}/questionnaire/${questionnaire.body.id}/answer`,
    )
    .set({ Authorization: 'Bearer ' + token })
    .send([
      {
        elementId: 1,
        elementName: 'answer1',
        value: 'answer1',
      },
      {
        elementId: 2,
        elementName: 'answer2',
        value: 'answer2',
      },
    ]);

  expect(res.statusCode).toBe(200);
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

test('should test completion endpoint with sensor data tracking', async () => {
  const token = generateAdminToken();

  // Create a study with completion tracking enabled
  const studyWithCompletion = {
    ...dummyStudy,
    enrolmentKey: 'completion-test-key',
    name: 'Completion Test Study',
    completionTracking: {
      oneDayOfSensorData: [
        {
          type: 'PassiveSensingParticipationDays',
          value: 1,
        },
      ],
    },
  };

  const study = await request(app)
    .post('/v1/study')
    .set({ Authorization: 'Bearer ' + token })
    .send(studyWithCompletion);

  expect(study.statusCode).toBe(200);

  // Create experimental groups for the study
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

  // Update the study to include completion tracking
  const updatedStudy = await request(app)
    .put(`/v1/study/${study.body.id}`)
    .set({ Authorization: 'Bearer ' + token })
    .send({
      ...study.body,
      completionTracking: {
        oneDayOfSensorData: [
          {
            type: 'PassiveSensingParticipationDays',
            value: 1,
          },
        ],
      },
    });

  expect(updatedStudy.statusCode).toBe(200);

  // Enroll a participant in the study
  const enrollment = await request(app)
    .post('/v1/enrolment')
    .send({ enrolmentKey: 'completion-test-key' });

  expect(enrollment.statusCode).toBe(200);
  expect(enrollment.body).toHaveProperty('participantId');
  expect(enrollment.body).toHaveProperty('token');

  const participantToken = enrollment.body.token;

  // Test completion endpoint before adding sensor data (should show incomplete)
  const completionBefore = await request(app)
    .get('/v1/completion')
    .set({ Authorization: 'Bearer ' + participantToken });

  expect(completionBefore.statusCode).toBe(200);
  expect(completionBefore.body.oneDayOfSensorData).toBe(false);

  // Add sensor data for one day
  const today = new Date();
  const sensorData = {
    sensorType: 'accelerometer',
    data: JSON.stringify({ x: 1.0, y: 2.0, z: 3.0 }),
    timestamp: today.toISOString(),
  };

  const sensorReading = await request(app)
    .post('/v1/reading')
    .set({ Authorization: 'Bearer ' + participantToken })
    .send(sensorData);

  expect(sensorReading.statusCode).toBe(200);

  // Test completion endpoint after adding sensor data (should show complete)
  const completionAfter = await request(app)
    .get('/v1/completion')
    .set({ Authorization: 'Bearer ' + participantToken });

  expect(completionAfter.statusCode).toBe(200);
  expect(completionAfter.body.oneDayOfSensorData).toBe(true);
});

test('should test completion endpoint with multiple sensor readings on same day', async () => {
  const token = generateAdminToken();

  // Create a study with completion tracking for 2 days
  const studyWithCompletion = {
    ...dummyStudy,
    enrolmentKey: 'multi-day-test-key',
    name: 'Multi Day Test Study',
  };

  const study = await request(app)
    .post('/v1/study')
    .set({ Authorization: 'Bearer ' + token })
    .send(studyWithCompletion);

  // Create experimental groups
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

  // Update study with completion tracking for 2 days
  const updatedStudy = await request(app)
    .put(`/v1/study/${study.body.id}`)
    .set({ Authorization: 'Bearer ' + token })
    .send({
      ...study.body,
      completionTracking: {
        twoDaysOfSensorData: [
          {
            type: 'PassiveSensingParticipationDays',
            value: 2,
          },
        ],
      },
    });

  // Enroll participant
  const enrollment = await request(app)
    .post('/v1/enrolment')
    .send({ enrolmentKey: 'multi-day-test-key' });

  const participantToken = enrollment.body.token;

  // Add multiple sensor readings on the same day
  const today = new Date();
  const yesterday = new Date(today);
  yesterday.setDate(yesterday.getDate() - 1);

  // Add readings for today
  await request(app)
    .post('/v1/reading')
    .set({ Authorization: 'Bearer ' + participantToken })
    .send({
      sensorType: 'accelerometer',
      data: JSON.stringify({ x: 1.0, y: 2.0, z: 3.0 }),
      timestamp: today.toISOString(),
    });

  await request(app)
    .post('/v1/reading')
    .set({ Authorization: 'Bearer ' + participantToken })
    .send({
      sensorType: 'gyroscope',
      data: JSON.stringify({ x: 0.5, y: 1.5, z: 2.5 }),
      timestamp: today.toISOString(),
    });

  // Should only count as 1 day so far
  const completionAfterOneDay = await request(app)
    .get('/v1/completion')
    .set({ Authorization: 'Bearer ' + participantToken });

  expect(completionAfterOneDay.statusCode).toBe(200);
  expect(completionAfterOneDay.body.twoDaysOfSensorData).toBe(false);

  // Add readings for yesterday
  await request(app)
    .post('/v1/reading')
    .set({ Authorization: 'Bearer ' + participantToken })
    .send({
      sensorType: 'accelerometer',
      data: JSON.stringify({ x: 2.0, y: 3.0, z: 4.0 }),
      timestamp: yesterday.toISOString(),
    });

  // Should now count as 2 days and be complete
  const completionAfterTwoDays = await request(app)
    .get('/v1/completion')
    .set({ Authorization: 'Bearer ' + participantToken });

  expect(completionAfterTwoDays.statusCode).toBe(200);
  expect(completionAfterTwoDays.body.twoDaysOfSensorData).toBe(true);
});

test('should fail completion endpoint when completion tracking is not enabled', async () => {
  const token = generateAdminToken();

  // Create a study without completion tracking
  const studyWithoutCompletion = {
    ...dummyStudy,
    enrolmentKey: 'no-completion-key',
    name: 'No Completion Study',
  };

  const study = await request(app)
    .post('/v1/study')
    .set({ Authorization: 'Bearer ' + token })
    .send(studyWithoutCompletion);

  // Create experimental groups
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

  // Enroll participant
  const enrollment = await request(app)
    .post('/v1/enrolment')
    .send({ enrolmentKey: 'no-completion-key' });

  const participantToken = enrollment.body.token;

  // Try to access completion endpoint - should fail
  const completionResponse = await request(app)
    .get('/v1/completion')
    .set({ Authorization: 'Bearer ' + participantToken });

  expect(completionResponse.statusCode).toBe(400);
  expect(completionResponse.body.error).toBe(
    'Completion tracking is not enabled for this study',
  );
});
