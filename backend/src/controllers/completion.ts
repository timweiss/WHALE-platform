import { Enrolment, IEnrolmentRepository } from '../data/enrolmentRepository';
import {
  CompletionItem,
  CompletionItemType,
  EMAAnsweredCompletionItem,
  IStudyRepository,
  PassiveSensingParticipationDaysCompletionItem,
  Study,
} from '../data/studyRepository';
import { Express } from 'express';
import { authenticate, RequestUser } from '../middleware/authenticate';
import { ICompletionRepository } from '../data/completionRepository';

export function createCompletionController(
  completionRepository: ICompletionRepository,
  studyRepository: IStudyRepository,
  enrolmentRepository: IEnrolmentRepository,
  app: Express,
) {
  app.get('/v1/completion', authenticate, async (req, res) => {
    const enrolment = await enrolmentRepository.getEnrolmentById(
      (req.user as RequestUser).enrolmentId,
    );

    if (!enrolment) {
      return res.status(403).send({ error: 'Enrolment not found' });
    }

    const study = await studyRepository.getStudyById(enrolment.studyId);
    if (!study) {
      return res.status(404).send({ error: 'Study not found' });
    }

    if (!study.completionTracking) {
      return res
        .status(400)
        .send({ error: 'Completion tracking is not enabled for this study' });
    }

    try {
      const completion = await getCompletionLabels(study, enrolment);
      res.json(Object.fromEntries(completion));
    } catch (error) {
      return res.status(500).send({
        error: 'Error retrieving completion labels',
        details: (error as Error).message,
      });
    }
  });

  const getCompletionLabels = async (study: Study, enrolment: Enrolment) => {
    if (!study.completionTracking) {
      throw new Error('Completion tracking is not enabled for this study');
    }

    const labels: Map<string, boolean> = new Map();

    for (const label of Object.keys(study.completionTracking!)) {
      const completionItems = study.completionTracking[label];

      const conditionSatisfied = await meetsAllCompletionConditions(
        completionItems,
        enrolment,
      );

      labels.set(label, conditionSatisfied);
    }

    return labels;
  };

  const meetsAllCompletionConditions = async (
    completionItems: CompletionItem[],
    enrolment: Enrolment,
  ): Promise<boolean> => {
    const conditionSatisfied: Array<boolean> = [];

    for (const item of completionItems) {
      switch (item.type) {
        case CompletionItemType.EMAAnswered: {
          const emaItem = item as EMAAnsweredCompletionItem;

          const countEMAs = await completionRepository.getCountOfEMAsAnswered(
            enrolment.id,
          );
          conditionSatisfied.push(countEMAs >= emaItem.value);
          break;
        }
        case CompletionItemType.PassiveSensingParticipationDays: {
          const sensingItem =
            item as PassiveSensingParticipationDaysCompletionItem;
          const daysCompleted =
            await completionRepository.getCountOfDaysWithSensorData(
              enrolment.id,
            );
          conditionSatisfied.push(daysCompleted >= sensingItem.value);
          break;
        }
      }
    }

    return conditionSatisfied.every((v) => v);
  };
}
