package de.mimuc.senseeverything.service.accessibility

import android.content.Context
import de.mimuc.senseeverything.api.model.ElementValue
import de.mimuc.senseeverything.api.model.ScreenshotRatingValue
import de.mimuc.senseeverything.api.model.ema.ApproveScreenshotUpload
import de.mimuc.senseeverything.api.model.ema.QuestionnaireRule
import de.mimuc.senseeverything.api.model.ema.ScreenshotRatingElement
import de.mimuc.senseeverything.data.DataStoreManager
import de.mimuc.senseeverything.data.getQuestionnaireById
import de.mimuc.senseeverything.db.AppDatabase
import de.mimuc.senseeverything.helpers.QuestionnaireRuleEvaluator
import de.mimuc.senseeverything.logging.WHALELog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "ScreenshotApproval"

/**
 * The snapshotIds of the screenshots the participant approved for upload.
 * Any other actions are not handled here.
 *
 * @param ratings the answers to each screenshot's embedded questionnaire, by snapshotId and element id
 * @param packages the package of each stored screenshot, by snapshotId
 * @param rules the rules of each package's embedded questionnaire, by package
 */
fun approvedScreenshots(
    ratings: Map<String, Map<Int, ElementValue>>,
    packages: Map<String, String>,
    rules: Map<String, List<QuestionnaireRule>>
): Set<String> {
    val approvals = mutableSetOf<String>()

    for ((snapshotId, answers) in ratings) {
        val packageName = packages[snapshotId] ?: continue
        val packageRules = rules[packageName] ?: continue

        // consent checking: package needs rules, and rules that approve a screenshot upload need at least one condition
        val approvalRules = packageRules.filter {
            it.actions.filterIsInstance<ApproveScreenshotUpload>().isNotEmpty()
        }
            .filter { it.conditions.conditions.isNotEmpty() }

        if (approvalRules.isEmpty()) continue

        // evaluate to find if ApproveScreenshotUpload matches; nothing else is handled here
        val evaluation = QuestionnaireRuleEvaluator(approvalRules).evaluate(answers)
            .flatMap { it.value }

        val approval = evaluation.filterIsInstance<ApproveScreenshotUpload>().any()
        if (approval) approvals.add(snapshotId)
    }
    return approvals
}

/**
 * Keeps the screenshots approved in a completed rating questionnaire for upload. Has to run before the
 * questionnaire is marked as completed: from then on, its period may be deleted.
 *
 * Never fails the questionnaire: a screenshot that can't be kept is not uploaded.
 */
suspend fun keepApprovedScreenshots(
    context: Context,
    database: AppDatabase,
    dataStoreManager: DataStoreManager,
    contextJson: String?,
    ratingElements: List<ScreenshotRatingElement>,
    values: Map<Int, ElementValue>
) {
    if (ratingElements.isEmpty()) return
    val periodEnd = ScreenshotRatingContext.fromJson(contextJson)?.screenshotPeriodEnd ?: return

    try {
        withContext(Dispatchers.IO) {
            val samples = ScreenshotStore.readPeriod(ScreenshotStore.root(context), periodEnd)
                .associateBy { it.sample.snapshotId }
            for (element in ratingElements) {
                val ratings = (values[element.id] as? ScreenshotRatingValue)?.values ?: continue
                val rules =
                    element.configuration.packageRatingQuestionnaires.mapValues { (_, questionnaireId) ->
                        dataStoreManager.getQuestionnaireById(questionnaireId)?.questionnaire?.rules.orEmpty()
                    }
                val approved =
                    approvedScreenshots(ratings, samples.mapValues { it.value.packageName }, rules)
                WHALELog.i(
                    TAG,
                    "${approved.size} of ${ratings.size} screenshots of period $periodEnd approved for upload"
                )

                for (sample in approved.mapNotNull { samples[it] }) {
                    try {
                        ApprovedScreenshots.keep(context, database, sample)
                    } catch (e: Exception) {
                        WHALELog.e(
                            TAG,
                            "Could not keep approved screenshot ${sample.sample.snapshotId}: ${e.message}",
                            e
                        )
                    }
                }
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        WHALELog.e(TAG, "Could not evaluate the approvals of period $periodEnd: ${e.message}", e)
    }
}
