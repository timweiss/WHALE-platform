package de.mimuc.senseeverything.api.model.ema

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

@Serializable
enum class QuestionnaireElementType(val apiName: String) {
    @SerialName("malformed") MALFORMED("malformed"),
    @SerialName("text_view") TEXT_VIEW("text_view"),
    @SerialName("radio_group") RADIO_GROUP("radio_group"),
    @SerialName("checkbox_group") CHECKBOX_GROUP("checkbox_group"),
    @SerialName("slider") SLIDER("slider"),
    @SerialName("text_entry") TEXT_ENTRY("text_entry"),
    @SerialName("quantity_entry") QUANTITY_ENTRY("quantity_entry"),
    @SerialName("external_questionnaire_link") EXTERNAL_QUESTIONNAIRE_LINK("external_questionnaire_link"),
    @SerialName("social_network_entry") SOCIAL_NETWORK_ENTRY("social_network_entry"),
    @SerialName("social_network_rating") SOCIAL_NETWORK_RATING("social_network_rating"),
    @SerialName("circumplex") CIRCUMPLEX("circumplex"),
    @SerialName("likert_scale_label") LIKERT_SCALE_LABEL("likert_scale_label"),
    @SerialName("button_group") BUTTON_GROUP("button_group"),
    @SerialName("time_input") TIME_INPUT("time_input");

    companion object {
        fun fromApiName(apiName: String): QuestionnaireElementType? {
            return QuestionnaireElementType.entries.find { it.apiName == apiName }
                ?: throw IllegalArgumentException("Unknown QuestionnaireElementType: $apiName")
        }
    }
}

@Serializable
enum class GroupAlignment {
    @SerialName("horizontal") Horizontal,
    @SerialName("vertical") Vertical
}

@Serializable
sealed class QuestionnaireElement {
    open val id: Int = 0
    open val questionnaireId: Int = 0
    open val name: String = ""
    open val step: Int = 0
    open val position: Int = 0
    open val displayGroup: Int = 0

    // Computed property that derives type from the actual class
    val type: QuestionnaireElementType
        get() = when (this) {
            is TextViewElement -> QuestionnaireElementType.TEXT_VIEW
            is RadioGroupElement -> QuestionnaireElementType.RADIO_GROUP
            is CheckboxGroupElement -> QuestionnaireElementType.CHECKBOX_GROUP
            is SliderElement -> QuestionnaireElementType.SLIDER
            is TextEntryElement -> QuestionnaireElementType.TEXT_ENTRY
            is QuantityEntryElement -> QuestionnaireElementType.QUANTITY_ENTRY
            is ExternalQuestionnaireLinkElement -> QuestionnaireElementType.EXTERNAL_QUESTIONNAIRE_LINK
            is SocialNetworkEntryElement -> QuestionnaireElementType.SOCIAL_NETWORK_ENTRY
            is SocialNetworkRatingElement -> QuestionnaireElementType.SOCIAL_NETWORK_RATING
            is CircumplexElement -> QuestionnaireElementType.CIRCUMPLEX
            is LikertScaleLabelElement -> QuestionnaireElementType.LIKERT_SCALE_LABEL
            is ButtonGroupElement -> QuestionnaireElementType.BUTTON_GROUP
            is TimeInputElement -> QuestionnaireElementType.TIME_INPUT
            is MalformedElement -> QuestionnaireElementType.MALFORMED
        }
}

@Serializable
@SerialName("text_view")
data class TextViewElement(
    val configuration: TextViewConfiguration
) : QuestionnaireElement()

@Serializable
data class TextViewConfiguration(
    val text: String
)

@Serializable
@SerialName("radio_group")
data class RadioGroupElement(
    val configuration: RadioGroupConfiguration
) : QuestionnaireElement()

@Serializable
data class RadioGroupConfiguration(
    val options: List<String>,
    val alignment: GroupAlignment = GroupAlignment.Vertical
)

@Serializable
@SerialName("checkbox_group")
data class CheckboxGroupElement(
    val configuration: CheckboxGroupConfiguration
) : QuestionnaireElement()

@Serializable
data class CheckboxGroupConfiguration(
    val options: List<String>,
    val alignment: GroupAlignment = GroupAlignment.Vertical
)

@Serializable
@SerialName("slider")
data class SliderElement(
    val configuration: SliderConfiguration
) : QuestionnaireElement()

@Serializable
data class SliderConfiguration(
    val min: Int,
    val max: Int,
    val stepSize: Double
)

@Serializable
@SerialName("text_entry")
data class TextEntryElement(
    val configuration: TextEntryConfiguration
) : QuestionnaireElement()

@Serializable
data class TextEntryConfiguration(
    val hint: String
)

@Serializable
@SerialName("quantity_entry")
data class QuantityEntryElement(
    val configuration: QuantityEntryConfiguration
) : QuestionnaireElement()

@Serializable
data class QuantityEntryConfiguration(
    val hint: String,
    val unit: String,
    val rangeMin: Int,
    val rangeMax: Int
)

@Serializable
@SerialName("external_questionnaire_link")
data class ExternalQuestionnaireLinkElement(
    val configuration: ExternalQuestionnaireLinkConfiguration
) : QuestionnaireElement()

@Serializable
data class ExternalQuestionnaireLinkConfiguration(
    val externalUrl: String,
    val actionText: String,
    val urlParams: List<UrlParameter>
)

@Serializable
data class UrlParameter(
    val key: String,
    val value: String
)

@Serializable
@SerialName("social_network_entry")
data class SocialNetworkEntryElement(
    val configuration: SocialNetworkEntryConfiguration = SocialNetworkEntryConfiguration()
) : QuestionnaireElement()

@Serializable
data class SocialNetworkEntryConfiguration(
    val placeholder: String = ""
)

@Serializable
@SerialName("social_network_rating")
data class SocialNetworkRatingElement(
    val configuration: SocialNetworkRatingConfiguration
) : QuestionnaireElement()

@Serializable
data class SocialNetworkRatingConfiguration(
    val ratingQuestionnaireId: Int
)

@Serializable
@SerialName("circumplex")
data class CircumplexElement(
    val configuration: CircumplexConfiguration
) : QuestionnaireElement()

@Serializable
data class CircumplexConfiguration(
    val imageUrl: String,
    val clip: CircumplexClip = CircumplexClip()
)

@Serializable
data class CircumplexClip(
    val top: Int = 0,
    val bottom: Int = 0,
    val left: Int = 0,
    val right: Int = 0
)

@Serializable
@SerialName("likert_scale_label")
data class LikertScaleLabelElement(
    val configuration: LikertScaleLabelConfiguration
) : QuestionnaireElement()

@Serializable
data class LikertScaleLabelConfiguration(
    val options: List<String>
)

@Serializable
@SerialName("button_group")
data class ButtonGroupElement(
    val configuration: ButtonGroupConfiguration
) : QuestionnaireElement()

@Serializable
data class ButtonGroupConfiguration(
    val options: List<ButtonOption>,
    val alignment: GroupAlignment = GroupAlignment.Horizontal
)

@Serializable
data class ButtonOption(
    val label: String,
    val nextStep: Int? = null
)

@Serializable
@SerialName("time_input")
data class TimeInputElement(
    val configuration: TimeInputConfiguration
) : QuestionnaireElement()

@Serializable
data class TimeInputConfiguration(
    val label: String = "Select time",
    val filledText: String = "Change"
)

@Serializable
@SerialName("malformed")
data class MalformedElement(
    val configuration: MalformedConfiguration = MalformedConfiguration()
) : QuestionnaireElement()

@Serializable
data class MalformedConfiguration(
    val error: String = "Malformed element"
)

// Serializers module for polymorphic serialization
val questionnaireElementModule = SerializersModule {
    polymorphic(QuestionnaireElement::class) {
        subclass(TextViewElement::class)
        subclass(RadioGroupElement::class)
        subclass(CheckboxGroupElement::class)
        subclass(SliderElement::class)
        subclass(TextEntryElement::class)
        subclass(ExternalQuestionnaireLinkElement::class)
        subclass(SocialNetworkEntryElement::class)
        subclass(SocialNetworkRatingElement::class)
        subclass(CircumplexElement::class)
        subclass(LikertScaleLabelElement::class)
        subclass(ButtonGroupElement::class)
        subclass(TimeInputElement::class)
        subclass(MalformedElement::class)
    }
}

val questionnaireJson = Json {
    serializersModule = questionnaireElementModule
    ignoreUnknownKeys = true
    isLenient = true
    classDiscriminator = "type"
}

// Combined serializers module for both QuestionnaireElement and QuestionnaireTrigger
val fullQuestionnaireModule = SerializersModule {
    include(questionnaireElementModule)
    include(questionnaireTriggerModule)
}

val fullQuestionnaireJson = Json {
    serializersModule = fullQuestionnaireModule
    ignoreUnknownKeys = true
    explicitNulls = false
    isLenient = true
    classDiscriminator = "type"
}