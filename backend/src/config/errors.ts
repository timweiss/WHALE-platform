export class NotImplementedError extends Error {
  constructor() {
    super('Not Implemented');
  }
}

export class DatabaseError extends Error {
  constructor(error: string) {
    super(`Database error: ${error}`);
  }
}

export class NotFoundError extends Error {
  constructor(entityName: string) {
    super(`Could not find ${entityName}`);
  }
}

export class InvalidStudyConfigurationError extends Error {
  constructor(error: string) {
    super(`Invalid Study Configuration: ${error}`);
  }
}
