export class NotImplementedError extends Error {
  constructor() {
    super('Not Implemented');
  }
}

export class DatabaseError extends Error {
  constructor(error: string) {
    super('Database error: ' + error);
  }
}
