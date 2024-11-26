import request from 'supertest';
import { usePool } from '../src/config/database';
import { makeExpressApp } from '../src';
import { Config } from '../src/config';
import jwt from 'jsonwebtoken';
import { initializeRepositories } from '../src/data/repositoryHelper';

const pool = usePool();
const app = makeExpressApp(pool, initializeRepositories(pool));

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
};

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

test('should enrol in study', async () => {
  const token = generateAdminToken();
  const study = await request(app)
    .post('/v1/study')
    .set({ Authorization: 'Bearer ' + token })
    .send(dummyStudy);

  const res = await request(app)
    .post('/v1/enrolment')
    .send({ enrolmentKey: 'key' });

  expect(res.statusCode).toBe(200);
  expect(res.body).toHaveProperty('participantId');
  expect(res.body).toHaveProperty('token');
});

test('should enrol in study with participant id', async () => {
  const token = generateAdminToken();
  const study = await request(app)
    .post('/v1/study')
    .set({ Authorization: 'Bearer ' + token })
    .send(dummyStudy);

  const enrol = await request(app)
    .post('/v1/enrolment')
    .send({ enrolmentKey: 'key' });

  const res = await request(app).post(
    `/v1/enrolment/${enrol.body.participantId}`,
  );

  expect(res.statusCode).toBe(200);
  expect(res.body).toHaveProperty('participantId');
  expect(res.body).toHaveProperty('token');
});

// sensor reading tests

function getTimestampInSeconds() {
  return Math.floor(Date.now() / 1000);
}

test('should create a sensor reading', async () => {
  const token = generateAdminToken();
  const study = await request(app)
    .post('/v1/study')
    .set({ Authorization: 'Bearer ' + token })
    .send(dummyStudy);

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
  const token = generateAdminToken();
  const study = await request(app)
    .post('/v1/study')
    .set({ Authorization: 'Bearer ' + token })
    .send(dummyStudy);

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
