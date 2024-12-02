import { InvalidStudyConfigurationError } from '../config/errors';
import { StudyExperimentalGroup } from '../data/studyRepository';

/**
 * Rolls a dice and returns the index of the probability group.
 * fixme: this does not guarantee an equal distribution, so another algorithm should be consulted
 * @param probabilities index of the probability group.
 */
export async function rollDiceOverProbabilities(
  probabilities: number[],
): Promise<number> {
  const total = probabilities.reduce((a, b) => a + b, 0);
  const random = Math.random() * total;
  let sum = 0;
  for (let i = 0; i < probabilities.length; i++) {
    sum += probabilities[i];
    if (random < sum) {
      return i;
    }
  }

  return -1;
}

/**
 * Validates that the probabilities sum to 1.
 * @param probabilities The probabilities to validate.
 */
export function validateProbabilities(probabilities: number[]) {
  const total = probabilities.reduce((a, b) => a + b, 0);
  if (total > 1) {
    throw new InvalidStudyConfigurationError(
      `Experimental groups have total probability greater than 1. (actual: ${total})`,
    );
  }
  if (total < 1) {
    throw new InvalidStudyConfigurationError(
      `Experimental groups have total probability less than 1. (actual: ${total})`,
    );
  }
}

export function listGroupNames(groups: StudyExperimentalGroup[]): string {
  return groups
    .map((g) => `${g.allocation.type}: ${g.internalName}`)
    .join(`, `);
}
