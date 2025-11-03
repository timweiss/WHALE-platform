package de.mimuc.senseeverything.activity.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.mimuc.senseeverything.R
import de.mimuc.senseeverything.activity.SpacerLine
import de.mimuc.senseeverything.db.models.QuestionnaireInboxItem
import de.mimuc.senseeverything.db.models.validDistance

@Composable
fun QuestionnaireInbox(
    pendingQuestionnaires: List<QuestionnaireInboxItem>,
    openQuestionnaire: (QuestionnaireInboxItem) -> Unit
) {
    if (pendingQuestionnaires.isEmpty()) {
        return
    }

    SpacerLine(paddingValues = PaddingValues(vertical = 12.dp), width = 96.dp)

    Column {
        Row(horizontalArrangement = Arrangement.Center) {
            Image(
                painter = painterResource(id = R.drawable.baseline_inbox_24),
                contentDescription = "",
                modifier = Modifier
                    .size(28.dp)
                    .padding(end = 6.dp)
            )
            Text(
                stringResource(R.string.main_questionnaire_inbox),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp
            )
        }

        Text(stringResource(R.string.main_questionnaire_inbox_completion_hint))
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(vertical = 12.dp)
    ) {
        pendingQuestionnaires.forEach { pq ->
            Card(
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                    .fillMaxWidth(), onClick = {
                    openQuestionnaire(pq)
                }) {
                Column(modifier = Modifier.padding(6.dp)) {
                    Text(pq.title, fontWeight = FontWeight.SemiBold)
                    if (pq.validUntil != -1L) {
                        Text(
                            stringResource(
                                R.string.main_questionnaire_inbox_element_duration_validitiy,
                                pq.validDistance.inWholeMinutes
                            )
                        )
                    } else {
                        Text(stringResource(R.string.main_questionnaire_inbox_element_duration_indefinite))
                    }
                }
            }
        }
    }
}